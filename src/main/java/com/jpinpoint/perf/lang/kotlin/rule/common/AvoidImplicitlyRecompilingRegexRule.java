package com.jpinpoint.perf.lang.kotlin.rule.common;

import com.jpinpoint.perf.lang.kotlin.util.KotlinAstUtil;
import net.sourceforge.pmd.lang.kotlin.AbstractKotlinRule;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitor;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitorBase;
import net.sourceforge.pmd.lang.rule.RuleTargetSelector;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.*;

/**
 * Detects implicit regex compilation inside Kotlin function bodies:
 * <ul>
 *   <li>{@code "pattern".toRegex()} / {@code CONST.toRegex()} / {@code field.toRegex()}</li>
 *   <li>{@code Regex("pattern")} / {@code Regex(CONST)} / {@code Regex(field)}</li>
 *   <li>{@code Pattern.matches("pattern", ...)} (requires {@code import java.util.regex.Pattern})</li>
 *   <li>{@code FileSystems.getDefault().getPathMatcher("pattern")}
 *       (requires {@code import java.nio.file.FileSystems} or {@code java.nio.file.Path})</li>
 * </ul>
 *
 * <p>Regex that are dynamic (derived from function parameters, local variables, or method-call
 * results) are not flagged, as they cannot be pre-compiled.</p>
 */
public class AvoidImplicitlyRecompilingRegexRule extends AbstractKotlinRule {

    @Override
    protected RuleTargetSelector buildTargetSelector() {
        return RuleTargetSelector.forTypes(KotlinParser.KtFunctionDeclaration.class);
    }

    @Override
    public KotlinVisitor<RuleContext, ?> buildVisitor() {
        return new Visitor();
    }

    private static final class Visitor extends KotlinVisitorBase<RuleContext, Void> {

        @Override
        public Void visitFunctionDeclaration(KotlinParser.KtFunctionDeclaration node, RuleContext ctx) {
            Set<String> paramNames = KotlinAstUtil.collectAllParamNames(node);

            KotlinParser.KtFunctionBody body = node.functionBody();
            Set<String> localVarNames = KotlinAstUtil.collectLocalVarNames(body);

            Set<String> classVarFields = KotlinAstUtil.collectClassVarFieldNames(node);

            boolean hasPatternImport =
                    KotlinAstUtil.hasImport(node, "java.util.regex.Pattern");
            boolean hasFileImport =
                    KotlinAstUtil.hasImport(node, "java.nio.file.FileSystems")
                    || KotlinAstUtil.hasImport(node, "java.nio.file.Path");

            // --- .toRegex() calls ---
            for (KotlinParser.KtNavigationSuffix navSuffix :
                    node.descendants(KotlinParser.KtNavigationSuffix.class)
                        .filter(ns -> KotlinAstUtil.isDirectDescendantOfFunctionDeclaration(ns, node))
                        .toList()) {
                if (!KotlinAstUtil.isToRegexNavSuffix(navSuffix)) continue;
                if (isToRegexDynamic(navSuffix, paramNames, localVarNames)) continue;
                ctx.addViolation(navSuffix);
            }

            // --- Regex(...) constructor calls ---
            for (KotlinParser.KtPostfixUnaryExpression pue :
                    node.descendants(KotlinParser.KtPostfixUnaryExpression.class)
                        .filter(p -> KotlinAstUtil.isDirectDescendantOfFunctionDeclaration(p, node))
                        .toList()) {
                if (!KotlinAstUtil.isRegexConstructorCall(pue)) continue;
                KotlinParser.KtCallSuffix callSuffix = KotlinAstUtil.getFirstCallSuffix(pue);
                if (callSuffix == null) continue;
                if (isRegexArgDynamic(pue, callSuffix, paramNames, localVarNames, classVarFields)) continue;
                // Report on the SimpleIdentifier "Regex" inside the PrimaryExpression
                KotlinParser.KtSimpleIdentifier si = pue.primaryExpression().simpleIdentifier();
                if (si != null) ctx.addViolation(si);
            }

            // --- Pattern.matches(...) calls ---
            if (hasPatternImport) {
                for (KotlinParser.KtPostfixUnaryExpression pue :
                        node.descendants(KotlinParser.KtPostfixUnaryExpression.class)
                            .filter(p -> KotlinAstUtil.isDirectDescendantOfFunctionDeclaration(p, node))
                            .toList()) {
                    if (!isPatternMatchesCall(pue)) continue;
                    if (isPatternMatchesArgDynamic(pue, paramNames)) continue;
                    KotlinParser.KtSimpleIdentifier si = pue.primaryExpression().simpleIdentifier();
                    if (si != null) ctx.addViolation(si);
                }
            }

            // --- FileSystems.getPathMatcher(...) calls ---
            if (hasFileImport) {
                Set<String> fileSystemsVarNames = KotlinAstUtil.collectFileSystemsVarNames(node);
                for (KotlinParser.KtNavigationSuffix navSuffix :
                        node.descendants(KotlinParser.KtNavigationSuffix.class)
                            .filter(ns -> KotlinAstUtil.isDirectDescendantOfFunctionDeclaration(ns, node))
                            .toList()) {
                    if (!KotlinAstUtil.isGetPathMatcherNavSuffix(navSuffix)) continue;
                    if (!KotlinAstUtil.isOnFileSystemsReceiver(navSuffix, fileSystemsVarNames)) continue;
                    if (isGetPathMatcherArgDynamic(navSuffix, paramNames)) continue;
                    ctx.addViolation(navSuffix);
                }
            }

            return visitChildren(node, ctx);
        }

        /**
         * Returns true if the .toRegex() call should be treated as dynamic (no violation):
         * <ul>
         *   <li>Condition 2: first PostfixUnarySuffix of the receiver PostfixUnaryExpression
         *       is a CallSuffix (meaning the receiver is the result of a method call)</li>
         *   <li>Condition 1: the PrimaryExpression's identifier matches a function parameter</li>
         *   <li>Condition 3: the PrimaryExpression's identifier is a local variable</li>
         * </ul>
         */
        private static boolean isToRegexDynamic(KotlinParser.KtNavigationSuffix navSuffix,
                                                 Set<String> paramNames,
                                                 Set<String> localVarNames) {
            // Navigate: NavigationSuffix -> PostfixUnarySuffix -> PostfixUnaryExpression
            KotlinParser.KtPostfixUnarySuffix suffix =
                    navSuffix.ancestors(KotlinParser.KtPostfixUnarySuffix.class).first();
            if (suffix == null) return true;
            KotlinParser.KtPostfixUnaryExpression pue =
                    suffix.ancestors(KotlinParser.KtPostfixUnaryExpression.class).first();
            if (pue == null) return true;

            // Condition 2: first PostfixUnarySuffix is a CallSuffix -> receiver is method result
            KotlinParser.KtPostfixUnarySuffix firstSuffix = pue.postfixUnarySuffix(0);
            if (firstSuffix != null && firstSuffix.callSuffix() != null) return true;

            // Get receiver identifier from PrimaryExpression
            String receiverName = KotlinAstUtil.getPrimaryExpressionSimpleIdentifierText(
                    pue.primaryExpression());

            // Condition 1: receiver is a function parameter
            if (receiverName != null && paramNames.contains(receiverName)) return true;

            // Condition 3: receiver is a local variable
            if (receiverName != null && localVarNames.contains(receiverName)) return true;

            return false;
        }

        /**
         * Returns true if the Regex(...) argument is dynamic and should not be flagged:
         * <ul>
         *   <li>Condition 1: arg contains a PrimaryExpression with a function parameter name</li>
         *   <li>Condition 2: arg contains a nested CallSuffix (method call)</li>
         *   <li>Condition 3: nearest ancestor CallSuffix contains a local-variable reference</li>
         *   <li>Condition 4: arg contains a string-template reference to a param (e.g. {@code "$param"})</li>
         *   <li>Condition 5: arg contains a string-template reference to a class var field</li>
         *   <li>Condition 6: arg contains string concatenation (+) with a class var field</li>
         * </ul>
         */
        private static boolean isRegexArgDynamic(KotlinParser.KtPostfixUnaryExpression pue,
                                                  KotlinParser.KtCallSuffix callSuffix,
                                                  Set<String> paramNames,
                                                  Set<String> localVarNames,
                                                  Set<String> classVarFields) {
            // Condition 1: any PrimaryExpression in the CallSuffix contains a param identifier
            if (callSuffix.descendants(KotlinParser.KtPrimaryExpression.class)
                    .any(pe -> {
                        String name = KotlinAstUtil.getPrimaryExpressionSimpleIdentifierText(pe);
                        return paramNames.contains(name) || classVarFields.contains(name);
                    })) {
                return true;
            }

            // Condition 2: any nested CallSuffix (method call) inside the argument
            if (callSuffix.descendants(KotlinParser.KtCallSuffix.class).nonEmpty()) {
                return true;
            }

            // Condition 3: nearest ancestor CallSuffix of this Regex PUE has a local-var reference
            KotlinParser.KtCallSuffix ancestorCallSuffix =
                    pue.ancestors(KotlinParser.KtCallSuffix.class).first();
            if (ancestorCallSuffix != null) {
                if (ancestorCallSuffix.descendants(KotlinParser.KtPostfixUnaryExpression.class)
                        .any(inner -> {
                            String text = KotlinAstUtil.getPrimaryExpressionSimpleIdentifierText(
                                    inner.primaryExpression());
                            return text != null && localVarNames.contains(text);
                        })) {
                    return true;
                }
            }

            // Condition 4: string template in the arg contains a reference to a function param
            if (KotlinAstUtil.descendantHasStringTemplateRefFromSet(callSuffix, paramNames)) {
                return true;
            }

            // Condition 5: string template in the arg contains a reference to a class var field
            if (KotlinAstUtil.descendantHasStringTemplateRefFromSet(callSuffix, classVarFields)) {
                return true;
            }

            // Condition 6: additive expression in the arg contains a class var field identifier
            //   (only when the arg also contains a string literal, matching XPath [//LineStringLiteral])
            if (callSuffix.descendants(KotlinParser.KtStringLiteral.class).nonEmpty()) {
                return callSuffix.descendants(KotlinParser.KtAdditiveExpression.class)
                        .any(ae -> ae.descendants(KotlinParser.KtMultiplicativeExpression.class)
                                .any(me -> me.descendants(KotlinParser.KtSimpleIdentifier.class)
                                        .any(si -> classVarFields.contains(
                                                KotlinAstUtil.getIdentifierText(si)))));
            }

            return false;
        }

        /**
         * Returns true if the PostfixUnaryExpression is a {@code Pattern.matches(...)} call:
         * primary expression is "Pattern" and there is a NavigationSuffix with "matches".
         */
        private static boolean isPatternMatchesCall(KotlinParser.KtPostfixUnaryExpression pue) {
            KotlinParser.KtPrimaryExpression pe = pue.primaryExpression();
            if (pe == null) return false;
            if (!"Pattern".equals(KotlinAstUtil.getIdentifierText(pe.simpleIdentifier()))) return false;
            // Check that some PostfixUnarySuffix has NavigationSuffix with text "matches"
            return pue.postfixUnarySuffix().stream()
                    .anyMatch(sus -> {
                        KotlinParser.KtNavigationSuffix ns = sus.navigationSuffix();
                        return ns != null && "matches".equals(
                                KotlinAstUtil.getIdentifierText(ns.simpleIdentifier()));
                    });
        }

        /**
         * Returns true if the first argument to {@code Pattern.matches(...)} is a function param.
         */
        private static boolean isPatternMatchesArgDynamic(KotlinParser.KtPostfixUnaryExpression pue,
                                                           Set<String> paramNames) {
            // Find the CallSuffix that follows "matches" NavigationSuffix
            List<KotlinParser.KtPostfixUnarySuffix> suffixes = pue.postfixUnarySuffix();
            for (int i = 0; i < suffixes.size(); i++) {
                KotlinParser.KtNavigationSuffix ns = suffixes.get(i).navigationSuffix();
                if (ns != null && "matches".equals(KotlinAstUtil.getIdentifierText(ns.simpleIdentifier()))) {
                    // The CallSuffix with the arguments should follow
                    if (i + 1 < suffixes.size()) {
                        KotlinParser.KtCallSuffix callSuffix = suffixes.get(i + 1).callSuffix();
                        if (callSuffix != null) {
                            return KotlinAstUtil.firstArgIsParam(callSuffix, paramNames);
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Returns true if the getPathMatcher argument is dynamic (function param or string template
         * containing a function param).
         */
        private static boolean isGetPathMatcherArgDynamic(KotlinParser.KtNavigationSuffix navSuffix,
                                                           Set<String> paramNames) {
            // Find the CallSuffix that immediately follows this NavigationSuffix's PostfixUnarySuffix
            KotlinParser.KtPostfixUnarySuffix suffix =
                    navSuffix.ancestors(KotlinParser.KtPostfixUnarySuffix.class).first();
            if (suffix == null) return false;
            KotlinParser.KtPostfixUnaryExpression pue =
                    suffix.ancestors(KotlinParser.KtPostfixUnaryExpression.class).first();
            if (pue == null) return false;

            List<KotlinParser.KtPostfixUnarySuffix> suffixes = pue.postfixUnarySuffix();
            for (int i = 0; i < suffixes.size(); i++) {
                if (suffixes.get(i) == suffix) {
                    // The next PostfixUnarySuffix should be the CallSuffix
                    if (i + 1 < suffixes.size()) {
                        KotlinParser.KtCallSuffix callSuffix = suffixes.get(i + 1).callSuffix();
                        if (callSuffix != null) {
                            if (KotlinAstUtil.firstArgIsParam(callSuffix, paramNames)) return true;
                            // String template with param
                            if (KotlinAstUtil.descendantHasStringTemplateRefFromSet(callSuffix, paramNames)) return true;
                        }
                    }
                    break;
                }
            }
            return false;
        }
    }
}

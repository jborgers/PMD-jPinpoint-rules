package com.jpinpoint.perf.lang.kotlin.rule.common;

import com.jpinpoint.perf.lang.kotlin.util.KotlinAstUtil;
import net.sourceforge.pmd.lang.kotlin.AbstractKotlinRule;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinTerminalNode;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitor;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitorBase;
import net.sourceforge.pmd.lang.rule.RuleTargetSelector;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.*;

/**
 * Detects when a local String variable in a Kotlin function body is concatenated
 * to via 2+ separate assignment statements ({@code +=} or {@code var = var + ...}),
 * indicating repeated hidden StringBuilder creation.
 * Violation is reported at the last such statement.
 */
public class AvoidMultipleConcatStatementsRule extends AbstractKotlinRule {

    @Override
    protected RuleTargetSelector buildTargetSelector() {
        return RuleTargetSelector.forTypes(KotlinParser.KtFunctionBody.class);
    }

    @Override
    public KotlinVisitor<RuleContext, ?> buildVisitor() {
        return new Visitor();
    }

    private static final class Visitor extends KotlinVisitorBase<RuleContext, Void> {

        @Override
        public Void visitFunctionBody(KotlinParser.KtFunctionBody node, RuleContext ctx) {
            Set<String> stringParams = KotlinAstUtil.findParamNamesOfType(node, "String");
            Set<String> stringFunctions = KotlinAstUtil.findFunctionNamesReturningType(node, "String");

            // Collect string variable names declared directly in this function body (not in nested lambdas)
            Set<String> stringVarNames = new HashSet<>();
            for (KotlinParser.KtPropertyDeclaration propDecl :
                    node.descendants(KotlinParser.KtPropertyDeclaration.class).toList()) {
                if (!KotlinAstUtil.isDirectChildOfFunctionBody(propDecl, node)) continue;
                if (!isStringProperty(propDecl, stringParams, stringFunctions)) continue;
                KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
                if (varDecl != null) {
                    String name = KotlinAstUtil.getIdentifierText(varDecl.simpleIdentifier());
                    if (name != null) stringVarNames.add(name);
                }
            }

            if (stringVarNames.isEmpty()) {
                return visitChildren(node, ctx);
            }

            // Track concat-assignment statements per string variable, in source order
            Map<String, List<KotlinParser.KtStatement>> concatStmts = new LinkedHashMap<>();
            for (KotlinParser.KtStatement stmt :
                    node.descendants(KotlinParser.KtStatement.class)
                        .filter(s -> KotlinAstUtil.isDirectChildOfFunctionBody(s, node))
                        .toList()) {
                KotlinParser.KtAssignment assignment = stmt.assignment();
                if (assignment == null) continue;
                if (!hasConcatOperator(assignment)) continue;

                String lhsName = KotlinAstUtil.getLhsVarName(assignment);
                if (lhsName == null || !stringVarNames.contains(lhsName)) continue;

                concatStmts.computeIfAbsent(lhsName, k -> new ArrayList<>()).add(stmt);
            }

            // Report violation at the last concat statement for each variable with >= 2
            for (List<KotlinParser.KtStatement> stmts : concatStmts.values()) {
                if (stmts.size() >= 2) {
                    ctx.addViolation(stmts.get(stmts.size() - 1));
                }
            }

            return visitChildren(node, ctx);
        }

        /**
         * Determines if a property declaration represents a String variable.
         * Uses four heuristics, matching the original XPath rule.
         */
        private boolean isStringProperty(KotlinParser.KtPropertyDeclaration propDecl,
                                         Set<String> stringParams,
                                         Set<String> stringFunctions) {
            // Skip if initializer contains toMutableList (false positive guard for issue #651)
            KotlinParser.KtExpression initExpr = propDecl.expression();
            if (initExpr != null
                    && initExpr.descendants(KotlinTerminalNode.class)
                               .any(t -> "toMutableList".equals(t.getText()))) {
                return false;
            }

            // (a) explicit type annotation: var x: String = ...
            KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
            if (varDecl != null && KotlinAstUtil.typeContainsName(varDecl.type(), "String")) {
                return true;
            }

            if (initExpr == null) return false;

            // (b) initializer is a simple expression (no nested KtExpression) containing a string literal
            //     matches: var x = "" or var x = "text"
            boolean hasNestedExpr = initExpr.descendants(KotlinParser.KtExpression.class).nonEmpty();
            boolean hasStringLiteral = initExpr.descendants(KotlinParser.KtStringLiteral.class).nonEmpty();
            if (!hasNestedExpr && hasStringLiteral) {
                return true;
            }

            // (c) initializer references a String parameter by name
            if (initExpr.descendants(KotlinTerminalNode.class)
                        .any(t -> stringParams.contains(t.getText()))) {
                return true;
            }

            // (d) initializer calls a String-returning function at the top level
            //     (not as an argument inside another call's CallSuffix)
            for (KotlinTerminalNode t : initExpr.descendants(KotlinTerminalNode.class)
                    .filter(tok -> stringFunctions.contains(tok.getText()))
                    .toList()) {
                if (t.ancestors(KotlinParser.KtCallSuffix.class).isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Returns true if the assignment uses += or uses + in the RHS expression.
         * Uses text comparison ("+=", "+") because PMD's BaseAntlrTerminalNode.getTokenKind()
         * returns the token stream index, not the token type, making getToken(type, idx)
         * unreliable for operator checks.
         */
        private boolean hasConcatOperator(KotlinParser.KtAssignment assignment) {
            // Check for += (compound assignment)
            KotlinParser.KtAssignmentAndOperator andOp = assignment.assignmentAndOperator();
            if (andOp != null
                    && andOp.children(KotlinTerminalNode.class).any(t -> "+=".equals(t.getText()))) {
                return true;
            }
            // Check for + in plain assignment (e.g. x = x + y)
            return assignment.descendants(KotlinParser.KtAdditiveOperator.class)
                             .any(op -> op.children(KotlinTerminalNode.class)
                                          .any(t -> "+".equals(t.getText())));
        }
    }
}

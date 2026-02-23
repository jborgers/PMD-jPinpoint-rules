package com.jpinpoint.perf.lang.kotlin.rule.common;

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
            Set<String> stringParams = findStringParamNames(node);
            Set<String> stringFunctions = findStringFunctionNames(node);

            // Collect string variable names declared directly in this function body (not in nested lambdas)
            Set<String> stringVarNames = new HashSet<>();
            for (KotlinParser.KtPropertyDeclaration propDecl :
                    node.descendants(KotlinParser.KtPropertyDeclaration.class).toList()) {
                KotlinParser.KtFunctionBody nearestFb =
                        propDecl.ancestors(KotlinParser.KtFunctionBody.class).first();
                if (nearestFb != node) continue;
                if (!isStringProperty(propDecl, stringParams, stringFunctions)) continue;
                KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
                if (varDecl != null && varDecl.simpleIdentifier() != null) {
                    KotlinTerminalNode token =
                            varDecl.simpleIdentifier().children(KotlinTerminalNode.class).first();
                    if (token != null) {
                        stringVarNames.add(token.getText());
                    }
                }
            }

            if (stringVarNames.isEmpty()) {
                return visitChildren(node, ctx);
            }

            // Track concat-assignment statements per string variable, in source order
            Map<String, List<KotlinParser.KtStatement>> concatStmts = new LinkedHashMap<>();
            for (KotlinParser.KtStatement stmt :
                    node.descendants(KotlinParser.KtStatement.class)
                        .filter(s -> s.ancestors(KotlinParser.KtFunctionBody.class).first() == node)
                        .toList()) {
                KotlinParser.KtAssignment assignment = stmt.assignment();
                if (assignment == null) continue;
                if (!hasConcatOperator(assignment)) continue;

                String lhsName = getLhsVarName(assignment);
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
         * Finds names of parameters of type String in the enclosing function declaration.
         */
        private Set<String> findStringParamNames(KotlinParser.KtFunctionBody node) {
            Set<String> result = new HashSet<>();
            KotlinParser.KtFunctionDeclaration funcDecl =
                    node.ancestors(KotlinParser.KtFunctionDeclaration.class).first();
            if (funcDecl == null) return result;

            KotlinParser.KtFunctionValueParameters params = funcDecl.functionValueParameters();
            if (params == null) return result;

            for (KotlinParser.KtFunctionValueParameter param : params.functionValueParameter()) {
                KotlinParser.KtParameter p = param.parameter();
                if (p != null && p.type() != null
                        && p.type().descendants(KotlinTerminalNode.class)
                                   .any(t -> "String".equals(t.getText()))) {
                    KotlinParser.KtSimpleIdentifier nameId = p.simpleIdentifier();
                    if (nameId != null) {
                        KotlinTerminalNode token = nameId.children(KotlinTerminalNode.class).first();
                        if (token != null) {
                            result.add(token.getText());
                        }
                    }
                }
            }
            return result;
        }

        /**
         * Finds names of class-level functions that explicitly return String.
         */
        private Set<String> findStringFunctionNames(KotlinParser.KtFunctionBody node) {
            Set<String> result = new HashSet<>();
            KotlinParser.KtClassMemberDeclarations classMembers =
                    node.ancestors(KotlinParser.KtClassMemberDeclarations.class).first();
            if (classMembers == null) return result;

            for (KotlinParser.KtFunctionDeclaration funcDecl :
                    classMembers.descendants(KotlinParser.KtFunctionDeclaration.class).toList()) {
                KotlinParser.KtType returnType = funcDecl.type();
                if (returnType != null
                        && returnType.descendants(KotlinTerminalNode.class)
                                     .any(t -> "String".equals(t.getText()))) {
                    KotlinParser.KtSimpleIdentifier nameId = funcDecl.simpleIdentifier();
                    if (nameId != null) {
                        KotlinTerminalNode token = nameId.children(KotlinTerminalNode.class).first();
                        if (token != null) {
                            result.add(token.getText());
                        }
                    }
                }
            }
            return result;
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
            if (varDecl != null && varDecl.type() != null
                    && varDecl.type().descendants(KotlinTerminalNode.class)
                               .any(t -> "String".equals(t.getText()))) {
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

        /**
         * Extracts the simple identifier name from the LHS of an assignment.
         * Returns null if the LHS is not a simple identifier.
         */
        private String getLhsVarName(KotlinParser.KtAssignment assignment) {
            // directlyAssignableExpression covers `x = ...` (plain assignment)
            KotlinParser.KtDirectlyAssignableExpression dae = assignment.directlyAssignableExpression();
            if (dae != null && dae.simpleIdentifier() != null) {
                KotlinTerminalNode token = dae.simpleIdentifier().children(KotlinTerminalNode.class).first();
                if (token != null) return token.getText();
            }
            // assignableExpression covers `x += ...` (compound assignment)
            KotlinParser.KtAssignableExpression ae = assignment.assignableExpression();
            if (ae != null) {
                KotlinTerminalNode token = ae.descendants(KotlinTerminalNode.class).first();
                if (token != null) return token.getText();
            }
            return null;
        }
    }
}

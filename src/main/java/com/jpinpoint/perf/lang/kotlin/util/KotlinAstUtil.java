package com.jpinpoint.perf.lang.kotlin.util;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinTerminalNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Static utility methods for navigating the PMD 7 ANTLR-based Kotlin AST.
 *
 * <p><b>Key gotcha:</b> {@code BaseAntlrTerminalNode.getTokenKind()} returns the token-stream
 * index, not the token-type constant. Generated accessor methods such as {@code ADD()} or
 * {@code ADD_ASSIGNMENT()} therefore always return {@code null}.
 * Always use text comparison instead: {@code t.getText().equals("+=")} etc.</p>
 */
public final class KotlinAstUtil {

    private KotlinAstUtil() { /* utility class */ }

    /**
     * Returns the text of the first terminal-node child of a {@link KotlinParser.KtSimpleIdentifier},
     * or {@code null} if the node itself is {@code null} or has no terminal children.
     */
    public static String getIdentifierText(KotlinParser.KtSimpleIdentifier simpleId) {
        if (simpleId == null) return null;
        KotlinTerminalNode token = simpleId.children(KotlinTerminalNode.class).first();
        return token != null ? token.getText() : null;
    }

    /**
     * Returns {@code true} if any terminal-node descendant of {@code type} has text equal to
     * {@code typeName}. Useful for checking type annotations like {@code var x: String} or a
     * declared return type of {@code String}.
     */
    public static boolean typeContainsName(KotlinParser.KtType type, String typeName) {
        if (type == null || typeName == null) return false;
        return type.descendants(KotlinTerminalNode.class).any(t -> typeName.equals(t.getText()));
    }

    /**
     * Returns {@code true} if the nearest enclosing {@link KotlinParser.KtFunctionBody} ancestor
     * of {@code node} is exactly {@code body}. Used to restrict descendant searches to a single
     * function body without crossing into nested lambdas or local functions.
     */
    public static boolean isDirectChildOfFunctionBody(Node node, KotlinParser.KtFunctionBody body) {
        return node.ancestors(KotlinParser.KtFunctionBody.class).first() == body;
    }

    /**
     * Extracts the simple variable name from the left-hand side of an assignment.
     * Handles both plain assignment ({@code x = ...}) and compound assignment ({@code x += ...}).
     * Returns {@code null} if the LHS is not a simple identifier (e.g. property access, index).
     */
    public static String getLhsVarName(KotlinParser.KtAssignment assignment) {
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

    /**
     * Returns the names of all parameters whose type annotation contains {@code typeName} in the
     * function declaration that directly encloses {@code functionBody}.
     */
    public static Set<String> findParamNamesOfType(KotlinParser.KtFunctionBody functionBody,
                                                    String typeName) {
        Set<String> result = new HashSet<>();
        KotlinParser.KtFunctionDeclaration funcDecl =
                functionBody.ancestors(KotlinParser.KtFunctionDeclaration.class).first();
        if (funcDecl == null) return result;

        KotlinParser.KtFunctionValueParameters params = funcDecl.functionValueParameters();
        if (params == null) return result;

        for (KotlinParser.KtFunctionValueParameter param : params.functionValueParameter()) {
            KotlinParser.KtParameter p = param.parameter();
            if (p != null && typeContainsName(p.type(), typeName)) {
                String name = getIdentifierText(p.simpleIdentifier());
                if (name != null) result.add(name);
            }
        }
        return result;
    }

    /**
     * Returns the names of all class-level functions that explicitly declare {@code typeName} as
     * their return type, searching within the class body that encloses {@code functionBody}.
     */
    public static Set<String> findFunctionNamesReturningType(KotlinParser.KtFunctionBody functionBody,
                                                              String typeName) {
        Set<String> result = new HashSet<>();
        KotlinParser.KtClassMemberDeclarations classMembers =
                functionBody.ancestors(KotlinParser.KtClassMemberDeclarations.class).first();
        if (classMembers == null) return result;

        for (KotlinParser.KtFunctionDeclaration funcDecl :
                classMembers.descendants(KotlinParser.KtFunctionDeclaration.class).toList()) {
            if (typeContainsName(funcDecl.type(), typeName)) {
                String name = getIdentifierText(funcDecl.simpleIdentifier());
                if (name != null) result.add(name);
            }
        }
        return result;
    }
}

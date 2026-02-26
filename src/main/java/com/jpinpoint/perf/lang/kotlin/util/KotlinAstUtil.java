package com.jpinpoint.perf.lang.kotlin.util;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinTerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    // -------------------------------------------------------------------------
    // Identifier helpers
    // -------------------------------------------------------------------------

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
     * Returns the text of the SimpleIdentifier child of a {@link KotlinParser.KtPrimaryExpression},
     * or {@code null} if none is present.
     */
    public static String getPrimaryExpressionSimpleIdentifierText(KotlinParser.KtPrimaryExpression pe) {
        if (pe == null) return null;
        return getIdentifierText(pe.simpleIdentifier());
    }

    // -------------------------------------------------------------------------
    // Type / field helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if any terminal-node descendant of {@code type} has text equal to
     * {@code typeName}. Useful for checking type annotations like {@code var x: String} or a
     * declared return type of {@code String}.
     */
    public static boolean typeContainsName(KotlinParser.KtType type, String typeName) {
        if (type == null || typeName == null) return false;
        return type.descendants(KotlinTerminalNode.class).any(t -> typeName.equals(t.getText()));
    }

    // -------------------------------------------------------------------------
    // Scope helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the nearest enclosing {@link KotlinParser.KtFunctionBody} ancestor
     * of {@code node} is exactly {@code body}. Used to restrict descendant searches to a single
     * function body without crossing into nested lambdas or local functions.
     */
    public static boolean isDirectChildOfFunctionBody(Node node, KotlinParser.KtFunctionBody body) {
        return node.ancestors(KotlinParser.KtFunctionBody.class).first() == body;
    }

    /**
     * Returns {@code true} if the nearest enclosing {@link KotlinParser.KtFunctionDeclaration}
     * ancestor of {@code node} is exactly {@code funcDecl}. Used to restrict descendant searches
     * to a single function declaration without crossing into nested local functions.
     * Note: lambdas (KtFunctionLiteral) are not KtFunctionDeclaration so they are transparent to this check.
     */
    public static boolean isDirectDescendantOfFunctionDeclaration(Node node,
                                                                    KotlinParser.KtFunctionDeclaration funcDecl) {
        return node.ancestors(KotlinParser.KtFunctionDeclaration.class).first() == funcDecl;
    }

    // -------------------------------------------------------------------------
    // Assignment helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Parameter collection
    // -------------------------------------------------------------------------

    /**
     * Returns the names of all parameters in the function declaration (regardless of type).
     */
    public static Set<String> collectAllParamNames(KotlinParser.KtFunctionDeclaration funcDecl) {
        Set<String> result = new HashSet<>();
        if (funcDecl == null) return result;
        KotlinParser.KtFunctionValueParameters params = funcDecl.functionValueParameters();
        if (params == null) return result;
        for (KotlinParser.KtFunctionValueParameter param : params.functionValueParameter()) {
            KotlinParser.KtParameter p = param.parameter();
            if (p != null) {
                String name = getIdentifierText(p.simpleIdentifier());
                if (name != null) result.add(name);
            }
        }
        return result;
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

    // -------------------------------------------------------------------------
    // Local variable collection
    // -------------------------------------------------------------------------

    /**
     * Returns the names of all local variables (PropertyDeclarations) declared anywhere within
     * {@code functionBody}, including inside nested lambdas. This matches the XPath behaviour of
     * {@code ancestor::FunctionBody//PropertyDeclaration/VariableDeclaration/...}.
     */
    public static Set<String> collectLocalVarNames(KotlinParser.KtFunctionBody functionBody) {
        Set<String> result = new HashSet<>();
        if (functionBody == null) return result;
        for (KotlinParser.KtPropertyDeclaration propDecl :
                functionBody.descendants(KotlinParser.KtPropertyDeclaration.class).toList()) {
            KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
            if (varDecl != null) {
                String name = getIdentifierText(varDecl.simpleIdentifier());
                if (name != null) result.add(name);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Class field collection
    // -------------------------------------------------------------------------

    /**
     * Returns the names of all mutable ({@code var}) class fields declared in the class
     * body that encloses {@code node}. This matches the XPath pattern
     * {@code ancestor::ClassDeclaration//PropertyDeclaration[T-VAR]//SimpleIdentifier/T-Identifier/@Text}.
     */
    public static Set<String> collectClassVarFieldNames(Node node) {
        Set<String> result = new HashSet<>();
        KotlinParser.KtClassDeclaration classDecl =
                node.ancestors(KotlinParser.KtClassDeclaration.class).first();
        if (classDecl == null) return result;
        for (KotlinParser.KtPropertyDeclaration propDecl :
                classDecl.descendants(KotlinParser.KtPropertyDeclaration.class).toList()) {
            // Check for `var` keyword using text comparison (not VAR() method which may be unreliable)
            if (propDecl.children(KotlinTerminalNode.class).any(t -> "var".equals(t.getText()))) {
                KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
                if (varDecl != null) {
                    String name = getIdentifierText(varDecl.simpleIdentifier());
                    if (name != null) result.add(name);
                }
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

    // -------------------------------------------------------------------------
    // Import checking
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the Kotlin file enclosing {@code node} has an import that matches
     */
    public static boolean hasImport(Node node, String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.isEmpty()) return false;
        String[] identifier = fullyQualifiedName.split("\\.");
        return hasImport(node, identifier);
    }

    /**
     * Returns {@code true} if the Kotlin file enclosing {@code node} has an import that matches
     * all the given identifier parts (or has a wildcard import). For example, to check for
     * {@code import java.util.regex.Pattern}, pass {@code "java", "util", "regex", "Pattern"}.
     *
     * <p>A wildcard import (containing {@code *}) always matches.</p>
     */
    public static boolean hasImport(Node node, String... identifiers) {
        KotlinParser.KtKotlinFile file = node.getClass().equals(KotlinParser.KtKotlinFile.class) ? (KotlinParser.KtKotlinFile) node : node.ancestors(KotlinParser.KtKotlinFile.class).first();
        if (file == null) return false;
        KotlinParser.KtImportList importList = file.importList();
        if (importList == null) return false;

        for (KotlinParser.KtImportHeader importHeader : importList.importHeader()) {
            List<KotlinTerminalNode> tokens = importHeader.descendants(KotlinTerminalNode.class).toList();

            // Build identifier-like token list directly from terminal nodes (keep original order).
            List<String> idTokens = new ArrayList<>();
            for (KotlinTerminalNode tn : tokens) {
                String txt = tn.getText().trim();
                if (txt.isEmpty()) continue;
                idTokens.add(txt);
            }

            // If this import has a wildcard (e.g. import x.y.z.*), only match when the prefix before '*'
            // equals the leading part of the requested identifiers. For example, import a.b.* matches
            // identifiers ["a","b","C"] but should NOT match an unrelated package.
            if (!idTokens.isEmpty() && lastTokenIsWildcard(idTokens)) {
                int prefixLen = idTokens.size() - 1;
                if (identifiers.length >= prefixLen) {
                    boolean prefixMatches = true;
                    for (int i = 0; i < prefixLen; i++) {
                        if (!idTokens.get(i).equals(identifiers[i])) {
                            prefixMatches = false;
                            break;
                        }
                    }
                    if (prefixMatches) return true;
                }
                // wildcard present but prefix doesn't match; continue checking other imports
                continue;
            }

            // No wildcard: check whether the requested identifiers appear in order within the identifier tokens.
            // This is a subsequence match: each identifier must be found in order (not necessarily adjacent).
            boolean allPresent = identifierMatchAllTokens(identifiers, idTokens);
            if (allPresent) return true;
        }
        return false;
    }

    private static boolean lastTokenIsWildcard(List<String> idTokens) {
        return "*".equals(idTokens.get(idTokens.size() - 1));
    }

    private static boolean identifierMatchAllTokens(String[] identifiers, List<String> idTokens) {
        boolean allPresent = true;
        int pos = 0;
        for (String id : identifiers) {
            boolean found = false;
            for (int i = pos; i < idTokens.size(); i++) {
                if (id.equals(idTokens.get(i))) {
                    found = true;
                    pos = i + 1;
                    break;
                }
            }
            if (!found) {
                allPresent = false;
                break;
            }
        }
        return allPresent;
    }

    // -------------------------------------------------------------------------
    // Expression content helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code node} has any descendant {@link KotlinTerminalNode} whose
     * text is in {@code names}. Useful for checking if an expression references any of a set
     * of variable names.
     */
    public static boolean descendantHasIdentifierFromSet(Node node, Set<String> names) {
        if (node == null || names == null || names.isEmpty()) return false;
        return node.descendants(KotlinTerminalNode.class).any(t -> names.contains(t.getText()));
    }

    /**
     * Returns {@code true} if any {@link KotlinParser.KtLineStringContent} descendant of
     * {@code node} has a {@code LineStrRef} token whose text (including the leading {@code $})
     * equals {@code "$" + name} for some {@code name} in {@code names}.
     * <p>
     * This matches the XPath pattern
     * {@code //LineStringContent/T-LineStrRef[@Text = concat('$', name)]}.
     */
    public static boolean descendantHasStringTemplateRefFromSet(Node node, Set<String> names) {
        if (node == null || names == null || names.isEmpty()) return false;
        for (KotlinParser.KtLineStringContent lsc :
                node.descendants(KotlinParser.KtLineStringContent.class).toList()) {
            KotlinTerminalNode lineStrRef = lsc.children(KotlinTerminalNode.class)
                    .filter(t -> t.getText().startsWith("$"))
                    .first();
            if (lineStrRef != null) {
                String refText = lineStrRef.getText(); // e.g. "$context1"
                if (refText.length() > 1 && names.contains(refText.substring(1))) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Regex/PathMatcher helpers (moved from rule)
    // -------------------------------------------------------------------------

    /** Returns true if the NavigationSuffix refers to {@code toRegex}. */
    public static boolean isToRegexNavSuffix(KotlinParser.KtNavigationSuffix navSuffix) {
        return "toRegex".equals(getIdentifierText(navSuffix.simpleIdentifier()));
    }

    /** Returns true if the PostfixUnaryExpression represents a {@code Regex(...)} constructor call. */
    public static boolean isRegexConstructorCall(KotlinParser.KtPostfixUnaryExpression pue) {
        KotlinParser.KtPrimaryExpression pe = pue.primaryExpression();
        if (pe == null) return false;
        if (!"Regex".equals(getIdentifierText(pe.simpleIdentifier()))) return false;
        KotlinParser.KtPostfixUnarySuffix firstSuffix = pue.postfixUnarySuffix(0);
        return firstSuffix != null && firstSuffix.callSuffix() != null;
    }

    /** Returns the CallSuffix of the first PostfixUnarySuffix, or null. */
    public static KotlinParser.KtCallSuffix getFirstCallSuffix(KotlinParser.KtPostfixUnaryExpression pue) {
        KotlinParser.KtPostfixUnarySuffix firstSuffix = pue.postfixUnarySuffix(0);
        return firstSuffix != null ? firstSuffix.callSuffix() : null;
    }

    /** Returns true if the NavigationSuffix refers to {@code getPathMatcher}. */
    public static boolean isGetPathMatcherNavSuffix(KotlinParser.KtNavigationSuffix navSuffix) {
        return "getPathMatcher".equals(getIdentifierText(navSuffix.simpleIdentifier()));
    }

    /** Returns true if the getPathMatcher call is on a FileSystems instance. */
    public static boolean isOnFileSystemsReceiver(KotlinParser.KtNavigationSuffix navSuffix, Set<String> fileSystemsVarNames) {
        KotlinParser.KtPostfixUnarySuffix suffix = navSuffix.ancestors(KotlinParser.KtPostfixUnarySuffix.class).first();
        if (suffix == null) return false;
        KotlinParser.KtPostfixUnaryExpression pue = suffix.ancestors(KotlinParser.KtPostfixUnaryExpression.class).first();
        if (pue == null) return false;
        String receiverName = getPrimaryExpressionSimpleIdentifierText(pue.primaryExpression());
        if (receiverName == null) return false;
        if ("FileSystems".equals(receiverName)) return true;
        return fileSystemsVarNames.contains(receiverName);
    }

    /**
     * Collects names of local variables in the function body that were initialized from
     * an expression containing {@code FileSystems} (e.g. {@code val fs = FileSystems.getDefault()}).
     */
    public static Set<String> collectFileSystemsVarNames(KotlinParser.KtFunctionDeclaration funcDecl) {
        Set<String> result = new HashSet<>();
        KotlinParser.KtFunctionBody body = funcDecl.functionBody();
        if (body == null) return result;
        for (KotlinParser.KtStatement stmt : body.descendants(KotlinParser.KtStatement.class).toList()) {
            KotlinParser.KtDeclaration decl = stmt.declaration();
            if (decl == null) continue;
            KotlinParser.KtPropertyDeclaration propDecl = decl.propertyDeclaration();
            if (propDecl == null) continue;
            KotlinParser.KtExpression initExpr = propDecl.expression();
            if (initExpr == null) continue;
            if (initExpr.descendants(KotlinTerminalNode.class)
                    .any(t -> "FileSystems".equals(t.getText()))) {
                KotlinParser.KtVariableDeclaration varDecl = propDecl.variableDeclaration();
                if (varDecl != null) {
                    String name = getIdentifierText(varDecl.simpleIdentifier());
                    if (name != null) result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * Returns true if the first ValueArgument in the CallSuffix is a simple identifier
     * that matches a function parameter name.
     */
    public static boolean firstArgIsParam(KotlinParser.KtCallSuffix callSuffix, Set<String> paramNames) {
        if (callSuffix.valueArguments() == null) return false;
        List<KotlinParser.KtValueArgument> args = callSuffix.valueArguments().valueArgument();
        if (args.isEmpty()) return false;
        KotlinParser.KtValueArgument firstArg = args.get(0);
        return firstArg.descendants(KotlinParser.KtPrimaryExpression.class)
                .any(pe -> paramNames.contains(getPrimaryExpressionSimpleIdentifierText(pe)));
    }

}


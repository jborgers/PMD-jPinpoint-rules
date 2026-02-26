package com.jpinpoint.perf.lang.kotlin.util;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinTerminalNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddEqualsTest {

    private Node parseResource(String resourcePath) throws Exception {
        String code = new String(Files.readAllBytes(Paths.get(resourcePath)));
        return KotlinParsingHelper.DEFAULT.parse(code);
    }

    @Test
    public void testHasAddEquals() throws Exception {
        Node node = parseResource("src/test/resources/com/jpinpoint/perf/lang/kotlin/util/AddEquals.kt");
        // find all "+=" nodes
        List<KotlinTerminalNode> addEqualsNodes = node.descendantsOrSelf()
                        .filter(n -> n instanceof KotlinTerminalNode)
                        .map(n -> (KotlinTerminalNode) n)
                        .filter(n -> n.getText().equals("+="))
                        .toList();

        assertEquals(2, addEqualsNodes.size());

        // this one is broken because of the bug https://github.com/pmd/pmd/issues/6471
        List<KotlinParser.KtAssignmentAndOperator> addAssigmentNodes = node.descendantsOrSelf()
                .filter(n -> n instanceof KotlinParser.KtAssignmentAndOperator)
                .map(n -> (KotlinParser.KtAssignmentAndOperator) n)
                .filter(n -> n.ADD_ASSIGNMENT() != null)
                .toList();

        // this is now actually 0, activate after fixing the bug
        //assertEquals(2, addAssigmentNodes.size());
    }
}

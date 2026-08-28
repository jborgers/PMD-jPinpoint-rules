package com.jpinpoint.perf.lang.kotlin.util;

import net.sourceforge.pmd.lang.ast.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KotlinAstUtilTest {

    private Node parseResource(String resourcePath) throws Exception {
        String code = new String(Files.readAllBytes(Paths.get(resourcePath)));
        // KotlinParsingHelper is in the same package and extends BaseParsingHelper
        return KotlinParsingHelper.DEFAULT.parse(code);
    }

    @Test
    public void testHasImportExactMatch() throws Exception {
        Node node = parseResource("src/test/resources/com/jpinpoint/perf/lang/kotlin/util/TestImports.kt");
        assertTrue(KotlinAstUtil.hasImport(node, "java.util.regex.Pattern"));
        assertTrue(KotlinAstUtil.hasImport(node, "java", "util", "regex", "Pattern"));
    }

    @Test
    public void testHasImportWildcard() throws Exception {
        Node node = parseResource("src/test/resources/com/jpinpoint/perf/lang/kotlin/util/TestImports.kt");
        assertTrue(KotlinAstUtil.hasImport(node, "java.util.regex.Pattern"));
        assertTrue(KotlinAstUtil.hasImport(node, "java", "util", "regex", "Pattern"));
    }

    @Test
    public void testHasImportNegative() throws Exception {
        Node node = parseResource("src/test/resources/com/jpinpoint/perf/lang/kotlin/util/TestImports.kt");
        assertFalse(KotlinAstUtil.hasImport(node, "not.present.Import"));
    }

    @Test
    public void testHasImportMultipleIdentifiers() throws Exception {
        Node node = parseResource("src/test/resources/com/jpinpoint/perf/lang/kotlin/util/TestImports.kt");
        assertTrue(KotlinAstUtil.hasImport(node, "java.util.regex.Pattern"));
        assertTrue(KotlinAstUtil.hasImport(node, "org.example.Foo"));
        assertTrue(KotlinAstUtil.hasImport(node, "java.util.MatchAnything"));
        assertFalse(KotlinAstUtil.hasImport(node, "io.test.ListNotPresent"));
    }
}

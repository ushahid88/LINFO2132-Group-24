package compiler;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/** Loads programs from {@code tests/lang} (same sources as manual Gradle runs). */
public class CompilerFeatureLangFilesTest {

    private static Path lang(String name) {
        return Path.of(System.getProperty("user.dir")).resolve("tests").resolve("lang").resolve(name);
    }

    private static String runFile(String fileName) throws Exception {
        String source = CompileHarness.readUtf8(lang(fileName));
        return CompileHarness.runProgram(source, "Program");
    }

    @Test
    public void hello() throws Exception {
        assertEquals("ok", runFile("hello.lang"));
    }

    @Test
    public void featureWhile() throws Exception {
        assertEquals("012\n", runFile("feature_while.lang"));
    }

    @Test
    public void featureIf() throws Exception {
        assertEquals("yes", runFile("feature_if.lang"));
    }

    @Test
    public void featureCollection() throws Exception {
        assertEquals("9\n", runFile("feature_collection.lang"));
    }

    @Test
    public void extraShortcircuitFile() throws Exception {
        assertEquals("00\n", runFile("extra_shortcircuit.lang"));
    }

    @Test
    public void extraStrFloorCeilFile() throws Exception {
        assertEquals("42\n3\n-3\n3\n", runFile("extra_str_floor_ceil.lang"));
    }
}

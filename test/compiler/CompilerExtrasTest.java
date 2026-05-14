package compiler;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/** Extra features: short-circuit booleans, INT/FLOAT promotion, builtins. */
public class CompilerExtrasTest {

    @Test
    public void shortCircuitSkipsRightOperand() {
        String src = ""
                + "INT counter = 0;\n"
                + "\n"
                + "def BOOL side() {\n"
                + "    counter = counter + 1;\n"
                + "    return true;\n"
                + "}\n"
                + "\n"
                + "def main() {\n"
                + "    BOOL a = false && side();\n"
                + "    printINT(counter);\n"
                + "    BOOL b = true || side();\n"
                + "    printINT(counter);\n"
                + "    println(\"\");\n"
                + "}\n";
        assertEquals("00\n", CompileHarness.runProgramOrFail("shortCircuit", src, "Program"));
    }

    @Test
    public void intFloatPromotionInArithmetic() {
        String src = ""
                + "def main() {\n"
                + "    FLOAT x = 1 + 2.5;\n"
                + "    printFLOAT(x);\n"
                + "    println(\"\");\n"
                + "}\n";
        String out = CompileHarness.runProgramOrFail("promotion", src, "Program");
        assertEquals("3.5\n", out);
    }

    @Test
    public void strLengthFloorCeil() {
        String src = ""
                + "def main() {\n"
                + "    print(str(42));\n"
                + "    println(\"\");\n"
                + "    printINT(length(\"abc\"));\n"
                + "    println(\"\");\n"
                + "    printINT(floor(-2.7));\n"
                + "    println(\"\");\n"
                + "    printINT(ceil(2.2));\n"
                + "    println(\"\");\n"
                + "}\n";
        assertEquals("42\n3\n-3\n3\n", CompileHarness.runProgramOrFail("builtins", src, "Program"));
    }

    @Test
    public void readIntFromStdin() {
        String src = ""
                + "def main() {\n"
                + "    INT x = read_INT();\n"
                + "    printINT(x);\n"
                + "    println(\"\");\n"
                + "}\n";
        InputStream oldIn = System.in;
        System.setIn(new ByteArrayInputStream("15\n".getBytes(StandardCharsets.UTF_8)));
        try {
            assertEquals("15\n", CompileHarness.runProgramOrFail("readInt", src, "Program"));
        } finally {
            System.setIn(oldIn);
        }
    }

    @Test
    public void langFileExtraMixedFloat() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        Path file = root.resolve("tests").resolve("lang").resolve("extra_mixed_float.lang");
        String source = CompileHarness.readUtf8(file);
        assertEquals("3.5\n", CompileHarness.runProgramOrFail("extra_mixed_float.lang", source, "Program"));
    }
}

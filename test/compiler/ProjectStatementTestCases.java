package compiler;

import compiler.Lexer.Lexer;
import compiler.codegen.CodeGenerator;
import compiler.codegen.CodeGenException;
import compiler.parser.AstNode;
import compiler.parser.Parser;
import compiler.semantic.SemanticAnalyzer;
import compiler.semantic.SemanticException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * End-to-end checks for the eight sample programs in the course test-case list
 * (lexer → parser → semantic analysis → JVM bytecode → captured stdout).
 */
public class ProjectStatementTestCases {

    private static final String TEST1 = ""
            + "def main() {\n"
            + "    printINT(1);\n"
            + "    println(\"\");\n"
            + "    printFLOAT(1.1);\n"
            + "    println(\"\");\n"
            + "    print(\"hello\");\n"
            + "}\n";

    /** {@code println(\"\")} emits one newline; there is no extra blank line between values. */
    private static final String EXPECTED1 = ""
            + "1\n"
            + "1.1\n"
            + "hello";

    private static final String TEST2 = ""
            + "def main() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value);\n"
            + "}\n";

    private static final String EXPECTED2 = "1";

    private static final String TEST3 = ""
            + "def main() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value + 4);\n"
            + "}\n";

    private static final String EXPECTED3 = "5";

    private static final String TEST4 = ""
            + "def main() {\n"
            + "    INT a = 10;\n"
            + "    INT b = 5;\n"
            + "\n"
            + "    print(\"Addition: \");\n"
            + "    printINT(a + b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Subtraction: \");\n"
            + "    printINT(a - b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Unary Minus: \");\n"
            + "    printINT(-a);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Multiplication: \");\n"
            + "    printINT(a * b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Division: \");\n"
            + "    printINT(a / b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Modulus: \");\n"
            + "    printINT(a % b);\n"
            + "    println(\"\");\n"
            + "}\n";

    private static final String EXPECTED4 = ""
            + "Addition: 15\n"
            + "Subtraction: 5\n"
            + "Unary Minus: -10\n"
            + "Multiplication: 50\n"
            + "Division: 2\n"
            + "Modulus: 0\n";

    private static final String TEST5 = ""
            + "def main() {\n"
            + "    FLOAT a = 10.5;\n"
            + "    FLOAT b = 5.2;\n"
            + "\n"
            + "    print(\"Addition: \");\n"
            + "    printFLOAT(a + b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Subtraction: \");\n"
            + "    printFLOAT(a - b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Unary Minus: \");\n"
            + "    printFLOAT(-a);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    print(\"Multiplication: \");\n"
            + "    printFLOAT(a * b);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    FLOAT c = 4.4;\n"
            + "    FLOAT d = 2.2;\n"
            + "\n"
            + "    print(\"Division: \");\n"
            + "    printFLOAT(c / d);\n"
            + "    println(\"\");\n"
            + "}\n";

    private static final String EXPECTED5 = ""
            + "Addition: 15.7\n"
            + "Subtraction: 5.3\n"
            + "Unary Minus: -10.5\n"
            + "Multiplication: 54.6\n"
            + "Division: 2.0\n";

    private static final String TEST6 = ""
            + "def scopeTest() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value);\n"
            + "}\n"
            + "\n"
            + "def main() {\n"
            + "    INT value = 2;\n"
            + "    scopeTest();\n"
            + "    printINT(value);\n"
            + "}\n";

    /** {@code printINT} maps to {@code PrintStream#print} (no line terminator). */
    private static final String EXPECTED6 = "12";

    private static final String TEST7 = ""
            + "coll Testing {\n"
            + "    INT x;\n"
            + "}\n"
            + "\n"
            + "def main() {\n"
            + "    Testing test = Testing(3);\n"
            + "    printINT(test.x);\n"
            + "}\n";

    private static final String EXPECTED7 = "3";

    private static final String TEST8 = ""
            + "def main() {\n"
            + "    INT i = 0;\n"
            + "    INT result = 0;\n"
            + "\n"
            + "    while(i < 5) {\n"
            + "        i = i + 1;\n"
            + "        result = result + i;\n"
            + "    }\n"
            + "\n"
            + "    print(\"While Result: \");\n"
            + "    printINT(result);\n"
            + "    println(\"\");\n"
            + "\n"
            + "    for(i; 1 -> 5; i + 1) {\n"
            + "        if(i > 3) {\n"
            + "            print(\"High \");\n"
            + "        } else {\n"
            + "            print(\"Low \");\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    /** Statement sheet shows four words; source uses trailing spaces inside literals. */
    private static final String EXPECTED8 = ""
            + "While Result: 15\n"
            + "Low Low Low High ";

    private String runCompiledMain(String source, String mainClassName) throws Exception {
        Path dir = Files.createTempDirectory("langtest-");
        try {
            AstNode ast = parse(source);
            SemanticAnalyzer.analyze(ast);
            CodeGenerator gen = new CodeGenerator(mainClassName, dir);
            gen.generate(ast);
            gen.writeClassFiles();

            URL[] urls = {dir.toUri().toURL()};
            try (URLClassLoader cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader())) {
                Class<?> programClass = Class.forName(mainClassName, true, cl);
                Method main = programClass.getMethod("main", String[].class);

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                PrintStream capture = new PrintStream(buf, true, StandardCharsets.UTF_8);
                PrintStream old = System.out;
                System.setOut(capture);
                try {
                    main.invoke(null, (Object) new String[0]);
                } finally {
                    System.setOut(old);
                }
                return buf.toString(StandardCharsets.UTF_8.name()).replace("\r\n", "\n");
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(Path root) {
        try {
            if (Files.exists(root)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static AstNode parse(String input) {
        return new Parser(new Lexer(new StringReader(input))).getAST();
    }

    private void assertProgramOutput(String name, String source, String expected) {
        try {
            String actual = runCompiledMain(source, "Program");
            assertEquals(name, expected, actual);
        } catch (SemanticException e) {
            fail(name + " semantic: " + e.getMessage());
        } catch (CodeGenException e) {
            fail(name + " codegen: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(name, e);
        }
    }

    @Test
    public void test01_printing() {
        assertProgramOutput("test1", TEST1, EXPECTED1);
    }

    @Test
    public void test02_assignment() {
        assertProgramOutput("test2", TEST2, EXPECTED2);
    }

    @Test
    public void test03_addition() {
        assertProgramOutput("test3", TEST3, EXPECTED3);
    }

    @Test
    public void test04_integerOperations() {
        assertProgramOutput("test4", TEST4, EXPECTED4);
    }

    @Test
    public void test05_floatOperations() {
        assertProgramOutput("test5", TEST5, EXPECTED5);
    }

    @Test
    public void test06_scope() {
        assertProgramOutput("test6", TEST6, EXPECTED6);
    }

    @Test
    public void test07_collections() {
        assertProgramOutput("test7", TEST7, EXPECTED7);
    }

    @Test
    public void test08_loopsAndConditions() {
        assertProgramOutput("test8", TEST8, EXPECTED8);
    }
}

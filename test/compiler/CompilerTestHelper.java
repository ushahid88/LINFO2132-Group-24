package compiler;

import compiler.Lexer.Lexer;
import compiler.codegen.CodeGenerator;
import compiler.codegen.CodeGenException;
import compiler.parser.AstNode;
import compiler.parser.Parser;
import compiler.semantic.SemanticAnalyzer;
import compiler.semantic.SemanticException;

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

public final class CompilerTestHelper {

    private CompilerTestHelper() {
    }

    public static AstNode parse(String input) {
        return new Parser(new Lexer(new StringReader(input))).getAST();
    }

    public static String runCompiledMain(String source, String mainClassName) throws Exception {
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

    public static void assertProgramOutput(String name, String source, String expected) {
        try {
            assertEquals(name, expected, runCompiledMain(source, "Program"));
        } catch (SemanticException e) {
            fail(name + " semantic: " + e.getMessage());
        } catch (CodeGenException e) {
            fail(name + " codegen: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(name, e);
        }
    }

    public static String readProgramResource(String fileName) throws Exception {
        Path path = Path.of("test", "resources", "programs", fileName);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void deleteTree(Path root) {
        try {
            if (Files.exists(root)) {
                try (var walk = Files.walk(root)) {
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
}

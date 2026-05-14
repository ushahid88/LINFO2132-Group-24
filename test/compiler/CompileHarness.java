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
import java.util.Comparator;

/**
 * Compiles in-memory source to a temporary directory, loads the main class, runs {@code main}, returns stdout.
 */
public final class CompileHarness {

    private CompileHarness() {
    }

    public static AstNode parse(String input) {
        return new Parser(new Lexer(new StringReader(input))).getAST();
    }

    public static String runProgram(String source, String mainClassName) throws Exception {
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
                PrintStream oldOut = System.out;
                System.setOut(capture);
                try {
                    main.invoke(null, (Object) new String[0]);
                } finally {
                    System.setOut(oldOut);
                }
                return buf.toString(StandardCharsets.UTF_8.name()).replace("\r\n", "\n");
            }
        } finally {
            deleteTree(dir);
        }
    }

    public static String runProgramOrFail(String name, String source, String mainClassName) {
        try {
            return runProgram(source, mainClassName);
        } catch (SemanticException e) {
            throw new AssertionError(name + " semantic: " + e.getMessage(), e);
        } catch (CodeGenException e) {
            throw new AssertionError(name + " codegen: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AssertionError(name, e);
        }
    }

    public static String readUtf8(Path path) throws java.io.IOException {
        return Files.readString(path);
    }

    private static void deleteTree(Path root) {
        try {
            if (Files.exists(root)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
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

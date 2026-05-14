package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.parser.AstNode;
import compiler.parser.AstPrinter;
import compiler.parser.Parser;
import compiler.semantic.SemanticAnalyzer;
import compiler.codegen.CodeGenerator;
import compiler.codegen.CodeGenException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Compiler {

    private static final String DEFAULT_INPUT = "code_example.txt";
    private static final String DEFAULT_CLASS_NAME = "Program";
    private static final String DEFAULT_OUTPUT_DIR = "./bin/default";

    public static void main(String[] args) {
        if (args.length >= 1 && "-lexer".equals(args[0])) {
            String file = (args.length >= 2) ? args[1] : DEFAULT_INPUT;
            runLexer(file);
            return;
        }

        if (args.length >= 2 && "-parser".equals(args[0])) {
            runParser(args[1]);
            return;
        }

        // Check for -o flag for code generation
        // Format: source_file -o target_file
        // Or: source_file (no -o flag, use defaults)
        if (args.length >= 1 && !args[0].startsWith("-")) {
            String sourceFile = args[0];
            String targetPath = null;

            for (int i = 1; i < args.length; i++) {
                if ("-o".equals(args[i]) && i + 1 < args.length) {
                    targetPath = args[i + 1];
                    break;
                }
            }

            runCodeGeneration(sourceFile, targetPath);
            return;
        }

        // Default to parser mode if a single file argument is provided
        if (args.length == 1) {
            runParser(args[0]);
            return;
        }

        System.out.println("Hello from the compiler !");
        System.out.println("Usage:");
        System.out.println("  gradle run --args=\"-lexer\"                         # uses code_example.txt");
        System.out.println("  gradle run --args=\"-lexer <input_file>\"            # uses provided file");
        System.out.println("  gradle run --args=\"<source_file>\"                  # parse + semantic analysis");
        System.out.println("  gradle run --args=\"<source_file> -o <target>\"      # compile to .class file");
    }

    private static void runLexer(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        try (BufferedReader br = Files.newBufferedReader(p)) {
            Lexer lexer = new Lexer(br);

            while (true) {
                Symbol s = lexer.getNextSymbol();
                System.out.println(s);
                if (s.getKind() == Symbol.Kind.EOF) break;
            }
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Cannot read file: " + p + " (" + e.getMessage() + ")");
            System.exit(2);
        }
    }

    private static void runParser(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        try (BufferedReader br = Files.newBufferedReader(p)) {
            Lexer lexer = new Lexer(br);
            Parser parser = new Parser(lexer);
            AstNode ast = parser.getAST();
            SemanticAnalyzer.analyze(ast);
            System.out.print(AstPrinter.toTreeString(ast));
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Cannot read file: " + p + " (" + e.getMessage() + ")");
            System.exit(1);
        }
    }

    private static void runCodeGeneration(String sourceFile, String targetPath) {
        Path srcPath = Path.of(sourceFile).toAbsolutePath().normalize();

        // Determine output directory and class name
        Path outDir;
        String mainClassName = DEFAULT_CLASS_NAME;

        if (targetPath != null) {
            Path target = Path.of(targetPath).toAbsolutePath().normalize();
            String fileName = target.getFileName().toString();
            if (fileName.endsWith(".class")) {
                mainClassName = fileName.substring(0, fileName.length() - 6);
            } else {
                mainClassName = fileName;
            }
            outDir = target.getParent();
            if (outDir == null) {
                outDir = Path.of(".");
            }
        } else {
            outDir = Path.of(DEFAULT_OUTPUT_DIR);
        }

        // Ensure output directory exists
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            System.err.println("Cannot create output directory: " + outDir + " (" + e.getMessage() + ")");
            System.exit(1);
        }

        try (BufferedReader br = Files.newBufferedReader(srcPath)) {
            Lexer lexer = new Lexer(br);
            Parser parser = new Parser(lexer);
            AstNode ast = parser.getAST();

            // Run semantic analysis
            SemanticAnalyzer.analyze(ast);

            // Generate code
            CodeGenerator gen = new CodeGenerator(mainClassName, outDir);
            gen.generate(ast);
            gen.writeClassFiles();

            System.out.println("Compilation successful.");
            System.out.println("Generated " + mainClassName + ".class in " + outDir);
        } catch (CodeGenException e) {
            System.err.println("CodeGen Error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Cannot read file: " + srcPath + " (" + e.getMessage() + ")");
            System.exit(1);
        }
    }
}

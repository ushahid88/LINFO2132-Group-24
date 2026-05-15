package compiler;

import compiler.codegen.CodeGenerator;
import compiler.parser.AstNode;
import compiler.semantic.SemanticAnalyzer;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TestCodeGeneration {

    @Test
    public void voidFunctionCanBeCalledFromMain() {
        String source =
                "def log() { print(\"ok\"); }\n" +
                        "def main() { log(); }\n";
        CompilerTestHelper.assertProgramOutput("voidFunction", source, "ok");
    }

    @Test
    public void sameVariableNameInDifferentFunctions() {
        String source =
                "def helper() { INT value = 1; printINT(value); }\n" +
                        "def main() { INT value = 2; helper(); printINT(value); }\n";
        CompilerTestHelper.assertProgramOutput("separateScopes", source, "12");
    }

    @Test
    public void writesMainAndCollectionClassesToOutputDirectory() throws Exception {
        Path dir = Files.createTempDirectory("codegen-out-");
        try {
            String source =
                    "coll Box { INT v; }\n" +
                            "def main() { Box b = Box(7); printINT(b.v); }\n";

            AstNode ast = CompilerTestHelper.parse(source);
            SemanticAnalyzer.analyze(ast);
            CodeGenerator gen = new CodeGenerator("MainTest", dir);
            gen.generate(ast);
            gen.writeClassFiles();

            assertTrue(Files.exists(dir.resolve("MainTest.class")));
            assertTrue(Files.exists(dir.resolve("Box.class")));
        } finally {
            CompilerTestHelper.deleteTree(dir);
        }
    }
}

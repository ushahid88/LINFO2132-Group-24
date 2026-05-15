package compiler;

import compiler.Lexer.Lexer;
import compiler.parser.AstNode;
import compiler.parser.Parser;
import compiler.parser.ParserException;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestParser {

    @Test
    public void parsesSimpleProgram() {
        AstNode result = CompilerTestHelper.parse("def main() { INT x = 5; }");
        assertNotNull(result);
        assertTrue(result.label().startsWith("Program"));
    }

    @Test
    public void parsesMultipleStatements() {
        String input =
                "def main() {\n" +
                        "  INT x = 10;\n" +
                        "  y = 20;\n" +
                        "}";
        assertNotNull(CompilerTestHelper.parse(input));
    }

    @Test
    public void parsesForLoop() {
        String input =
                "def main() {\n" +
                        "  for (INT i; 1 -> 10; i+1) {\n" +
                        "  }\n" +
                        "}";
        assertNotNull(CompilerTestHelper.parse(input));
    }

    @Test
    public void parsesCollectionAndArrayDecl() {
        String input =
                "coll Point { INT x; INT y; }\n" +
                        "def main() {\n" +
                        "  INT[] arr = INT ARRAY [3];\n" +
                        "  Point p = Point(1, 2);\n" +
                        "}";
        assertNotNull(CompilerTestHelper.parse(input));
    }

    @Test
    public void parsesVoidFunction() {
        String input = "def greet() { println(\"hi\"); } def main() { greet(); }";
        assertNotNull(CompilerTestHelper.parse(input));
    }

    @Test(expected = ParserException.class)
    public void failsOnMissingSemicolon() {
        new Parser(new Lexer(new StringReader("def main() { INT x = 5 }"))).getAST();
    }

    @Test(expected = ParserException.class)
    public void failsOnInvalidSyntax() {
        new Parser(new Lexer(new StringReader("def main( { }"))).getAST();
    }

    @Test
    public void parsesNestedBlocks() {
        String input =
                "def main() {\n" +
                        "  {\n" +
                        "    INT x = 1;\n" +
                        "  }\n" +
                        "}";
        assertNotNull(CompilerTestHelper.parse(input));
    }
}

import compiler.Lexer.Lexer;
import compiler.parser.AstNode;
import compiler.parser.Parser;
import compiler.parser.ParserException;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertNotNull;

public class TestParser {

    @Test
    public void parsesSimpleProgram() {
        String input = "def main() { INT x = 5; }";

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AstNode result = parser.getAST();

        assertNotNull(result);
    }

    @Test
    public void parsesMultipleStatements() {
        String input =
                "def main() {\n" +
                        "  INT x = 10;\n" +
                        "  y = 20;\n" +
                        "}";

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AstNode result = parser.getAST();

        assertNotNull(result);
    }

    @Test
    public void parsesForLoop() {
        String input =
                "def main() {\n" +
                        "  for (INT i; 1 -> 10; i+1) {\n" +
                        "  }\n" +
                        "}";

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AstNode result = parser.getAST();

        assertNotNull(result);
    }

    @Test
    public void parsesPrintStatement() {
        String input =
                "def main() {\n" +
                        "  println(\"hello\");\n" +
                        "}";

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AstNode result = parser.getAST();

        assertNotNull(result);
    }

    @Test(expected = ParserException.class)
    public void failsOnMissingSemicolon() {
        String input = "def main() { INT x = 5 }"; // missing ;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        parser.getAST();
    }

    @Test(expected = ParserException.class)
    public void failsOnInvalidSyntax() {
        String input = "def main( { }"; // broken syntax

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        parser.getAST();
    }

    @Test
    public void parsesNestedBlocks() {
        String input =
                "def main() {\n" +
                        "  {\n" +
                        "    INT x = 1;\n" +
                        "  }\n" +
                        "}";

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AstNode result = parser.getAST();

        assertNotNull(result);
    }
}
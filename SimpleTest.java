import compiler.Lexer.Lexer;
import compiler.parser.AstNode;
import compiler.parser.Parser;
import compiler.semantic.SemanticAnalyzer;
import compiler.semantic.SemanticException;

import java.io.StringReader;

public class SimpleTest {
    private static AstNode parse(String input) {
        Parser parser = new Parser(new Lexer(new StringReader(input)));
        return parser.getAST();
    }

    public static void main(String[] args) {
        String input = "def main() { FLOAT x = 5; }";
        AstNode ast = parse(input);
        try {
            SemanticAnalyzer.analyze(ast);
            System.out.println("No error thrown");
        } catch (SemanticException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
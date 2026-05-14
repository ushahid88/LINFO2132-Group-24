import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

import java.io.StringReader;
import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import static org.junit.Assert.assertEquals;

public class TestLexer {
    
    private Lexer createLexer(String input) {
        return new Lexer(new StringReader(input));
    }
    
    private void assertNextToken(Lexer lexer, Symbol.Kind expectedType) {
        Symbol token = lexer.getNextSymbol();
        assertNotNull(token);
        if (!expectedType.equals(token.getKind())) {
            System.out.println("Expected " + expectedType + " but got " + token.getKind() + " lexeme " + token.getLexeme());
        }
        assertEquals(expectedType, token.getKind());
    }
    
    private void assertEOF(Lexer lexer) {
        assertNextToken(lexer, Symbol.Kind.EOF);
    }

    @Test
    public void test() {
        String input = "var x int = 2;";
        StringReader reader = new StringReader(input);
        Lexer lexer = new Lexer(reader);
        assertNotNull(lexer.getNextSymbol());
    }

    @Test
    public void lexesBasicProgramPieces() {
        String input =
                "def main() {\n" +
                        "  # comment\n" +
                        "  final x = 00342;\n" +
                        "  y = .234;\n" +
                        "  println(\"hi\\n\\\\\\\"\");\n" +
                        "}\n";

        Lexer lexer = new Lexer(new StringReader(input));

        assertEquals(new Symbol(Symbol.Kind.KW_DEF), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "main"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.LPAREN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.RPAREN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.LBRACE), lexer.getNextSymbol());

        assertEquals(new Symbol(Symbol.Kind.KW_FINAL), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "x"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.ASSIGN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.INT_LITERAL, "342"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.SEMICOLON), lexer.getNextSymbol());

        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "y"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.ASSIGN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.FLOAT_LITERAL, "0.234"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.SEMICOLON), lexer.getNextSymbol());
    }

    @Test(expected = Lexer.LexerException.class)
    public void throwsOnUnknownToken() {
        Lexer lexer = new Lexer(new StringReader("@"));
        lexer.getNextSymbol();
    }

    @Test
    public void lexesBaseTypesAsTypeTokens() {
        Lexer lexer = new Lexer(new StringReader("final INT i = 3;"));
        assertEquals(new Symbol(Symbol.Kind.KW_FINAL), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.TYPE_INT), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "i"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.ASSIGN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.INT_LITERAL, "3"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.SEMICOLON), lexer.getNextSymbol());
    }

    @Test
    public void lexesArrowInForRange() {
        Lexer lexer = new Lexer(new StringReader("for (INT i; 1 -> 100; i+1) {}"));

        assertEquals(new Symbol(Symbol.Kind.KW_FOR), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.LPAREN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.TYPE_INT), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "i"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.SEMICOLON), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.INT_LITERAL, "1"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.ARROW), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.INT_LITERAL, "100"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.SEMICOLON), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.IDENTIFIER, "i"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.PLUS), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.INT_LITERAL, "1"), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.RPAREN), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.LBRACE), lexer.getNextSymbol());
        assertEquals(new Symbol(Symbol.Kind.RBRACE), lexer.getNextSymbol());
    }

}


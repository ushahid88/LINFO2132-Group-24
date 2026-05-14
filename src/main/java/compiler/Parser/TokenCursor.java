package compiler.parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

public final class TokenCursor {

    private final Lexer lexer;
    private Symbol lookahead;

    public TokenCursor(Lexer lexer) {
        this.lexer = lexer;
        this.lookahead = lexer.getNextSymbol();
    }

    public Symbol peek() {
        return lookahead;
    }

    public Symbol.Kind kind() {
        return lookahead.getKind();
    }

    public boolean match(Symbol.Kind k) {
        if (kind() == k) {
            advance();
            return true;
        }
        return false;
    }

    public Symbol consume(Symbol.Kind expected) {
        if (kind() != expected) {
            throw new ParserException("ParseError: Expected " + expected + " but got " + lookahead);
        }
        Symbol cur = lookahead;
        advance();
        return cur;
    }

    public void expect(Symbol.Kind expected) {
        if (kind() != expected) {
            throw new ParserException("ParseError: Expected " + expected + " but got " + lookahead);
        }
    }

    private void advance() {
        lookahead = lexer.getNextSymbol();
    }
}
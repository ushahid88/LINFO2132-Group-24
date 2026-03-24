package compiler.Lexer;

import java.util.Objects;

public final class Symbol {

    public enum Kind {
        EOF,

        // Names
        IDENTIFIER,
        COLLECTION_NAME,

        // Keywords
        KW_FINAL, KW_COLL, KW_DEF, KW_FOR, KW_WHILE, KW_IF, KW_ELSE, KW_RETURN, KW_NOT, KW_ARRAY,

        // Types (IMPORTANT for parser)
        TYPE_INT, TYPE_FLOAT, TYPE_BOOL, TYPE_STRING,

        // Literals
        INT_LITERAL,
        FLOAT_LITERAL,
        STRING_LITERAL,
        BOOL_LITERAL,

        // Operators / punctuation
        ASSIGN,
        PLUS, MINUS, STAR, SLASH, MOD,
        EQ, NEQ, LT, GT, LE, GE,
        AND, OR,

        ARROW,     // ->
        COLON,     // :

        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        DOT,
        SEMICOLON,
        COMMA
    }

    private final Kind kind;
    private final String lexeme;

    public Symbol(Kind kind) {
        this(kind, null);
    }

    public Symbol(Kind kind, String lexeme) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lexeme = lexeme;
    }

    public Kind getKind() {
        return kind;
    }

    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String toString() {
        if (lexeme == null) return "<" + kind + ">";
        return "<" + kind + ", " + lexeme + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Symbol)) return false;
        Symbol other = (Symbol) o;
        return kind == other.kind && Objects.equals(lexeme, other.lexeme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, lexeme);
    }
}
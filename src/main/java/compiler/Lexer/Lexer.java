package compiler.Lexer;

import java.io.IOException;
import java.io.Reader;

import static compiler.Lexer.Symbol.Kind;

public class Lexer {

    private final Reader input;
    private int current = -2;

    public Lexer(Reader input) {
        this.input = input;
    }

    public Symbol getNextSymbol() {
        try {
            skipWhitespaceAndComments();

            int ch = peek();
            if (ch == -1) {
                return new Symbol(Kind.EOF);
            }

            if (ch == '"') {
                return lexString();
            }

            if (isDigit(ch) || ch == '.') {
                Symbol num = tryLexNumber();
                if (num != null) return num;
            }
            if (isIdentStart(ch)) {
                return lexIdentifierOrKeyword();
            }
            return lexOperatorOrPunctuation();

        } catch (LexerException e) {
            throw e; // already descriptive
        } catch (IOException e) {
            throw new LexerException("Lexer IO error: " + e.getMessage(), e);
        }
    }

    private void skipWhitespaceAndComments() throws IOException {
        while (true) {
            int ch = peek();
            while (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                read();
                ch = peek();
            }
            if (ch == '#') {
                while (ch != -1 && ch != '\n') {
                    read();
                    ch = peek();
                }
                continue;
            }
            return;
        }
    }

    private Symbol lexString() throws IOException {
        expect('"');

        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = read();
            if (ch == -1) {
                throw new LexerException("Unterminated string literal");
            }
            if (ch == '"') break; // closing quote

            if (ch == '\\') {
                int esc = read();
                if (esc == -1) throw new LexerException("Unterminated string escape");
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    default:
                        throw new LexerException("Unknown escape sequence: \\" + (char) esc);
                }
            } else {
                sb.append((char) ch);
            }
        }
        return new Symbol(Kind.STRING_LITERAL, sb.toString());
    }

    private Symbol tryLexNumber() throws IOException {
        int ch = peek();
        if (ch == '.') {
            read();
            int next = peek();
            if (!isDigit(next)) {
                current = '.';
                return null;
            }
            String frac = readDigits();
            String lexeme = "0." + frac;
            return new Symbol(Kind.FLOAT_LITERAL, normalizeFloatLexeme(lexeme));
        }

        String intPart = readDigits();
        boolean isFloat = false;
        if (peek() == '.') {
            read();
            isFloat = true;
            String fracPart = readDigits();
            if (fracPart.isEmpty()) {
                throw new LexerException("Malformed float literal: missing digits after '.'");
            }
            String lexeme = normalizeIntLexeme(intPart) + "." + fracPart;
            return new Symbol(Kind.FLOAT_LITERAL, normalizeFloatLexeme(lexeme));
        }

        return new Symbol(Kind.INT_LITERAL, normalizeIntLexeme(intPart));
    }

    private Symbol lexIdentifierOrKeyword() throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch = peek();
        sb.append((char) read());

        while (true) {
            int p = peek();
            if (p == -1) break;
            if (isLetter(p) || isDigit(p) || p == '_') {
                sb.append((char) read());
            } else {
                break;
            }
        }

        String word = sb.toString();

        switch (word) {
            case "final":  return new Symbol(Kind.KW_FINAL);
            case "coll":   return new Symbol(Kind.KW_COLL);
            case "def":    return new Symbol(Kind.KW_DEF);
            case "for":    return new Symbol(Kind.KW_FOR);
            case "while":  return new Symbol(Kind.KW_WHILE);
            case "if":     return new Symbol(Kind.KW_IF);
            case "else":   return new Symbol(Kind.KW_ELSE);
            case "return": return new Symbol(Kind.KW_RETURN);
            case "not":    return new Symbol(Kind.KW_NOT);
            case "ARRAY":  return new Symbol(Kind.KW_ARRAY);
            case "true":
            case "false":
                return new Symbol(Kind.BOOL_LITERAL, word);
            default:

                char first = word.charAt(0);
                if (Character.isUpperCase(first)) {
                    return new Symbol(Kind.COLLECTION_NAME, word);
                }
                return new Symbol(Kind.IDENTIFIER, word);
        }
    }

    private Symbol lexOperatorOrPunctuation() throws IOException {
        int ch = peek();
        if (ch == '=') {
            read();
            if (peek() == '=') { read(); return new Symbol(Kind.EQ); }
            if (peek() == '/') {
                read();
                if (peek() == '=') { read(); return new Symbol(Kind.NEQ); }
                throw new LexerException("Unrecognized token: =/ (did you mean =/= ?)");
            }
            return new Symbol(Kind.ASSIGN);
        }

        if (ch == '<') {
            read();
            if (peek() == '=') { read(); return new Symbol(Kind.LE); }
            return new Symbol(Kind.LT);
        }

        if (ch == '>') {
            read();
            if (peek() == '=') { read(); return new Symbol(Kind.GE); }
            return new Symbol(Kind.GT);
        }

        if (ch == '&') {
            read();
            if (peek() == '&') { read(); return new Symbol(Kind.AND); }
            throw new LexerException("Unrecognized token: & (did you mean && ?)");
        }

        if (ch == '|') {
            read();
            if (peek() == '|') { read(); return new Symbol(Kind.OR); }
            throw new LexerException("Unrecognized token: | (did you mean || ?)");
        }

        switch (ch) {
            case '+': read(); return new Symbol(Kind.PLUS);
            case '-': read(); return new Symbol(Kind.MINUS);
            case '*': read(); return new Symbol(Kind.STAR);
            case '/': read(); return new Symbol(Kind.SLASH);
            case '%': read(); return new Symbol(Kind.MOD);

            case '(': read(); return new Symbol(Kind.LPAREN);
            case ')': read(); return new Symbol(Kind.RPAREN);
            case '{': read(); return new Symbol(Kind.LBRACE);
            case '}': read(); return new Symbol(Kind.RBRACE);
            case '[': read(); return new Symbol(Kind.LBRACKET);
            case ']': read(); return new Symbol(Kind.RBRACKET);

            case '.': read(); return new Symbol(Kind.DOT);
            case ';': read(); return new Symbol(Kind.SEMICOLON);
            case ',': read(); return new Symbol(Kind.COMMA);

            default:
                throw new LexerException("Unrecognized token: '" + (char) ch + "'");
        }
    }

    private int peek() throws IOException {
        if (current != -2) return current;
        current = input.read();
        return current;
    }

    private int read() throws IOException {
        int ch = peek();
        current = -2;
        return ch;
    }

    private void expect(char c) throws IOException {
        int ch = read();
        if (ch != c) throw new LexerException("Expected '" + c + "' but got " + printable(ch));
    }

    private static String printable(int ch) {
        if (ch == -1) return "<EOF>";
        if (ch == '\n') return "\\n";
        if (ch == '\t') return "\\t";
        if (ch == '\r') return "\\r";
        return "'" + (char) ch + "'";
    }

    private static boolean isDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isLetter(int ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    private static boolean isIdentStart(int ch) {
        return (ch >= 'a' && ch <= 'z') || (ch == '_') || (ch >= 'A' && ch <= 'Z');
    }

    private String readDigits() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (isDigit(peek())) {
            sb.append((char) read());
        }
        return sb.toString();
    }

    private static String normalizeIntLexeme(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') i++;
        return digits.substring(i);
    }

    private static String normalizeFloatLexeme(String s) {
        return s;
    }

    public static class LexerException extends RuntimeException {
        public LexerException(String message) { super(message); }
        public LexerException(String message, Throwable cause) { super(message, cause); }
    }
}
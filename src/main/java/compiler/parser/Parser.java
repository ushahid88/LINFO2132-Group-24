package compiler.parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

import static compiler.Lexer.Symbol.Kind.*;

public class Parser {

    private final Lexer lexer;
    private Symbol lookahead;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        this.lookahead = lexer.getNextSymbol();
    }

    public AstNode getAST() {
        AstNode root = parseProgram();
        expect(EOF);
        return root;
    }

    // ---------------- Program / Decls ----------------

    private AstNode parseProgram() {
        Node program = new Node("Program");
        while (lookahead.getKind() != EOF) {
            program.children().add(parseTopLevelDecl());
        }
        return program;
    }

    private AstNode parseTopLevelDecl() {
        if (lookahead.getKind() == KW_FINAL) {
            return parseConstDecl();
        }
        if (lookahead.getKind() == KW_DEF) {
            return parseFunctionDecl();
        }
        if (startsType(lookahead.getKind())) {
            return parseVarDecl();
        }
        throw error("Expected top-level declaration (final/def/type), got " + lookahead);
    }

    private AstNode parseConstDecl() {
        consume(KW_FINAL);
        AstNode type = parseType();
        AstNode id = parseIdentifier();
        consume(ASSIGN);
        AstNode expr = parseExpr();
        consume(SEMICOLON);

        return new Node("ConstDecl",
                type,
                id,
                Node.leaf("AssignmentOperator"),
                expr
        );
    }

    private AstNode parseVarDecl() {
        AstNode type = parseType();
        AstNode id = parseIdentifier();

        if (lookahead.getKind() == ASSIGN) {
            consume(ASSIGN);
            AstNode expr = parseExpr();
            consume(SEMICOLON);
            return new Node("VarDecl",
                    type,
                    id,
                    Node.leaf("AssignmentOperator"),
                    expr
            );
        }

        consume(SEMICOLON);
        return new Node("VarDecl", type, id);
    }

    private AstNode parseFunctionDecl() {
        consume(KW_DEF);

        // return type is optional: def main() { ... }
        AstNode returnType;
        if (startsType(lookahead.getKind())) {
            returnType = parseType();
        } else {
            returnType = Node.leaf("Type, VOID");
        }

        AstNode name = parseIdentifier();

        consume(LPAREN);
        AstNode params = parseParams();
        consume(RPAREN);

        AstNode body = parseBlock();

        return new Node("FunctionDecl", returnType, name, params, body);
    }

    private AstNode parseParams() {
        Node params = new Node("Params");

        if (lookahead.getKind() == RPAREN) {
            return params; // empty
        }

        params.children().add(parseParam());
        while (lookahead.getKind() == COMMA) {
            consume(COMMA);
            params.children().add(parseParam());
        }
        return params;
    }

    private AstNode parseParam() {
        AstNode type = parseType();
        AstNode id = parseIdentifier();
        return new Node("Param", type, id);
    }

    private AstNode parseBlock() {
        consume(LBRACE);
        Node block = new Node("Block");
        while (lookahead.getKind() != RBRACE) {
            block.children().add(parseStatement());
        }
        consume(RBRACE);
        return block;
    }

    // ---------------- Types ----------------

    private AstNode parseType() {
        String base;
        switch (lookahead.getKind()) {
            case TYPE_INT: base = "INT"; consume(TYPE_INT); break;
            case TYPE_FLOAT: base = "FLOAT"; consume(TYPE_FLOAT); break;
            case TYPE_BOOL: base = "BOOL"; consume(TYPE_BOOL); break;
            case TYPE_STRING: base = "STRING"; consume(TYPE_STRING); break;
            case COLLECTION_NAME:
                base = lookahead.getLexeme();
                consume(COLLECTION_NAME);
                break;
            default:
                throw error("Expected a type, got " + lookahead);
        }

        int dims = 0;
        while (lookahead.getKind() == LBRACKET) {
            consume(LBRACKET);
            consume(RBRACKET);
            dims++;
        }

        StringBuilder t = new StringBuilder(base);
        for (int i = 0; i < dims; i++) t.append("[]");
        return Node.leaf("Type, " + t);
    }

    private static boolean startsType(Symbol.Kind k) {
        return k == TYPE_INT || k == TYPE_FLOAT || k == TYPE_BOOL || k == TYPE_STRING || k == COLLECTION_NAME;
    }

    // ---------------- Statements ----------------

    private AstNode parseStatement() {
        // local var decl
        if (startsType(lookahead.getKind())) {
            return parseVarDecl();
        }

        // return [expr] ;
        if (lookahead.getKind() == KW_RETURN) {
            consume(KW_RETURN);
            Node ret = new Node("Return");
            if (lookahead.getKind() != SEMICOLON) {
                ret.children().add(parseExpr());
            }
            consume(SEMICOLON);
            return ret;
        }

        // assignment or expression statement
        AstNode left = parseExpr();

        if (lookahead.getKind() == ASSIGN) {
            consume(ASSIGN);
            AstNode right = parseExpr();
            consume(SEMICOLON);
            return new Node("AssignStmt", left, Node.leaf("AssignmentOperator"), right);
        }

        consume(SEMICOLON);
        return new Node("ExprStmt", left);
    }

    // ---------------- Expressions (precedence + associativity) ----------------

    private AstNode parseExpr() {
        return new Node("Expr", parseOr());
    }

    private AstNode parseOr() {
        AstNode left = parseAnd();
        while (lookahead.getKind() == OR) {
            consume(OR);
            AstNode right = parseAnd();
            left = new Node("Expr", left, Node.leaf("LogicalOperator, ||"), right);
        }
        return left;
    }

    private AstNode parseAnd() {
        AstNode left = parseCmp();
        while (lookahead.getKind() == AND) {
            consume(AND);
            AstNode right = parseCmp();
            left = new Node("Expr", left, Node.leaf("LogicalOperator, &&"), right);
        }
        return left;
    }

    private AstNode parseCmp() {
        AstNode left = parseAdd();
        while (lookahead.getKind() == EQ || lookahead.getKind() == NEQ ||
                lookahead.getKind() == LT || lookahead.getKind() == LE ||
                lookahead.getKind() == GT || lookahead.getKind() == GE) {

            Symbol.Kind op = lookahead.getKind();
            consume(op);
            AstNode right = parseAdd();
            left = new Node("Expr", left, Node.leaf("ComparisonOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseAdd() {
        AstNode left = parseMul();
        while (lookahead.getKind() == PLUS || lookahead.getKind() == MINUS) {
            Symbol.Kind op = lookahead.getKind();
            consume(op);
            AstNode right = parseMul();
            left = new Node("Expr", left, Node.leaf("ArithmeticOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseMul() {
        AstNode left = parseUnary();
        while (lookahead.getKind() == STAR || lookahead.getKind() == SLASH || lookahead.getKind() == MOD) {
            Symbol.Kind op = lookahead.getKind();
            consume(op);
            AstNode right = parseUnary();
            left = new Node("Expr", left, Node.leaf("ArithmeticOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseUnary() {
        if (lookahead.getKind() == KW_NOT) {
            consume(KW_NOT);
            return new Node("Expr", Node.leaf("UnaryOperator, not"), parseUnary());
        }
        if (lookahead.getKind() == MINUS) {
            consume(MINUS);
            return new Node("Expr", Node.leaf("UnaryOperator, -"), parseUnary());
        }
        return parsePostfix();
    }

    // Postfix chaining: call / index / field access
    private AstNode parsePostfix() {
        AstNode base = parseAtom();

        while (true) {
            if (lookahead.getKind() == LPAREN) {
                consume(LPAREN);
                Node args = parseArgs();
                consume(RPAREN);
                base = new Node("Call", base, args);
                continue;
            }

            if (lookahead.getKind() == LBRACKET) {
                consume(LBRACKET);
                AstNode idx = parseExpr();
                consume(RBRACKET);
                base = new Node("Index", base, idx);
                continue;
            }

            if (lookahead.getKind() == DOT) {
                consume(DOT);
                AstNode field = parseIdentifier();
                base = new Node("FieldAccess", base, field);
                continue;
            }

            break;
        }

        return base;
    }

    private Node parseArgs() {
        Node args = new Node("Args");
        if (lookahead.getKind() == RPAREN) {
            return args;
        }
        args.children().add(parseExpr());
        while (lookahead.getKind() == COMMA) {
            consume(COMMA);
            args.children().add(parseExpr());
        }
        return args;
    }

    // Primary atoms (no postfix here)
    private AstNode parseAtom() {
        switch (lookahead.getKind()) {
            case INT_LITERAL: {
                String v = lookahead.getLexeme();
                consume(INT_LITERAL);
                return Node.leaf("Integer, " + v);
            }
            case FLOAT_LITERAL: {
                String v = lookahead.getLexeme();
                consume(FLOAT_LITERAL);
                return Node.leaf("Float, " + v);
            }
            case STRING_LITERAL: {
                String v = lookahead.getLexeme();
                consume(STRING_LITERAL);
                return Node.leaf("String, " + v);
            }
            case BOOL_LITERAL: {
                String v = lookahead.getLexeme();
                consume(BOOL_LITERAL);
                return Node.leaf("Boolean, " + v);
            }
            case IDENTIFIER: {
                String name = lookahead.getLexeme();
                consume(IDENTIFIER);
                return Node.leaf("Identifier, " + name);
            }
            case LPAREN: {
                consume(LPAREN);
                AstNode inside = parseExpr();
                consume(RPAREN);
                return inside;
            }
            default:
                throw error("Expected expression, got " + lookahead);
        }
    }

    private static String opLexeme(Symbol.Kind k) {
        switch (k) {
            case PLUS: return "+";
            case MINUS: return "-";
            case STAR: return "*";
            case SLASH: return "/";
            case MOD: return "%";
            case EQ: return "==";
            case NEQ: return "=/=";
            case LT: return "<";
            case LE: return "<=";
            case GT: return ">";
            case GE: return ">=";
            default: return k.name();
        }
    }

    // ---------------- Lexer interaction helpers ----------------

    private void consume(Symbol.Kind expected) {
        expect(expected);
        lookahead = lexer.getNextSymbol();
    }

    private void expect(Symbol.Kind expected) {
        if (lookahead.getKind() != expected) {
            throw error("Expected " + expected + " but got " + lookahead);
        }
    }

    private ParserException error(String msg) {
        return new ParserException("ParseError: " + msg);
    }

    public static class ParserException extends RuntimeException {
        public ParserException(String message) { super(message); }
    }

    private AstNode parseIdentifier() {
        if (lookahead.getKind() != IDENTIFIER) {
            throw error("Expected identifier, got " + lookahead);
        }
        String name = lookahead.getLexeme();
        consume(IDENTIFIER);
        return Node.leaf("Identifier, " + name);
    }
}
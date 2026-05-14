package compiler.parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

import static compiler.Lexer.Symbol.Kind.*;

public class Parser {

    private final TokenCursor t;

    public Parser(Lexer lexer) {
        this.t = new TokenCursor(lexer);
    }
    public Object parse() {
        return parseProgram();
    }

    public AstNode getAST() {
        AstNode root = parseProgram();
        t.consume(EOF);
        return root;
    }

    private AstNode parseProgram() {
        Node program = new Node("Program");
        while (t.kind() != EOF) {
            program.children().add(parseTopLevelDecl());
        }
        return program;
    }

    private AstNode parseTopLevelDecl() {
        if (t.kind() == KW_FINAL) return parseConstDecl();
        if (t.kind() == KW_COLL)  return parseCollDecl();
        if (t.kind() == KW_DEF)   return parseFunctionDecl();
        if (startsType(t.kind())) return parseVarDecl();
        throw err("Expected top-level declaration (final/coll/def/type), got " + t.peek());
    }

    private AstNode parseConstDecl() {
        t.consume(KW_FINAL);
        AstNode type = parseType();
        AstNode id = parseIdentifier();
        t.consume(ASSIGN);
        AstNode expr = parseExpr();
        t.consume(SEMICOLON);

        return new Node("ConstDecl", type, id, Node.leaf("AssignmentOperator"), expr);
    }

    private AstNode parseVarDecl() {
        AstNode type = parseType();
        AstNode id = parseIdentifier();

        if (t.kind() == ASSIGN) {
            t.consume(ASSIGN);
            AstNode expr = parseExpr();
            t.consume(SEMICOLON);
            return new Node("VarDecl", type, id, Node.leaf("AssignmentOperator"), expr);
        }

        t.consume(SEMICOLON);
        return new Node("VarDecl", type, id);
    }

    private AstNode parseCollDecl() {
        t.consume(KW_COLL);

        if (t.kind() != COLLECTION_NAME && t.kind() != IDENTIFIER
                && t.kind() != TYPE_INT && t.kind() != TYPE_FLOAT
                && t.kind() != TYPE_BOOL && t.kind() != TYPE_STRING) {
            throw err("Expected collection name after 'coll', got " + t.peek());
        }

        String name = getSymbolName(t.peek());
        t.consume(t.kind());

        t.consume(LBRACE);
        Node fields = new Node("Fields");
        while (t.kind() != RBRACE) {
            fields.children().add(parseFieldDecl());
        }
        t.consume(RBRACE);

        return new Node("CollDecl", Node.leaf("CollectionName, " + name), fields);
    }

    private static String getSymbolName(Symbol symbol) {
        if (symbol.getLexeme() != null) {
            return symbol.getLexeme();
        }
        switch (symbol.getKind()) {
            case TYPE_INT: return "INT";
            case TYPE_FLOAT: return "FLOAT";
            case TYPE_BOOL: return "BOOL";
            case TYPE_STRING: return "STRING";
            default:
                throw new IllegalArgumentException("Cannot extract name from symbol: " + symbol);
        }
    }

    private AstNode parseFieldDecl() {
        AstNode type = parseType();
        AstNode id = parseIdentifier();
        t.consume(SEMICOLON);
        return new Node("FieldDecl", type, id);
    }

    private AstNode parseFunctionDecl() {
        t.consume(KW_DEF);
        AstNode returnType = startsType(t.kind()) ? parseType() : Node.leaf("Type, VOID");

        AstNode name = parseIdentifier();

        t.consume(LPAREN);
        AstNode params = parseParams();
        t.consume(RPAREN);

        AstNode body = parseBlock();
        return new Node("FunctionDecl", returnType, name, params, body);
    }

    private AstNode parseParams() {
        Node params = new Node("Params");
        if (t.kind() == RPAREN) return params;

        params.children().add(parseParam());
        while (t.kind() == COMMA) {
            t.consume(COMMA);
            params.children().add(parseParam());
        }
        return params;
    }

    private AstNode parseParam() {
        AstNode type = parseType();
        AstNode id = parseIdentifier();
        return new Node("Param", type, id);
    }

    private AstNode parseType() {
        String base;
        switch (t.kind()) {
            case TYPE_INT:    base = "INT";    t.consume(TYPE_INT); break;
            case TYPE_FLOAT:  base = "FLOAT";  t.consume(TYPE_FLOAT); break;
            case TYPE_BOOL:   base = "BOOL";   t.consume(TYPE_BOOL); break;
            case TYPE_STRING: base = "STRING"; t.consume(TYPE_STRING); break;
            case COLLECTION_NAME:
                base = t.peek().getLexeme();
                t.consume(COLLECTION_NAME);
                break;
            default:
                throw err("Expected a type, got " + t.peek());
        }

        int dims = 0;
        while (t.kind() == LBRACKET) {
            t.consume(LBRACKET);
            t.consume(RBRACKET);
            dims++;
        }

        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < dims; i++) sb.append("[]");
        return Node.leaf("Type, " + sb);
    }

    private static boolean startsType(Symbol.Kind k) {
        return k == TYPE_INT || k == TYPE_FLOAT || k == TYPE_BOOL || k == TYPE_STRING || k == COLLECTION_NAME;
    }

    private AstNode parseIdentifier() {
        if (t.kind() != IDENTIFIER) {
            throw err("Expected identifier, got " + t.peek());
        }
        String name = t.peek().getLexeme();
        t.consume(IDENTIFIER);
        return Node.leaf("Identifier, " + name);
    }

    private AstNode parseBlock() {
        t.consume(LBRACE);
        Node block = new Node("Block");
        while (t.kind() != RBRACE) {
            block.children().add(parseStatement());
        }
        t.consume(RBRACE);
        return block;
    }

    private AstNode parseStatement() {
        if (t.kind() == LBRACE) return parseBlock();
        if (t.kind() == KW_IF) return parseIf();
        if (t.kind() == KW_WHILE) return parseWhile();
        if (t.kind() == KW_FOR) return parseFor();

        if (startsType(t.kind())) return parseVarDecl();

        if (t.kind() == KW_RETURN) {
            t.consume(KW_RETURN);
            Node ret = new Node("Return");
            if (t.kind() != SEMICOLON) {
                ret.children().add(parseExpr());
            }
            t.consume(SEMICOLON);
            return ret;
        }

        // assignment or expression statement
        AstNode left = parseExpr();
        if (t.kind() == ASSIGN) {
            t.consume(ASSIGN);
            AstNode right = parseExpr();
            t.consume(SEMICOLON);
            return new Node("AssignStmt", left, Node.leaf("AssignmentOperator"), right);
        }

        t.consume(SEMICOLON);
        return new Node("ExprStmt", left);
    }

    private AstNode parseIf() {
        t.consume(KW_IF);
        t.consume(LPAREN);
        AstNode cond = parseExpr();
        t.consume(RPAREN);
        AstNode thenBlock = parseBlock();

        if (t.kind() == KW_ELSE) {
            t.consume(KW_ELSE);
            AstNode elseBlock = parseBlock();
            return new Node("If", cond, thenBlock, elseBlock);
        }
        return new Node("If", cond, thenBlock);
    }

    private AstNode parseWhile() {
        t.consume(KW_WHILE);
        t.consume(LPAREN);
        AstNode cond = parseExpr();
        t.consume(RPAREN);
        AstNode body = parseBlock();
        return new Node("While", cond, body);
    }
    private AstNode parseFor() {
        t.consume(KW_FOR);
        t.consume(LPAREN);

        AstNode init;
        if (startsType(t.kind())) {
            AstNode type = parseType();
            AstNode var = parseIdentifier();
            init = new Node("InitDecl", type, var);
        } else {
            AstNode initExpr = parseExpr();
            init = new Node("InitExpr", initExpr);
        }
        t.consume(SEMICOLON);
        AstNode from = parseExpr();
        t.consume(ARROW);
        AstNode to = parseExpr();
        t.consume(SEMICOLON);
        AstNode step = parseExpr();
        t.consume(RPAREN);
        AstNode body = parseBlock();
        return new Node("For",
                init,
                new Node("Range", from, to),
                new Node("Step", step),
                body
        );
    }

    private AstNode parseExpr() {
        return new Node("Expr", parseOr());
    }

    private AstNode parseOr() {
        AstNode left = parseAnd();
        while (t.kind() == OR) {
            t.consume(OR);
            AstNode right = parseAnd();
            left = new Node("Expr", left, Node.leaf("LogicalOperator, ||"), right);
        }
        return left;
    }

    private AstNode parseAnd() {
        AstNode left = parseCmp();
        while (t.kind() == AND) {
            t.consume(AND);
            AstNode right = parseCmp();
            left = new Node("Expr", left, Node.leaf("LogicalOperator, &&"), right);
        }
        return left;
    }

    private AstNode parseCmp() {
        AstNode left = parseAdd();
        while (t.kind() == EQ || t.kind() == NEQ || t.kind() == LT || t.kind() == LE || t.kind() == GT || t.kind() == GE) {
            Symbol.Kind op = t.kind();
            t.consume(op);
            AstNode right = parseAdd();
            left = new Node("Expr", left, Node.leaf("ComparisonOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseAdd() {
        AstNode left = parseMul();
        while (t.kind() == PLUS || t.kind() == MINUS) {
            Symbol.Kind op = t.kind();
            t.consume(op);
            AstNode right = parseMul();
            left = new Node("Expr", left, Node.leaf("ArithmeticOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseMul() {
        AstNode left = parseUnary();
        while (t.kind() == STAR || t.kind() == SLASH || t.kind() == MOD) {
            Symbol.Kind op = t.kind();
            t.consume(op);
            AstNode right = parseUnary();
            left = new Node("Expr", left, Node.leaf("ArithmeticOperator, " + opLexeme(op)), right);
        }
        return left;
    }

    private AstNode parseUnary() {
        if (t.kind() == KW_NOT) {
            t.consume(KW_NOT);
            return new Node("Expr", Node.leaf("UnaryOperator, not"), parseUnary());
        }
        if (t.kind() == MINUS) {
            t.consume(MINUS);
            return new Node("Expr", Node.leaf("UnaryOperator, -"), parseUnary());
        }
        return parsePostfix();
    }

    private AstNode parsePostfix() {
        AstNode base = parseAtom();
        while (true) {
            if (t.kind() == LPAREN) {
                t.consume(LPAREN);
                Node args = parseArgs();
                t.consume(RPAREN);
                base = new Node("Call", base, args);
                continue;
            }
            if (t.kind() == LBRACKET) {
                t.consume(LBRACKET);
                AstNode idx = parseExpr();
                t.consume(RBRACKET);
                base = new Node("Index", base, idx);
                continue;
            }
            if (t.kind() == DOT) {
                t.consume(DOT);
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
        if (t.kind() == RPAREN) return args;

        args.children().add(parseExpr());
        while (t.kind() == COMMA) {
            t.consume(COMMA);
            args.children().add(parseExpr());
        }
        return args;
    }

    private AstNode parseAtom() {
        switch (t.kind()) {
            case INT_LITERAL: {
                String v = t.peek().getLexeme();
                t.consume(INT_LITERAL);
                return Node.leaf("Integer, " + v);
            }
            case FLOAT_LITERAL: {
                String v = t.peek().getLexeme();
                t.consume(FLOAT_LITERAL);
                return Node.leaf("Float, " + v);
            }
            case STRING_LITERAL: {
                String v = t.peek().getLexeme();
                t.consume(STRING_LITERAL);
                return Node.leaf("String, " + v);
            }
            case BOOL_LITERAL: {
                String v = t.peek().getLexeme();
                t.consume(BOOL_LITERAL);
                return Node.leaf("Boolean, " + v);
            }
            case IDENTIFIER: {
                String name = t.peek().getLexeme();
                t.consume(IDENTIFIER);
                return Node.leaf("Identifier, " + name);
            }
            case COLLECTION_NAME: {
                String n = t.peek().getLexeme();
                t.consume(COLLECTION_NAME);
                return Node.leaf("CollectionName, " + n);
            }
            case TYPE_INT:
            case TYPE_FLOAT:
            case TYPE_BOOL:
            case TYPE_STRING: {
                String base = (t.kind() == TYPE_INT) ? "INT"
                        : (t.kind() == TYPE_FLOAT) ? "FLOAT"
                        : (t.kind() == TYPE_BOOL) ? "BOOL"
                        : "STRING";
                t.consume(t.kind());

                if (t.kind() != KW_ARRAY) {
                    throw err("Expected ARRAY after base type in array constructor, got " + t.peek());
                }
                t.consume(KW_ARRAY);
                t.consume(LBRACKET);
                AstNode sizeExpr = parseExpr();
                t.consume(RBRACKET);

                return new Node("ArrayConstructor", Node.leaf("Type, " + base), sizeExpr);
            }
            case LPAREN: {
                t.consume(LPAREN);
                AstNode inside = parseExpr();
                t.consume(RPAREN);
                return inside;
            }
            default:
                throw err("Expected expression, got " + t.peek());
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

    private ParserException err(String msg) {
        return new ParserException("ParseError: " + msg);
    }
}
# Grammar Rules Report for Compiler Language

## Introduction

This report documents the grammar rules for the programming language implemented in our compiler project. The language is designed to support basic programming constructs including variable declarations, expressions with operator precedence, function calls, and array operations. The parser is implemented using a recursive-descent approach in Java, building an Abstract Syntax Tree (AST) from tokens produced by the lexer.

The grammar is defined using Extended Backus-Naur Form (EBNF) notation, where:
- `|` denotes alternatives
- `[]` denotes optional elements
- `{}` denotes zero or more repetitions
- `()` groups elements


## Grammar Rules

### Program
A program consists of a sequence of top-level declarations.

```
Program ::= { Declaration }
```

### Declarations
Declarations include variable declarations, constant declarations, and array declarations.

```
Declaration ::= Type Identifier "=" Expression ";"
              | "final" Type Identifier "=" Expression ";"
              | Type "[" "]" Identifier "=" Type "ARRAY" "[" Expression "]" ";"
```

- `Type` can be `INT`, `BOOL`, or `FLOAT`.
- Constants are declared with the `final` keyword.
- Arrays are declared with `Type[]` and initialized using `Type ARRAY [size]`.

### Expressions
Expressions support operator precedence, with logical operators having the highest precedence, followed by relational, additive, multiplicative, unary, and primary expressions.

```
Expression ::= LogicalOr

LogicalOr ::= LogicalAnd { "||" LogicalAnd }

LogicalAnd ::= Equality { "&&" Equality }

Equality ::= Relational { ("==" | "!=") Relational }

Relational ::= Additive { ("<" | ">" | "<=" | ">=") Additive }

Additive ::= Multiplicative { ("+" | "-") Multiplicative }

Multiplicative ::= Unary { ("*" | "/") Unary }

Unary ::= "-" Unary
        | "not" Unary
        | Primary

Primary ::= IntegerLiteral
          | FloatLiteral
          | BoolLiteral
          | StringLiteral
          | Identifier
          | "(" Expression ")"
          | Identifier "(" [ Expression { "," Expression } ] ")"  // Function call
          | Type "ARRAY" "[" Expression "]"  // Array creation
```

### Literals and Identifiers
- `IntegerLiteral`: Sequences of digits (e.g., `42`)
- `FloatLiteral`: Digits with a decimal point (e.g., `3.14`)
- `BoolLiteral`: `true` or `false`
- `StringLiteral`: Quoted strings (e.g., `"hello"`)
- `Identifier`: Alphanumeric sequences starting with a letter or underscore (e.g., `variableName`)

## Parser Implementation Notes

- **Precedence and Associativity**: The parser correctly handles operator precedence as defined. All binary operators are left-associative.
- **Error Handling**: The parser throws a `ParserException` for syntax errors, such as unexpected tokens or missing semicolons.
- **AST Construction**: The parser builds an AST with nodes for `Program`, `Declaration`, `ConstantDeclaration`, `BinaryExpression`, `UnaryExpression`, `FunctionCall`, `ArrayCreation`, and literals.
- **Limitations**:
  - No support for control structures (e.g., if, while, for).
  - No support for assignment statements beyond declarations.
  - Function definitions are not implemented; only function calls are supported.
  - No support for more complex types or user-defined types.
  - Expressions are parsed but may not handle all nested or complex cases perfectly (e.g., deep nesting or mixed operators).
  - The parser assumes well-formed input and may not recover from errors gracefully.

</content>

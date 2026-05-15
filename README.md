# LINFO2132 Group 24 — Language Compiler

GitHub: https://github.com/ushahid88/LINFO2132-Group-24/tree/dev

## Run

```bash
gradle run --args="-lexer code_example.txt"
gradle run --args="-parser code_example.txt"
gradle run --args="code_example.txt -o build/out/Program.class"
```

## Tests

```bash
gradle test
```

Tests live under `test/compiler/`:

| Class | Purpose |
|-------|---------|
| `TestLexer` | Tokenization |
| `TestParser` | Syntax / AST |
| `TestSemanticAnalysis` | Semantic errors (required keywords) |
| `ProjectStatementTestCases` | End-to-end course sample programs |
| `TestCodeGeneration` | Void functions, scopes, `.class` output |
| `TestExtraFeatures` | Built-ins (`str`, `floor`, `ceil`), collections, `final` |
| `TestResourcePrograms` | Programs loaded from `test/resources/programs/` |

Sample `.lang` fixtures: `test/resources/programs/`.

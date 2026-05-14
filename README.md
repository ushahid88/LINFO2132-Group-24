Github Link: https://github.com/ushahid88/LINFO2132-Group-24/tree/dev

## Build and run

```text
gradle build -x test
gradle run --args="tests/lang/hello.lang -o tests/lang/hello.class"
```

From the output directory, run the generated main class (see `tests/README.md`).

## Automated tests

```text
gradle test
```

JUnit coverage includes the eight course statement programs (`ProjectStatementTestCases`), extra builtins and short-circuit behaviour (`CompilerExtrasTest`), and the sample `.lang` files under `tests/lang/` (`CompilerFeatureLangFilesTest`).

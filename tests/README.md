# Sample programs (course workflow)

Build the compiler (skip JUnit if you use JDK versions Gradle does not support yet):

```text
gradle build -x test
```

Compile a program to **the same directory** as the main `.class` so record (collection) classes are loadable next to it:

```text
gradle run --args="tests/lang/hello.lang -o tests/lang/hello.class"
cd tests/lang
java -cp . hello
```

From the repository root, relative paths work as in the project statement:

```text
gradle run --args="tests/lang/extra_read.lang -o tests/lang/extra_read.class"
```

Then pipe stdin when using `read_*`:

```text
cd tests/lang
echo 7 | java -cp . extra_read
```

Automated checks for these samples (and the eight statement tests) live under Gradle’s `test/` tree: `CompileHarness`, `ProjectStatementTestCases`, and `CompilerExtrasTest`.

package compiler;

import compiler.parser.AstNode;
import compiler.semantic.SemanticAnalyzer;
import compiler.semantic.SemanticException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSemanticAnalysis {

    private void assertSemanticError(String input, String expectedKeyword) {
        AstNode ast = CompilerTestHelper.parse(input);
        try {
            SemanticAnalyzer.analyze(ast);
            fail("Expected semantic error containing " + expectedKeyword);
        } catch (SemanticException e) {
            assertTrue(e.getMessage().contains(expectedKeyword));
        }
    }

    @Test
    public void typeErrorOnIntToFloatAssignment() {
        assertSemanticError("def main() { FLOAT x = 5; }", "TypeError");
    }

    @Test
    public void typeErrorOnStringToIntAssignment() {
        assertSemanticError("def main() { INT x = \"hello\"; }", "TypeError");
    }

    @Test
    public void collectionErrorOnLowercaseName() {
        assertSemanticError("coll myCollection { INT x; }", "CollectionError");
    }

    @Test
    public void collectionErrorOnBuiltinTypeOverwrite() {
        assertSemanticError("coll INT { INT x; }", "CollectionError");
    }

    @Test
    public void collectionErrorOnDuplicateDefinition() {
        assertSemanticError("coll Point { INT x; } coll Point { INT y; }", "CollectionError");
    }

    @Test
    public void operatorErrorOnStringAddition() {
        assertSemanticError("def main() { STRING x = \"a\" + \"b\"; }", "OperatorError");
    }

    @Test
    public void operatorErrorOnStringIntComparison() {
        assertSemanticError("def main() { if (\"hello\" == 5) { } }", "OperatorError");
    }

    @Test
    public void operatorErrorOnLogicalInt() {
        assertSemanticError("def main() { if (5 && 10) { } }", "OperatorError");
    }

    @Test
    public void operatorErrorOnNotInt() {
        assertSemanticError("def main() { BOOL x = not 5; }", "OperatorError");
    }

    @Test
    public void argumentErrorOnWrongParameterType() {
        assertSemanticError("def foo(INT x) { } def main() { foo(\"wrong\"); }", "ArgumentError");
    }

    @Test
    public void argumentErrorOnWrongArgumentCount() {
        assertSemanticError("def foo(INT x, INT y) { } def main() { foo(5); }", "ArgumentError");
    }

    @Test
    public void missingConditionErrorOnIntIf() {
        assertSemanticError("def main() { if (5) { } }", "MissingConditionError");
    }

    @Test
    public void missingConditionErrorOnIntWhile() {
        assertSemanticError("def main() { while (\"loop\") { } }", "MissingConditionError");
    }

    @Test
    public void returnErrorOnTypeMismatch() {
        assertSemanticError("def INT getValue() { return \"not int\"; }", "ReturnError");
    }

    @Test
    public void returnErrorOnMissingReturn() {
        assertSemanticError("def INT getValue() { INT x = 5; }", "ReturnError");
    }

    @Test
    public void scopeErrorOnUndefinedVariable() {
        assertSemanticError("def main() { INT x = y; }", "ScopeError");
    }

    @Test
    public void scopeErrorOnOutOfScope() {
        assertSemanticError("def main() { { INT x = 5; } INT y = x; }", "ScopeError");
    }

    @Test
    public void scopeErrorOnUndefinedFunction() {
        assertSemanticError("def main() { foo(5); }", "ScopeError");
    }

    @Test
    public void validParameterShadowing() {
        SemanticAnalyzer.analyze(CompilerTestHelper.parse(
                "INT x = 5; def foo(INT x) { } def main() { foo(10); }"));
    }

    @Test
    public void validArrayTypeDeclaration() {
        SemanticAnalyzer.analyze(CompilerTestHelper.parse(
                "def main() { INT[] arr = INT ARRAY [1]; }"));
    }

    @Test
    public void validCollectionUsage() {
        SemanticAnalyzer.analyze(CompilerTestHelper.parse(
                "coll Point { INT x; INT y; } def main() { Point p = Point(1, 2); }"));
    }
}

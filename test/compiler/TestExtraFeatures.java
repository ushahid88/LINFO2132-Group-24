package compiler;

import org.junit.Test;

public class TestExtraFeatures {

    @Test
    public void builtinStrConvertsIntToString() {
        String source =
                "def main() {\n" +
                        "  print(str(65));\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("strBuiltin", source, "65");
    }

    @Test
    public void builtinFloorAndCeil() {
        String source =
                "def main() {\n" +
                        "  FLOAT x = 2.7;\n" +
                        "  printINT(floor(x));\n" +
                        "  printINT(ceil(x));\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("floorCeilBuiltin", source, "23");
    }

    @Test
    public void floatArithmeticWithLiterals() {
        String source =
                "def main() {\n" +
                        "  FLOAT x = 1.5;\n" +
                        "  printFLOAT(x + 2.0);\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("floatArithmetic", source, "3.5");
    }

    @Test
    public void finalConstantVisibleInMain() {
        String source =
                "final INT scale = 10;\n" +
                        "def main() {\n" +
                        "  printINT(scale);\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("finalConstant", source, "10");
    }

    @Test
    public void nestedCollectionFieldAccess() {
        String source =
                "coll Point { INT x; INT y; }\n" +
                        "coll Segment { Point start; Point end; }\n" +
                        "def main() {\n" +
                        "  Segment s = Segment(Point(1, 2), Point(3, 4));\n" +
                        "  printINT(s.start.x + s.end.y);\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("nestedCollection", source, "5");
    }

    @Test
    public void userDefinedFunctionWithReturnValue() {
        String source =
                "def INT double(INT v) { return v * 2; }\n" +
                        "def main() { printINT(double(21)); }\n";
        CompilerTestHelper.assertProgramOutput("userFunction", source, "42");
    }

    @Test
    public void comparisonOperatorsProduceBranching() {
        String source =
                "def main() {\n" +
                        "  if (3 < 5) { print(\"lt\"); }\n" +
                        "  if (10 =/= 10) { print(\"neq\"); } else { print(\"ok\"); }\n" +
                        "}\n";
        CompilerTestHelper.assertProgramOutput("comparisons", source, "ltok");
    }
}

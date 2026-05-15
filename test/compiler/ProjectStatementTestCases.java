package compiler;

import org.junit.Test;

public class ProjectStatementTestCases {

    private static final String TEST1 = ""
            + "def main() {\n"
            + "    printINT(1);\n"
            + "    println(\"\");\n"
            + "    printFLOAT(1.1);\n"
            + "    println(\"\");\n"
            + "    print(\"hello\");\n"
            + "}\n";

    private static final String EXPECTED1 = "1\n1.1\nhello";

    private static final String TEST2 = ""
            + "def main() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value);\n"
            + "}\n";

    private static final String TEST3 = ""
            + "def main() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value + 4);\n"
            + "}\n";

    private static final String TEST4 = ""
            + "def main() {\n"
            + "    INT a = 10;\n"
            + "    INT b = 5;\n"
            + "    print(\"Addition: \");\n"
            + "    printINT(a + b);\n"
            + "    println(\"\");\n"
            + "    print(\"Subtraction: \");\n"
            + "    printINT(a - b);\n"
            + "    println(\"\");\n"
            + "    print(\"Unary Minus: \");\n"
            + "    printINT(-a);\n"
            + "    println(\"\");\n"
            + "    print(\"Multiplication: \");\n"
            + "    printINT(a * b);\n"
            + "    println(\"\");\n"
            + "    print(\"Division: \");\n"
            + "    printINT(a / b);\n"
            + "    println(\"\");\n"
            + "    print(\"Modulus: \");\n"
            + "    printINT(a % b);\n"
            + "    println(\"\");\n"
            + "}\n";

    private static final String EXPECTED4 = ""
            + "Addition: 15\n"
            + "Subtraction: 5\n"
            + "Unary Minus: -10\n"
            + "Multiplication: 50\n"
            + "Division: 2\n"
            + "Modulus: 0\n";

    private static final String TEST5 = ""
            + "def main() {\n"
            + "    FLOAT a = 10.5;\n"
            + "    FLOAT b = 5.2;\n"
            + "    print(\"Addition: \");\n"
            + "    printFLOAT(a + b);\n"
            + "    println(\"\");\n"
            + "    print(\"Subtraction: \");\n"
            + "    printFLOAT(a - b);\n"
            + "    println(\"\");\n"
            + "    print(\"Unary Minus: \");\n"
            + "    printFLOAT(-a);\n"
            + "    println(\"\");\n"
            + "    print(\"Multiplication: \");\n"
            + "    printFLOAT(a * b);\n"
            + "    println(\"\");\n"
            + "    FLOAT c = 4.4;\n"
            + "    FLOAT d = 2.2;\n"
            + "    print(\"Division: \");\n"
            + "    printFLOAT(c / d);\n"
            + "    println(\"\");\n"
            + "}\n";

    private static final String EXPECTED5 = ""
            + "Addition: 15.7\n"
            + "Subtraction: 5.3\n"
            + "Unary Minus: -10.5\n"
            + "Multiplication: 54.6\n"
            + "Division: 2.0\n";

    private static final String TEST6 = ""
            + "def scopeTest() {\n"
            + "    INT value = 1;\n"
            + "    printINT(value);\n"
            + "}\n"
            + "def main() {\n"
            + "    INT value = 2;\n"
            + "    scopeTest();\n"
            + "    printINT(value);\n"
            + "}\n";

    private static final String TEST7 = ""
            + "coll Testing {\n"
            + "    INT x;\n"
            + "}\n"
            + "def main() {\n"
            + "    Testing test = Testing(3);\n"
            + "    printINT(test.x);\n"
            + "}\n";

    private static final String TEST8 = ""
            + "def main() {\n"
            + "    INT i = 0;\n"
            + "    INT result = 0;\n"
            + "    while(i < 5) {\n"
            + "        i = i + 1;\n"
            + "        result = result + i;\n"
            + "    }\n"
            + "    print(\"While Result: \");\n"
            + "    printINT(result);\n"
            + "    println(\"\");\n"
            + "    for(i; 1 -> 5; i + 1) {\n"
            + "        if(i > 3) {\n"
            + "            print(\"High \");\n"
            + "        } else {\n"
            + "            print(\"Low \");\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static final String EXPECTED8 = "While Result: 15\nLow Low Low High ";

    @Test
    public void test01_printing() {
        CompilerTestHelper.assertProgramOutput("test1", TEST1, EXPECTED1);
    }

    @Test
    public void test02_assignment() {
        CompilerTestHelper.assertProgramOutput("test2", TEST2, "1");
    }

    @Test
    public void test03_addition() {
        CompilerTestHelper.assertProgramOutput("test3", TEST3, "5");
    }

    @Test
    public void test04_integerOperations() {
        CompilerTestHelper.assertProgramOutput("test4", TEST4, EXPECTED4);
    }

    @Test
    public void test05_floatOperations() {
        CompilerTestHelper.assertProgramOutput("test5", TEST5, EXPECTED5);
    }

    @Test
    public void test06_scope() {
        CompilerTestHelper.assertProgramOutput("test6", TEST6, "12");
    }

    @Test
    public void test07_collections() {
        CompilerTestHelper.assertProgramOutput("test7", TEST7, "3");
    }

    @Test
    public void test08_loopsAndConditions() {
        CompilerTestHelper.assertProgramOutput("test8", TEST8, EXPECTED8);
    }
}

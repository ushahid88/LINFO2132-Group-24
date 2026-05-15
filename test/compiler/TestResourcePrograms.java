package compiler;

import org.junit.Test;

public class TestResourcePrograms {

    @Test
    public void test1_printing() throws Exception {
        CompilerTestHelper.assertProgramOutput(
                "resource-test1",
                CompilerTestHelper.readProgramResource("test1.lang"),
                "1\n1.1\nhello");
    }

    @Test
    public void test2_assignment() throws Exception {
        CompilerTestHelper.assertProgramOutput(
                "resource-test2",
                CompilerTestHelper.readProgramResource("test2.lang"),
                "1");
    }

    @Test
    public void test3_addition() throws Exception {
        CompilerTestHelper.assertProgramOutput(
                "resource-test3",
                CompilerTestHelper.readProgramResource("test3.lang"),
                "5");
    }
}

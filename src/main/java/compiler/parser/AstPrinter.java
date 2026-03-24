package compiler.parser;

public final class AstPrinter {
    private AstPrinter() {}

    public static String toTreeString(AstNode root) {
        StringBuilder sb = new StringBuilder();
        print(root, sb, "");
        return sb.toString();
    }

    private static void print(AstNode node, StringBuilder sb, String indent) {
        sb.append(indent).append(node.label()).append('\n');
        for (AstNode child : node.children()) {
            print(child, sb, indent + "  ");
        }
    }
}
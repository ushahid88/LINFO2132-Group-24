package compiler.parser;

import java.util.List;

public interface AstNode {
    String label();
    List<AstNode> children();

    default String toTreeString() {
        StringBuilder sb = new StringBuilder();
        buildTree(sb, "", true);
        return sb.toString();
    }

    private void buildTree(StringBuilder sb, String indent, boolean last) {
        sb.append(indent).append(label()).append("\n");
        List<AstNode> kids = children();
        for (int i = 0; i < kids.size(); i++) {
            boolean isLast = (i == kids.size() - 1);
            kids.get(i).buildTree(sb, indent + "  ", isLast);
        }
    }
}

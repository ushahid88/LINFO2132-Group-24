package compiler.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Node implements AstNode {
    private final String label;
    private final List<AstNode> children;

    public Node(String label, List<AstNode> children) {
        this.label = label;
        this.children = children == null ? Collections.emptyList() : children;
    }

    public Node(String label, AstNode... children) {
        this(label, children == null ? Collections.emptyList() : new ArrayList<>(Arrays.asList(children)));
    }

    public static Node leaf(String label) {
        return new Node(label, Collections.emptyList());
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public List<AstNode> children() {
        return children;
    }
}
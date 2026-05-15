package compiler.parser;

import java.util.List;

public interface AstNode {
    String label();
    List<AstNode> children();
}
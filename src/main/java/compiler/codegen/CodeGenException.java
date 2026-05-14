package compiler.codegen;

public class CodeGenException extends RuntimeException {
    public CodeGenException(String message) {
        super(message);
    }
    public CodeGenException(String message, Throwable cause) {
        super(message, cause);
    }
}
package compiler.codegen;

import compiler.parser.AstNode;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class CodeGenerator {

    private static final Set<String> BUILTIN_TYPES = new HashSet<>(Arrays.asList("INT", "FLOAT", "BOOL", "STRING"));

    private final String mainClassName;
    private final Path outputDir;

    // Collection definitions: name -> list of (fieldName, fieldDescriptor)
    private final Map<String, List<FieldDef>> collections = new LinkedHashMap<>();

    // Global variable info: name -> (descriptor, isFinal)
    private final Map<String, GlobalVarInfo> globals = new LinkedHashMap<>();

    // The .class files generated so far
    private final Map<String, byte[]> classBytes = new LinkedHashMap<>();

    // For tracking if we already wrote a collection class
    private final Set<String> generatedCollectionClasses = new HashSet<>();

    // Function signatures: funcName -> (returnDescriptor, list of parameter descriptors)
    private final Map<String, FunctionSig> functions = new LinkedHashMap<>();

    public CodeGenerator(String mainClassName, Path outputDir) {
        this.mainClassName = mainClassName;
        this.outputDir = outputDir;
    }

    public void generate(AstNode program) {
        if (!"Program".equals(program.label())) {
            throw new CodeGenException("Expected Program node, got " + program.label());
        }

        // First pass: collect collection definitions and function signatures
        for (AstNode child : program.children()) {
            if ("CollDecl".equals(child.label())) {
                registerCollection(child);
            } else if ("FunctionDecl".equals(child.label())) {
                registerFunction(child);
            }
        }

        // Generate collection class files first
        for (String collName : collections.keySet()) {
            generateCollectionClass(collName);
        }

        // Generate the main class with global variables, constants, and functions
        generateMainClass(program);
    }

    private void registerFunction(AstNode node) {
        AstNode returnTypeNode = node.children().get(0);
        String funcName = labelValue(node.children().get(1), "Identifier");
        AstNode paramsNode = node.children().get(2);

        String returnDesc = returnDescriptorFromTypeNode(returnTypeNode);
        List<String> paramDescs = new ArrayList<>();
        for (AstNode param : paramsNode.children()) {
            paramDescs.add(typeDescriptorFromTypeNode(param.children().get(0)));
        }
        functions.put(funcName, new FunctionSig(returnDesc, paramDescs));
    }

    private void registerCollection(AstNode node) {
        String name = labelValue(node.children().get(0), "CollectionName");
        AstNode fieldsNode = node.children().get(1);
        List<FieldDef> fields = new ArrayList<>();
        for (AstNode fieldDecl : fieldsNode.children()) {
            if (!"FieldDecl".equals(fieldDecl.label())) continue;
            String fieldType = typeDescriptorFromTypeNode(fieldDecl.children().get(0));
            String fieldName = labelValue(fieldDecl.children().get(1), "Identifier");
            fields.add(new FieldDef(fieldName, fieldType));
        }
        collections.put(name, fields);
    }

    private void generateCollectionClass(String collName) {
        if (generatedCollectionClasses.contains(collName)) return;
        generatedCollectionClasses.add(collName);

        List<FieldDef> fields = collections.get(collName);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                collName, null, "java/lang/Object", null);

        // Generate constructor: takes all fields as parameters
        StringBuilder descBuilder = new StringBuilder("(");
        for (FieldDef f : fields) {
            descBuilder.append(f.descriptor);
        }
        descBuilder.append(")V");
        String ctorDesc = descBuilder.toString();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int slot = 1;
        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitVarInsn(loadOpcode(f.descriptor), slot);
            mv.visitFieldInsn(Opcodes.PUTFIELD, collName, f.name, f.descriptor);
            slot += slotSize(f.descriptor);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Generate fields
        for (FieldDef f : fields) {
            cw.visitField(Opcodes.ACC_PUBLIC, f.name, f.descriptor, null, null);
        }

        cw.visitEnd();
        classBytes.put(collName, cw.toByteArray());
    }

    private void generateMainClass(AstNode program) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                mainClassName, null, "java/lang/Object", null);

        // Gather global declarations and function definitions
        List<AstNode> topLevel = program.children();
        List<AstNode> functionNodes = new ArrayList<>();
        Map<String, String> globalFieldDescriptors = new LinkedHashMap<>();
        Set<String> globalFinalFields = new HashSet<>();

        for (AstNode child : topLevel) {
            if ("ConstDecl".equals(child.label())) {
                String typeDesc = typeDescriptorFromTypeNode(child.children().get(0));
                String name = labelValue(child.children().get(1), "Identifier");
                globalFieldDescriptors.put(name, typeDesc);
                globalFinalFields.add(name);
                globals.put(name, new GlobalVarInfo(typeDesc, true));
            } else if ("VarDecl".equals(child.label())) {
                String typeDesc = typeDescriptorFromTypeNode(child.children().get(0));
                String name = labelValue(child.children().get(1), "Identifier");
                globalFieldDescriptors.put(name, typeDesc);
                globals.put(name, new GlobalVarInfo(typeDesc, false));
            } else if ("FunctionDecl".equals(child.label())) {
                functionNodes.add(child);
            }
        }

        // Generate global fields
        for (Map.Entry<String, String> entry : globalFieldDescriptors.entrySet()) {
            int access = Opcodes.ACC_STATIC;
            if (globalFinalFields.contains(entry.getKey())) {
                access |= Opcodes.ACC_FINAL;
            }
            cw.visitField(access, entry.getKey(), entry.getValue(), null, null);
        }

        // Static initializer for global variables with initializers
        boolean hasGlobalInit = false;
        for (AstNode child : topLevel) {
            if (("ConstDecl".equals(child.label()) || "VarDecl".equals(child.label()))
                    && child.children().size() >= 4) {
                hasGlobalInit = true;
                break;
            }
        }

        if (hasGlobalInit) {
            MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode();
            Map<String, LocalVarInfo> emptyLocals = new HashMap<>();
            for (AstNode child : topLevel) {
                if (("ConstDecl".equals(child.label()) || "VarDecl".equals(child.label()))
                        && child.children().size() >= 4) {
                    String name = labelValue(child.children().get(1), "Identifier");
                    String desc = globalFieldDescriptors.get(name);
                    compileExpression(child.children().get(3), clinit, emptyLocals, mainClassName, globalFieldDescriptors.keySet(), this);
                    clinit.visitFieldInsn(Opcodes.PUTSTATIC, mainClassName, name, desc);
                }
            }
            clinit.visitInsn(Opcodes.RETURN);
            clinit.visitMaxs(0, 0);
            clinit.visitEnd();
        }

        // Generate all function methods
        for (AstNode func : functionNodes) {
            generateFunction(cw, func, mainClassName, globalFieldDescriptors.keySet());
        }

        cw.visitEnd();
        classBytes.put(mainClassName, cw.toByteArray());
    }

    private void generateFunction(ClassWriter cw, AstNode funcNode, String owner,
                                   Set<String> globalVarNames) {
        AstNode returnTypeNode = funcNode.children().get(0);
        String funcName = labelValue(funcNode.children().get(1), "Identifier");
        AstNode paramsNode = funcNode.children().get(2);
        AstNode bodyNode = funcNode.children().get(3);

        boolean isMain = "main".equals(funcName);

        // Get the function descriptor built from parameter types
        FunctionSig sig = functions.get(funcName);
        String returnDesc = (sig != null) ? sig.returnDesc : returnDescriptorFromTypeNode(returnTypeNode);
        List<String> paramDescs = (sig != null) ? sig.paramDescs : new ArrayList<>();

        // Build method descriptor
        StringBuilder descBuilder = new StringBuilder("(");
        for (String paramDesc : paramDescs) {
            descBuilder.append(paramDesc);
        }
        descBuilder.append(")").append(isMain ? "V" : returnDesc);
        String methodDesc = descBuilder.toString();

        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;

        // For main function, we use the standard Java main signature
        String methodName = isMain ? "main" : funcName;
        String actualDesc = isMain ? "([Ljava/lang/String;)V" : methodDesc;

        MethodVisitor mv = cw.visitMethod(access, methodName, actualDesc, null, null);
        mv.visitCode();

        // Build local variable map with type info
        Map<String, LocalVarInfo> localSlots = new LinkedHashMap<>();
        int nextSlot = 0;

        if (isMain) {
            localSlots.put("args", new LocalVarInfo("[Ljava/lang/String;", 0));
            nextSlot = 1;
        }

        // Add parameters
        for (int i = 0; i < paramsNode.children().size(); i++) {
            AstNode param = paramsNode.children().get(i);
            String paramName = labelValue(param.children().get(1), "Identifier");
            String paramDesc = (i < paramDescs.size()) ? paramDescs.get(i) 
                : typeDescriptorFromTypeNode(param.children().get(0));
            localSlots.put(paramName, new LocalVarInfo(paramDesc, nextSlot));
            nextSlot += slotSize(paramDesc);
        }

        // Compile the function body
        compileStatements(bodyNode, mv, localSlots, owner, globalVarNames, this);

        // Add trailing return if the function didn't end with one
        if (isMain || "V".equals(returnDesc)) {
            // Don't add RETURN if the block already ended with a return statement
            if (!lastStatementIsReturn(bodyNode)) {
                mv.visitInsn(Opcodes.RETURN);
            }
        }
        // For non-void functions, the return bytecode must have been
        // emitted by the return statement.

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private boolean lastStatementIsReturn(AstNode blockNode) {
        if (!"Block".equals(blockNode.label())) return false;
        List<AstNode> children = blockNode.children();
        if (children.isEmpty()) return false;
        AstNode last = children.get(children.size() - 1);
        if ("Return".equals(last.label())) return true;
        // If the last statement is a block, check inside it recursively
        if ("Block".equals(last.label())) return lastStatementIsReturn(last);
        // If the last statement is an if/else where both branches return
        if ("If".equals(last.label())) {
            if (last.children().size() == 3) {
                return lastStatementIsReturn(last.children().get(1)) 
                    && lastStatementIsReturn(last.children().get(2));
            }
        }
        return false;
    }

    private void compileStatements(AstNode blockNode, MethodVisitor mv,
                                    Map<String, LocalVarInfo> localSlots, String owner,
                                    Set<String> globalVarNames, CodeGenerator gen) {
        for (AstNode stmt : blockNode.children()) {
            compileStatement(stmt, mv, localSlots, owner, globalVarNames, gen);
        }
    }

    private void compileStatement(AstNode stmt, MethodVisitor mv,
                                   Map<String, LocalVarInfo> localSlots, String owner,
                                   Set<String> globalVarNames, CodeGenerator gen) {
        switch (stmt.label()) {
            case "Block":
                compileStatements(stmt, mv, new LinkedHashMap<>(localSlots), owner, globalVarNames, gen);
                break;
            case "VarDecl":
            case "ConstDecl":
                compileVarDecl(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "AssignStmt":
                compileAssign(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "ExprStmt":
                compileExpression(stmt.children().get(0), mv, localSlots, owner, globalVarNames, gen);
                popIfNeeded(stmt.children().get(0), mv, localSlots, owner, globalVarNames, gen);
                break;
            case "If":
                compileIf(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "While":
                compileWhile(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "For":
                compileFor(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "Return":
                compileReturn(stmt, mv, localSlots, owner, globalVarNames, gen);
                break;
            default:
                throw new CodeGenException("Unsupported statement: " + stmt.label());
        }
    }

    /**
     * Pop a value from the stack if the expression produces a value
     * (avoids popping when the expression is a void function call or println)
     */
    /**
     * Pop a value from the stack if the expression produces a value.
     * Void functions like println/print leave nothing on the stack.
     * The node may be an Expr wrapper around the actual expression.
     */
    private void popIfNeeded(AstNode node, MethodVisitor mv,
                              Map<String, LocalVarInfo> localSlots, String owner,
                              Set<String> globalVarNames, CodeGenerator gen) {
        // Unwrap Expr nodes
        if ("Expr".equals(node.label()) && node.children().size() == 1) {
            node = node.children().get(0);
        }
        String label = node.label();
        if ("Call".equals(label)) {
            AstNode base = node.children().get(0);
            if (base.label().startsWith("Identifier")) {
                String funcName = labelValue(base, "Identifier");
                // Built-in void functions: println, print, print_INT, print_FLOAT
                if ("println".equals(funcName) || "print".equals(funcName)
                    || "print_INT".equals(funcName) || "print_FLOAT".equals(funcName)
                    || "printINT".equals(funcName) || "printFLOAT".equals(funcName)) {
                    return; // void function, no value on stack
                }
                // User-defined void functions
                FunctionSig sig = functions.get(funcName);
                if (sig != null && "V".equals(sig.returnDesc)) {
                    return; // void function, no value on stack
                }
            }
        }
        mv.visitInsn(Opcodes.POP);
    }

    private void compileVarDecl(AstNode node, MethodVisitor mv,
                                 Map<String, LocalVarInfo> localSlots, String owner,
                                 Set<String> globalVarNames, CodeGenerator gen) {
        String varName = labelValue(node.children().get(1), "Identifier");
        String desc = typeDescriptorFromTypeNode(node.children().get(0));

        int slot = nextSlot(localSlots);
        localSlots.put(varName, new LocalVarInfo(desc, slot));

        if (node.children().size() >= 4) {
            compileExpression(node.children().get(3), mv, localSlots, owner, globalVarNames, gen);
            mv.visitVarInsn(storeOpcode(desc), slot);
        }
    }

    private void compileAssign(AstNode node, MethodVisitor mv,
                                Map<String, LocalVarInfo> localSlots, String owner,
                                Set<String> globalVarNames, CodeGenerator gen) {
        // The target might be wrapped in Expr, unwrap it
        AstNode target = node.children().get(0);
        if ("Expr".equals(target.label()) && target.children().size() == 1) {
            target = target.children().get(0);
        }
        AstNode value = node.children().get(2);

        String targetLabel = target.label();
        if (targetLabel.startsWith("Identifier")) {
            String name = labelValue(target, "Identifier");
            compileExpression(value, mv, localSlots, owner, globalVarNames, gen);
            if (localSlots.containsKey(name)) {
                String desc = localSlots.get(name).desc;
                mv.visitVarInsn(storeOpcode(desc), localSlots.get(name).slot);
            } else if (globalVarNames.contains(name)) {
                mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, globals.get(name).descriptor);
            } else {
                throw new CodeGenException("Unknown variable: " + name);
            }
        } else if ("Index".equals(targetLabel)) {
            compileExpression(target.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            compileExpression(target.children().get(1), mv, localSlots, owner, globalVarNames, gen);
            compileExpression(value, mv, localSlots, owner, globalVarNames, gen);
            String arrayType = getExpressionType(target.children().get(0), localSlots, owner, globalVarNames, gen);
            if (arrayType == null || arrayType.equals("I") || arrayType.startsWith("[")) {
                mv.visitInsn(Opcodes.IASTORE);
            } else if (arrayType.equals("F")) {
                mv.visitInsn(Opcodes.FASTORE);
            } else if (arrayType.equals("Z")) {
                mv.visitInsn(Opcodes.BASTORE);
            } else {
                mv.visitInsn(Opcodes.AASTORE);
            }
        } else if ("FieldAccess".equals(targetLabel)) {
            compileExpression(target.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            String fieldName = labelValue(target.children().get(1), "Identifier");
            String objType = getExpressionType(target.children().get(0), localSlots, owner, globalVarNames, gen);
            String cleanObjType = objType.startsWith("L") ? objType.substring(1, objType.length() - 1) : objType;
            List<FieldDef> fields = collections.get(cleanObjType);
            if (fields == null) {
                throw new CodeGenException("Unknown collection type: " + cleanObjType);
            }
            String fieldDesc = null;
            for (FieldDef f : fields) {
                if (f.name.equals(fieldName)) {
                    fieldDesc = f.descriptor;
                    break;
                }
            }
            if (fieldDesc == null) throw new CodeGenException("Unknown field: " + fieldName);
            compileExpression(value, mv, localSlots, owner, globalVarNames, gen);
            mv.visitFieldInsn(Opcodes.PUTFIELD, cleanObjType, fieldName, fieldDesc);
        } else {
            throw new CodeGenException("Unsupported assignment target: " + targetLabel);
        }
    }

    private void compileIf(AstNode node, MethodVisitor mv,
                            Map<String, LocalVarInfo> localSlots, String owner,
                            Set<String> globalVarNames, CodeGenerator gen) {
        AstNode cond = node.children().get(0);
        AstNode thenBlock = node.children().get(1);

        Label elseLabel = new Label();
        Label endLabel = new Label();

        compileExpression(cond, mv, localSlots, owner, globalVarNames, gen);
        mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);

        compileStatements(thenBlock, mv, new LinkedHashMap<>(localSlots), owner, globalVarNames, gen);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(elseLabel);
        if (node.children().size() == 3) {
            AstNode elseBlock = node.children().get(2);
            compileStatements(elseBlock, mv, new LinkedHashMap<>(localSlots), owner, globalVarNames, gen);
        }

        mv.visitLabel(endLabel);
    }

    private void compileWhile(AstNode node, MethodVisitor mv,
                               Map<String, LocalVarInfo> localSlots, String owner,
                               Set<String> globalVarNames, CodeGenerator gen) {
        AstNode cond = node.children().get(0);
        AstNode body = node.children().get(1);

        Label loopStart = new Label();
        Label loopEnd = new Label();

        mv.visitLabel(loopStart);
        compileExpression(cond, mv, localSlots, owner, globalVarNames, gen);
        mv.visitJumpInsn(Opcodes.IFEQ, loopEnd);

        compileStatements(body, mv, new LinkedHashMap<>(localSlots), owner, globalVarNames, gen);
        mv.visitJumpInsn(Opcodes.GOTO, loopStart);

        mv.visitLabel(loopEnd);
    }

    private void compileFor(AstNode node, MethodVisitor mv,
                             Map<String, LocalVarInfo> localSlots, String owner,
                             Set<String> globalVarNames, CodeGenerator gen) {
        AstNode initNode = node.children().get(0);
        AstNode rangeNode = node.children().get(1);
        AstNode stepNode = node.children().get(2);
        AstNode bodyNode = node.children().get(3);

        Map<String, LocalVarInfo> loopSlots = new LinkedHashMap<>(localSlots);

        String loopVarName = null;
        if ("InitDecl".equals(initNode.label())) {
            loopVarName = labelValue(initNode.children().get(1), "Identifier");
            String desc = typeDescriptorFromTypeNode(initNode.children().get(0));
            int slot = nextSlot(loopSlots);
            loopSlots.put(loopVarName, new LocalVarInfo(desc, slot));
        } else if ("InitExpr".equals(initNode.label())) {
            loopVarName = forLoopVarFromInitExpr(initNode);
            if (loopVarName == null) {
                throw new CodeGenException("for-loop init must be a variable name when using InitExpr");
            }
            if (!loopSlots.containsKey(loopVarName)) {
                throw new CodeGenException("Unknown for-loop variable: " + loopVarName);
            }
        }

        AstNode fromNode = rangeNode.children().get(0);
        AstNode toNode = rangeNode.children().get(1);
        AstNode stepExprNode = stepNode.children().get(0);

        // Assign initial value
        if (loopVarName != null) {
            compileExpression(fromNode, mv, loopSlots, owner, globalVarNames, gen);
            mv.visitVarInsn(Opcodes.ISTORE, loopSlots.get(loopVarName).slot);
        }

        Label loopStart = new Label();
        Label loopEnd = new Label();

        mv.visitLabel(loopStart);

        if (loopVarName != null) {
            mv.visitVarInsn(Opcodes.ILOAD, loopSlots.get(loopVarName).slot);
            compileExpression(toNode, mv, loopSlots, owner, globalVarNames, gen);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
        }

        compileStatements(bodyNode, mv, new LinkedHashMap<>(loopSlots), owner, globalVarNames, gen);

        if (loopVarName != null) {
            // The step expression is the full expression for the next value (e.g., "i+1")
            compileExpression(stepExprNode, mv, loopSlots, owner, globalVarNames, gen);
            mv.visitVarInsn(Opcodes.ISTORE, loopSlots.get(loopVarName).slot);
        }

        mv.visitJumpInsn(Opcodes.GOTO, loopStart);
        mv.visitLabel(loopEnd);
    }

    /**
     * {@code for (i; ...)} init: existing variable named {@code i} (parser wraps as InitExpr).
     */
    private String forLoopVarFromInitExpr(AstNode initNode) {
        AstNode expr = initNode.children().get(0);
        AstNode core = expr;
        if ("Expr".equals(core.label()) && core.children().size() == 1) {
            core = core.children().get(0);
        }
        if (core.label().startsWith("Identifier,")) {
            return labelValue(core, "Identifier");
        }
        return null;
    }

    private void compileReturn(AstNode node, MethodVisitor mv,
                                Map<String, LocalVarInfo> localSlots, String owner,
                                Set<String> globalVarNames, CodeGenerator gen) {
        if (node.children().isEmpty()) {
            mv.visitInsn(Opcodes.RETURN);
        } else {
            compileExpression(node.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            String desc = getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
            mv.visitInsn(returnOpcode(desc));
        }
    }

    private void compileExpression(AstNode node, MethodVisitor mv,
                                    Map<String, LocalVarInfo> localSlots, String owner,
                                    Set<String> globalVarNames, CodeGenerator gen) {
        String label = node.label();

        if (label.startsWith("Integer,")) {
            String val = labelValue(node, "Integer");
            pushIntConstant(mv, Integer.parseInt(val));
            return;
        }
        if (label.startsWith("Float,")) {
            String val = labelValue(node, "Float");
            mv.visitLdcInsn(Float.parseFloat(val));
            return;
        }
        if (label.startsWith("String,")) {
            String val = labelValue(node, "String");
            mv.visitLdcInsn(val);
            return;
        }
        if (label.startsWith("Boolean,")) {
            String val = labelValue(node, "Boolean");
            mv.visitInsn("true".equals(val) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            return;
        }
        if (label.startsWith("Identifier,")) {
            String name = labelValue(node, "Identifier");
            if (localSlots.containsKey(name)) {
                LocalVarInfo info = localSlots.get(name);
                mv.visitVarInsn(loadOpcode(info.desc), info.slot);
            } else if (globalVarNames.contains(name)) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, owner, name, globals.get(name).descriptor);
            } else {
                throw new CodeGenException("Unknown variable: " + name);
            }
            return;
        }
        if (label.startsWith("CollectionName,")) {
            return;
        }
        if (label.startsWith("Type,")) {
            return;
        }

        switch (label) {
            case "Expr":
                compileExprNode(node, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "Call":
                compileCall(node, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "Index":
                compileIndex(node, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "FieldAccess":
                compileFieldAccess(node, mv, localSlots, owner, globalVarNames, gen);
                break;
            case "ArrayConstructor":
                compileArrayConstructor(node, mv, localSlots, owner, globalVarNames, gen);
                break;
            default:
                throw new CodeGenException("Unsupported expression: " + label);
        }
    }

    private void compileExprNode(AstNode node, MethodVisitor mv,
                                  Map<String, LocalVarInfo> localSlots, String owner,
                                  Set<String> globalVarNames, CodeGenerator gen) {
        if (node.children().size() == 1) {
            compileExpression(node.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            return;
        }

        if (node.children().size() == 2) {
            AstNode opNode = node.children().get(0);
            AstNode operand = node.children().get(1);
            if (opNode.label().startsWith("UnaryOperator")) {
                String op = labelValue(opNode, "UnaryOperator");
                compileExpression(operand, mv, localSlots, owner, globalVarNames, gen);
                if ("not".equals(op)) {
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitInsn(Opcodes.IXOR);
                } else if ("-".equals(op)) {
                    String operandType = getExpressionType(operand, localSlots, owner, globalVarNames, gen);
                    if ("F".equals(operandType)) {
                        mv.visitInsn(Opcodes.FNEG);
                    } else {
                        mv.visitInsn(Opcodes.INEG);
                    }
                }
            }
            return;
        }

        if (node.children().size() == 3) {
            AstNode leftExpr = node.children().get(0);
            AstNode opNode = node.children().get(1);
            AstNode rightExpr = node.children().get(2);

            if (opNode.label().startsWith("LogicalOperator")) {
                String op = labelValue(opNode, "LogicalOperator");
                if ("&&".equals(op)) {
                    compileExpression(leftExpr, mv, localSlots, owner, globalVarNames, gen);
                    Label shortFalse = new Label();
                    Label end = new Label();
                    mv.visitJumpInsn(Opcodes.IFEQ, shortFalse);
                    compileExpression(rightExpr, mv, localSlots, owner, globalVarNames, gen);
                    mv.visitJumpInsn(Opcodes.GOTO, end);
                    mv.visitLabel(shortFalse);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitLabel(end);
                    return;
                }
                if ("||".equals(op)) {
                    compileExpression(leftExpr, mv, localSlots, owner, globalVarNames, gen);
                    Label shortTrue = new Label();
                    Label end = new Label();
                    mv.visitJumpInsn(Opcodes.IFNE, shortTrue);
                    compileExpression(rightExpr, mv, localSlots, owner, globalVarNames, gen);
                    mv.visitJumpInsn(Opcodes.GOTO, end);
                    mv.visitLabel(shortTrue);
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitLabel(end);
                    return;
                }
                throw new CodeGenException("Unknown logical operator: " + op);
            }

            String leftType = getExpressionType(leftExpr, localSlots, owner, globalVarNames, gen);
            String rightType = getExpressionType(rightExpr, localSlots, owner, globalVarNames, gen);

            compileExpression(leftExpr, mv, localSlots, owner, globalVarNames, gen);
            if ("I".equals(leftType) && "F".equals(rightType)) {
                mv.visitInsn(Opcodes.I2F);
                leftType = "F";
            }
            compileExpression(rightExpr, mv, localSlots, owner, globalVarNames, gen);
            if ("F".equals(leftType) && "I".equals(rightType)) {
                mv.visitInsn(Opcodes.I2F);
                rightType = "F";
            }

            if (opNode.label().startsWith("ArithmeticOperator")) {
                String op = labelValue(opNode, "ArithmeticOperator");
                boolean isFloat = "F".equals(leftType) || "F".equals(rightType);
                compileArithmetic(mv, op, isFloat);
            } else if (opNode.label().startsWith("ComparisonOperator")) {
                String op = labelValue(opNode, "ComparisonOperator");
                boolean isFloat = "F".equals(leftType) || "F".equals(rightType);
                compileComparison(mv, op, isFloat);
            } else {
                throw new CodeGenException("Unknown operator: " + opNode.label());
            }
        }
    }

    private void compileArithmetic(MethodVisitor mv, String op, boolean isFloat) {
        int add, sub, mul, div;
        if (isFloat) {
            add = Opcodes.FADD;
            sub = Opcodes.FSUB;
            mul = Opcodes.FMUL;
            div = Opcodes.FDIV;
        } else {
            add = Opcodes.IADD;
            sub = Opcodes.ISUB;
            mul = Opcodes.IMUL;
            div = Opcodes.IDIV;
        }

        switch (op) {
            case "+": mv.visitInsn(add); break;
            case "-": mv.visitInsn(sub); break;
            case "*": mv.visitInsn(mul); break;
            case "/": mv.visitInsn(div); break;
            case "%": mv.visitInsn(Opcodes.IREM); break;
            default: throw new CodeGenException("Unknown arithmetic operator: " + op);
        }
    }

    private void compileComparison(MethodVisitor mv, String op, boolean isFloat) {
        Label trueLabel = new Label();
        Label endLabel = new Label();

        if (isFloat) {
            mv.visitInsn(Opcodes.FCMPL);
            int jumpOp;
            switch (op) {
                case "==": jumpOp = Opcodes.IFEQ; break;
                case "=/=": jumpOp = Opcodes.IFNE; break;
                case "<": jumpOp = Opcodes.IFLT; break;
                case "<=": jumpOp = Opcodes.IFLE; break;
                case ">": jumpOp = Opcodes.IFGT; break;
                case ">=": jumpOp = Opcodes.IFGE; break;
                default: throw new CodeGenException("Unknown comparison: " + op);
            }
            mv.visitJumpInsn(jumpOp, trueLabel);
        } else {
            int jumpOp;
            switch (op) {
                case "==": jumpOp = Opcodes.IF_ICMPEQ; break;
                case "=/=": jumpOp = Opcodes.IF_ICMPNE; break;
                case "<": jumpOp = Opcodes.IF_ICMPLT; break;
                case "<=": jumpOp = Opcodes.IF_ICMPLE; break;
                case ">": jumpOp = Opcodes.IF_ICMPGT; break;
                case ">=": jumpOp = Opcodes.IF_ICMPGE; break;
                default: throw new CodeGenException("Unknown comparison: " + op);
            }
            mv.visitJumpInsn(jumpOp, trueLabel);
        }
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void compileCall(AstNode node, MethodVisitor mv,
                              Map<String, LocalVarInfo> localSlots, String owner,
                              Set<String> globalVarNames, CodeGenerator gen) {
        AstNode base = node.children().get(0);
        AstNode argsNode = node.children().get(1);

        // Collection constructor
        if (base.label().startsWith("CollectionName")) {
            String collName = labelValue(base, "CollectionName");
            List<FieldDef> fields = collections.get(collName);
            StringBuilder descBuilder = new StringBuilder("(");
            for (int i = 0; i < argsNode.children().size(); i++) {
                if (fields != null && i < fields.size()) {
                    descBuilder.append(fields.get(i).descriptor);
                }
            }
            descBuilder.append(")V");
            String ctorDesc = descBuilder.toString();

            mv.visitTypeInsn(Opcodes.NEW, collName);
            mv.visitInsn(Opcodes.DUP);
            for (int i = 0; i < argsNode.children().size(); i++) {
                AstNode arg = argsNode.children().get(i);
                compileExpression(arg, mv, localSlots, owner, globalVarNames, gen);
            }
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, collName, "<init>", ctorDesc, false);
            return;
        }

        String funcName = labelValue(base, "Identifier");

        // Check built-in functions
        if ("println".equals(funcName)) {
            compilePrintln(argsNode, mv, localSlots, owner, globalVarNames, gen);
            return;
        }
        if ("print".equals(funcName)) {
            compilePrintMethod(argsNode, mv, localSlots, owner, globalVarNames, gen);
            return;
        }
        if ("print_INT".equals(funcName) || "printINT".equals(funcName)) {
            compilePrintTyped(argsNode, mv, localSlots, owner, globalVarNames, gen, "I");
            return;
        }
        if ("print_FLOAT".equals(funcName) || "printFLOAT".equals(funcName)) {
            compilePrintTyped(argsNode, mv, localSlots, owner, globalVarNames, gen, "F");
            return;
        }
        if ("read_INT".equals(funcName)) {
            compileRead(mv, "nextInt", "I");
            return;
        }
        if ("read_FLOAT".equals(funcName)) {
            compileRead(mv, "nextFloat", "F");
            return;
        }
        if ("read_STRING".equals(funcName)) {
            compileRead(mv, "nextLine", "Ljava/lang/String;");
            return;
        }
        if ("read_BOOL".equals(funcName)) {
            compileRead(mv, "nextBoolean", "Z");
            return;
        }
        if ("str".equals(funcName)) {
            compileExpression(argsNode.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
            return;
        }
        if ("length".equals(funcName)) {
            AstNode arg = argsNode.children().get(0);
            compileExpression(arg, mv, localSlots, owner, globalVarNames, gen);
            String argType = getExpressionType(arg, localSlots, owner, globalVarNames, gen);
            if ("Ljava/lang/String;".equals(argType)) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            } else {
                mv.visitInsn(Opcodes.ARRAYLENGTH);
            }
            return;
        }
        if ("floor".equals(funcName)) {
            compileExpression(argsNode.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            mv.visitInsn(Opcodes.F2D);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
            mv.visitInsn(Opcodes.D2I);
            return;
        }
        if ("ceil".equals(funcName)) {
            compileExpression(argsNode.children().get(0), mv, localSlots, owner, globalVarNames, gen);
            mv.visitInsn(Opcodes.F2D);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
            mv.visitInsn(Opcodes.D2I);
            return;
        }

        // User-defined function call
        FunctionSig sig = functions.get(funcName);
        if (sig == null) {
            throw new CodeGenException("Unknown function: " + funcName);
        }

        // Compile all arguments
        for (int i = 0; i < argsNode.children().size(); i++) {
            compileExpression(argsNode.children().get(i), mv, localSlots, owner, globalVarNames, gen);
        }

        // Build complete descriptor
        StringBuilder argDesc = new StringBuilder("(");
        for (String pd : sig.paramDescs) {
            argDesc.append(pd);
        }
        argDesc.append(")").append(sig.returnDesc);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, funcName, argDesc.toString(), false);
    }

    /**
     * println - handles all types correctly.
     * For strings: push System.out, then string, call println(String)
     * For ints: push System.out, then int, call println(int)
     */
    private void compilePrintln(AstNode argsNode, MethodVisitor mv,
                                 Map<String, LocalVarInfo> localSlots, String owner,
                                 Set<String> globalVarNames, CodeGenerator gen) {
        if (argsNode.children().isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
            return;
        }

        AstNode arg = argsNode.children().get(0);
        String argType = getExpressionType(arg, localSlots, owner, globalVarNames, gen);

        // Stack order for invokevirtual: objectref (bottom) then args (top)
        // System.out is pushed first, then the argument - correct order already
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        compileExpression(arg, mv, localSlots, owner, globalVarNames, gen);

        String printlnDesc;
        switch (argType) {
            case "I": printlnDesc = "(I)V"; break;
            case "F": printlnDesc = "(F)V"; break;
            case "Z": printlnDesc = "(Z)V"; break;
            case "Ljava/lang/String;": printlnDesc = "(Ljava/lang/String;)V"; break;
            default: printlnDesc = "(Ljava/lang/Object;)V"; break;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", printlnDesc, false);
    }

    /**
     * print - same as println but without newline
     */
    private void compilePrintMethod(AstNode argsNode, MethodVisitor mv,
                                     Map<String, LocalVarInfo> localSlots, String owner,
                                     Set<String> globalVarNames, CodeGenerator gen) {
        if (argsNode.children().isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "()V", false);
            return;
        }

        AstNode arg = argsNode.children().get(0);
        String argType = getExpressionType(arg, localSlots, owner, globalVarNames, gen);

        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        compileExpression(arg, mv, localSlots, owner, globalVarNames, gen);

        String printDesc;
        switch (argType) {
            case "I": printDesc = "(I)V"; break;
            case "F": printDesc = "(F)V"; break;
            case "Z": printDesc = "(Z)V"; break;
            case "Ljava/lang/String;": printDesc = "(Ljava/lang/String;)V"; break;
            default: printDesc = "(Ljava/lang/Object;)V"; break;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", printDesc, false);
    }

    private void compilePrintTyped(AstNode argsNode, MethodVisitor mv,
                                    Map<String, LocalVarInfo> localSlots, String owner,
                                    Set<String> globalVarNames, CodeGenerator gen, String typeDesc) {
        if (argsNode.children().isEmpty()) {
            return;
        }
        AstNode arg = argsNode.children().get(0);

        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        compileExpression(arg, mv, localSlots, owner, globalVarNames, gen);

        String printDesc;
        switch (typeDesc) {
            case "I": printDesc = "(I)V"; break;
            case "F": printDesc = "(F)V"; break;
            case "Z": printDesc = "(Z)V"; break;
            default: printDesc = "(Ljava/lang/Object;)V"; break;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", printDesc, false);
    }

    private void compileRead(MethodVisitor mv, String methodName, String returnDesc) {
        mv.visitTypeInsn(Opcodes.NEW, "java/util/Scanner");
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);

        String readMethodDesc;
        if ("nextInt".equals(methodName)) {
            readMethodDesc = "()I";
        } else if ("nextFloat".equals(methodName)) {
            readMethodDesc = "()F";
        } else if ("nextLine".equals(methodName)) {
            readMethodDesc = "()Ljava/lang/String;";
        } else {
            readMethodDesc = "()Z";
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Scanner", methodName, readMethodDesc, false);
    }

    private void compileIndex(AstNode node, MethodVisitor mv,
                               Map<String, LocalVarInfo> localSlots, String owner,
                               Set<String> globalVarNames, CodeGenerator gen) {
        compileExpression(node.children().get(0), mv, localSlots, owner, globalVarNames, gen);
        compileExpression(node.children().get(1), mv, localSlots, owner, globalVarNames, gen);
        String arrayType = getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
        if (arrayType == null || arrayType.equals("I")) {
            mv.visitInsn(Opcodes.IALOAD);
        } else if (arrayType.equals("F")) {
            mv.visitInsn(Opcodes.FALOAD);
        } else if (arrayType.equals("Z") || arrayType.equals("C") || arrayType.equals("B")) {
            mv.visitInsn(Opcodes.BALOAD);
        } else {
            mv.visitInsn(Opcodes.AALOAD);
        }
    }

    private void compileFieldAccess(AstNode node, MethodVisitor mv,
                                     Map<String, LocalVarInfo> localSlots, String owner,
                                     Set<String> globalVarNames, CodeGenerator gen) {
        compileExpression(node.children().get(0), mv, localSlots, owner, globalVarNames, gen);
        String rawObjType = getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
        // Clean the type: remove leading 'L' and trailing ';' if present
        String objType = rawObjType.startsWith("L") ? rawObjType.substring(1, rawObjType.length() - 1) : rawObjType;
        String fieldName = labelValue(node.children().get(1), "Identifier");
        List<FieldDef> fields = collections.get(objType);
        if (fields == null) {
            throw new CodeGenException("Unknown collection type: " + objType);
        }
        String fieldDesc = null;
        for (FieldDef f : fields) {
            if (f.name.equals(fieldName)) {
                fieldDesc = f.descriptor;
                break;
            }
        }
        if (fieldDesc == null) throw new CodeGenException("Unknown field: " + fieldName);
        mv.visitFieldInsn(Opcodes.GETFIELD, objType, fieldName, fieldDesc);
    }

    private void compileArrayConstructor(AstNode node, MethodVisitor mv,
                                          Map<String, LocalVarInfo> localSlots, String owner,
                                          Set<String> globalVarNames, CodeGenerator gen) {
        String typeDesc = typeDescriptorFromTypeNode(node.children().get(0));
        compileExpression(node.children().get(1), mv, localSlots, owner, globalVarNames, gen);
        int newarrayType;
        switch (typeDesc) {
            case "I": newarrayType = Opcodes.T_INT; break;
            case "F": newarrayType = Opcodes.T_FLOAT; break;
            case "Z": newarrayType = Opcodes.T_BOOLEAN; break;
            default: newarrayType = -1; break;
        }
        if (newarrayType >= 0) {
            mv.visitIntInsn(Opcodes.NEWARRAY, newarrayType);
        } else {
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        }
    }

    // ============== TYPE RESOLUTION HELPERS ==============

    String typeDescriptorFromTypeNode(AstNode node) {
        String typeName = rawTypeNameFromTypeNode(node);
        boolean isArray = typeName.endsWith("[]");
        if (isArray) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }
        String baseDesc;
        switch (typeName) {
            case "INT": baseDesc = "I"; break;
            case "FLOAT": baseDesc = "F"; break;
            case "BOOL": baseDesc = "Z"; break;
            case "STRING": baseDesc = "Ljava/lang/String;"; break;
            case "VOID": baseDesc = "V"; break;
            default:
                baseDesc = "L" + typeName + ";";
                break;
        }
        if (isArray) {
            return "[" + baseDesc;
        }
        return baseDesc;
    }

    private String rawTypeNameFromTypeNode(AstNode node) {
        if (!node.label().startsWith("Type,")) {
            throw new CodeGenException("Expected Type node, got " + node.label());
        }
        return labelValue(node, "Type");
    }

    private String returnDescriptorFromTypeNode(AstNode node) {
        if ("Type, VOID".equals(node.label())) {
            return "V";
        }
        return typeDescriptorFromTypeNode(node);
    }

    String getExpressionType(AstNode node, Map<String, LocalVarInfo> localSlots,
                              String owner, Set<String> globalVarNames,
                              CodeGenerator gen) {
        String label = node.label();
        if (label.startsWith("Integer,")) return "I";
        if (label.startsWith("Float,")) return "F";
        if (label.startsWith("Boolean,")) return "Z";
        if (label.startsWith("String,")) return "Ljava/lang/String;";
        if (label.startsWith("Identifier,")) {
            String name = labelValue(node, "Identifier");
            if (localSlots.containsKey(name)) {
                return localSlots.get(name).desc;
            } else if (globalVarNames.contains(name)) {
                GlobalVarInfo info = globals.get(name);
                return info == null ? "I" : info.descriptor;
            }
        }
        if ("Expr".equals(label)) {
            if (node.children().size() == 1) {
                return getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
            }
            if (node.children().size() == 3) {
                AstNode opNode = node.children().get(1);
                if (opNode.label().startsWith("ComparisonOperator") || opNode.label().startsWith("LogicalOperator")) {
                    return "Z";
                }
                if (opNode.label().startsWith("ArithmeticOperator")) {
                    String leftType = getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
                    String rightType = getExpressionType(node.children().get(2), localSlots, owner, globalVarNames, gen);
                    if ("F".equals(leftType) || "F".equals(rightType)) return "F";
                    return leftType;
                }
            }
            if (node.children().size() == 2) {
                return getExpressionType(node.children().get(1), localSlots, owner, globalVarNames, gen);
            }
        }
        if ("Call".equals(label)) {
            AstNode base = node.children().get(0);
            if (base.label().startsWith("CollectionName")) {
                return "L" + labelValue(base, "CollectionName") + ";";
            }
            if (base.label().startsWith("Identifier")) {
                String funcName = labelValue(base, "Identifier");
                // Check builtins
                if ("println".equals(funcName) || "print".equals(funcName)
                    || "print_INT".equals(funcName) || "print_FLOAT".equals(funcName)
                    || "printINT".equals(funcName) || "printFLOAT".equals(funcName)) {
                    return "V";
                }
                if ("read_INT".equals(funcName)) return "I";
                if ("read_FLOAT".equals(funcName)) return "F";
                if ("read_STRING".equals(funcName)) return "Ljava/lang/String;";
                if ("read_BOOL".equals(funcName)) return "Z";
                if ("str".equals(funcName)) return "Ljava/lang/String;";
                if ("length".equals(funcName)) return "I";
                if ("floor".equals(funcName) || "ceil".equals(funcName)) return "I";
                // User function
                FunctionSig sig = functions.get(funcName);
                if (sig != null) return sig.returnDesc;
            }
            return "I";
        }
        if ("Index".equals(label)) {
            return "I";
        }
        if ("FieldAccess".equals(label)) {
            String rawObjType = getExpressionType(node.children().get(0), localSlots, owner, globalVarNames, gen);
            String objType = rawObjType.startsWith("L") ? rawObjType.substring(1, rawObjType.length() - 1) : rawObjType;
            List<FieldDef> fields = collections.get(objType);
            if (fields != null) {
                String fieldName = labelValue(node.children().get(1), "Identifier");
                for (FieldDef f : fields) {
                    if (f.name.equals(fieldName)) return f.descriptor;
                }
            }
        }
        if ("ArrayConstructor".equals(label)) {
            return "[" + typeDescriptorFromTypeNode(node.children().get(0));
        }
        return "I";
    }

    // ============== BYTECODE OPCODE HELPERS ==============

    private static int loadOpcode(String desc) {
        switch (desc) {
            case "I": case "Z": case "C": case "B": case "S": return Opcodes.ILOAD;
            case "F": return Opcodes.FLOAD;
            case "D": return Opcodes.DLOAD;
            case "J": return Opcodes.LLOAD;
            default: return Opcodes.ALOAD;
        }
    }

    private static int storeOpcode(String desc) {
        switch (desc) {
            case "I": case "Z": case "C": case "B": case "S": return Opcodes.ISTORE;
            case "F": return Opcodes.FSTORE;
            case "D": return Opcodes.DSTORE;
            case "J": return Opcodes.LSTORE;
            default: return Opcodes.ASTORE;
        }
    }

    private static int returnOpcode(String desc) {
        switch (desc) {
            case "I": case "Z": case "C": case "B": case "S": return Opcodes.IRETURN;
            case "F": return Opcodes.FRETURN;
            case "D": return Opcodes.DRETURN;
            case "J": return Opcodes.LRETURN;
            case "V": return Opcodes.RETURN;
            default: return Opcodes.ARETURN;
        }
    }

    private static int slotSize(String desc) {
        switch (desc) {
            case "J": case "D": return 2;
            default: return 1;
        }
    }

    private static int nextSlot(Map<String, LocalVarInfo> slots) {
        int max = 0;
        for (LocalVarInfo v : slots.values()) {
            int end = v.slot + slotSize(v.desc);
            if (end > max) max = end;
        }
        return max;
    }

    private static void pushIntConstant(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= -128 && value <= 127) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= -32768 && value <= 32767) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private static String labelValue(AstNode node, String prefix) {
        String label = node.label();
        String delimWithSpace = prefix + ", ";
        if (label.startsWith(delimWithSpace)) {
            return label.substring(delimWithSpace.length());
        }
        String delimNoSpace = prefix + ",";
        if (label.startsWith(delimNoSpace)) {
            return label.substring(delimNoSpace.length());
        }
        throw new CodeGenException("Expected " + prefix + " label, got " + label);
    }

    public void writeClassFiles() {
        for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            String className = entry.getKey();
            byte[] bytes = entry.getValue();
            Path classFile = outputDir.resolve(className + ".class");
            try (FileOutputStream fos = new FileOutputStream(classFile.toFile())) {
                fos.write(bytes);
            } catch (IOException e) {
                throw new CodeGenException("Failed to write class file " + classFile + ": " + e.getMessage());
            }
        }
    }

    public byte[] getClassBytes(String className) {
        return classBytes.get(className);
    }

    public Map<String, byte[]> getAllClassBytes() {
        return classBytes;
    }

    // Static inner classes
    private static class GlobalVarInfo {
        final String descriptor;
        final boolean isFinal;

        GlobalVarInfo(String descriptor, boolean isFinal) {
            this.descriptor = descriptor;
            this.isFinal = isFinal;
        }
    }

    private static class FieldDef {
        final String name;
        final String descriptor;

        FieldDef(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static class FunctionSig {
        final String returnDesc;
        final List<String> paramDescs;

        FunctionSig(String returnDesc, List<String> paramDescs) {
            this.returnDesc = returnDesc;
            this.paramDescs = paramDescs;
        }
    }

    private static class LocalVarInfo {
        final String desc;
        final int slot;

        LocalVarInfo(String desc, int slot) {
            this.desc = desc;
            this.slot = slot;
        }
    }
}
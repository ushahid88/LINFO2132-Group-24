package compiler.semantic;

import compiler.parser.AstNode;

import java.util.*;

public final class SemanticAnalyzer {
    private static final TypeInfo VOID = new TypeInfo("VOID", false);
    private static final Set<String> BUILTIN_TYPES = Set.of("INT", "FLOAT", "BOOL", "STRING");
    private static final Map<String, FunctionSignature> BUILTIN_FUNCTIONS;

    static {
        Map<String, FunctionSignature> builtins = new HashMap<>();
        builtins.put("println", new FunctionSignature(VOID, List.of(new TypeInfo("STRING", false))));
        builtins.put("print", new FunctionSignature(VOID, List.of(new TypeInfo("STRING", false))));
        builtins.put("print_INT", new FunctionSignature(VOID, List.of(new TypeInfo("INT", false))));
        builtins.put("print_FLOAT", new FunctionSignature(VOID, List.of(new TypeInfo("FLOAT", false))));
        builtins.put("printINT", new FunctionSignature(VOID, List.of(new TypeInfo("INT", false))));
        builtins.put("printFLOAT", new FunctionSignature(VOID, List.of(new TypeInfo("FLOAT", false))));
        builtins.put("str", new FunctionSignature(new TypeInfo("STRING", false), List.of(new TypeInfo("INT", false))));
        builtins.put("length", new FunctionSignature(new TypeInfo("INT", false), List.of(new TypeInfo("STRING", false))));
        builtins.put("floor", new FunctionSignature(new TypeInfo("INT", false), List.of(new TypeInfo("FLOAT", false))));
        builtins.put("ceil", new FunctionSignature(new TypeInfo("INT", false), List.of(new TypeInfo("FLOAT", false))));
        builtins.put("read_INT", new FunctionSignature(new TypeInfo("INT", false), List.of()));
        builtins.put("read_FLOAT", new FunctionSignature(new TypeInfo("FLOAT", false), List.of()));
        builtins.put("read_STRING", new FunctionSignature(new TypeInfo("STRING", false), List.of()));
        builtins.put("read_BOOL", new FunctionSignature(new TypeInfo("BOOL", false), List.of()));
        BUILTIN_FUNCTIONS = Collections.unmodifiableMap(builtins);
    }

    private final Map<String, CollectionInfo> collections = new HashMap<>();
    private final Map<String, FunctionSignature> functions = new HashMap<>(BUILTIN_FUNCTIONS);
    private Scope currentScope;

    private SemanticAnalyzer() {
    }

    public static void analyze(AstNode root) {
        if (root == null) {
            throw new SemanticException("SemanticError: AST root is null");
        }
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.collectDeclarations(root);
        analyzer.analyzeProgram(root);
    }

    private void collectDeclarations(AstNode root) {
        if (!"Program".equals(root.label())) {
            throw error("SemanticError: expected Program root");
        }

        for (AstNode child : root.children()) {
            switch (child.label()) {
                case "CollDecl":
                    String collName = getCollectionName(child.children().get(0));
                    if (!Character.isUpperCase(collName.charAt(0))) {
                        throw error("CollectionError: collection name must start with a capital letter: " + collName);
                    }
                    if (BUILTIN_TYPES.contains(collName)) {
                        throw error("CollectionError: collection name cannot overwrite built-in type: " + collName);
                    }
                    if (collections.containsKey(collName)) {
                        throw error("CollectionError: duplicate collection definition: " + collName);
                    }
                    collections.put(collName, new CollectionInfo(collName));
                    break;
                case "FunctionDecl":
                    String functionName = getIdentifier(child.children().get(1));
                    if (functions.containsKey(functionName)) {
                        throw error("ArgumentError: duplicate function definition: " + functionName);
                    }
                    functions.put(functionName, parseFunctionSignature(child));
                    break;
                default:
                    break;
            }
        }
    }

    private void analyzeProgram(AstNode root) {
        currentScope = new Scope(null);
        for (AstNode child : root.children()) {
            analyzeTopLevelDeclaration(child);
        }
    }

    private void analyzeTopLevelDeclaration(AstNode node) {
        switch (node.label()) {
            case "CollDecl":
                analyzeCollection(node);
                break;
            case "FunctionDecl":
                analyzeFunction(node);
                break;
            case "VarDecl":
                analyzeVariableDeclaration(node, false);
                break;
            case "ConstDecl":
                analyzeVariableDeclaration(node, true);
                break;
            default:
                throw error("SemanticError: unexpected top-level declaration: " + node.label());
        }
    }

    private void analyzeCollection(AstNode node) {
        String name = getCollectionName(node.children().get(0));
        CollectionInfo collection = collections.get(name);
        if (collection == null) {
            throw error("CollectionError: unknown collection " + name);
        }
        AstNode fields = node.children().get(1);
        for (AstNode field : fields.children()) {
            if (!"FieldDecl".equals(field.label())) {
                continue;
            }
            TypeInfo type = resolveType(field.children().get(0));
            String fieldName = getIdentifier(field.children().get(1));
            collection.addField(fieldName, type);
        }
    }

    private void analyzeFunction(AstNode node) {
        TypeInfo returnType = resolveReturnType(node.children().get(0));
        String functionName = getIdentifier(node.children().get(1));
        FunctionSignature signature = functions.get(functionName);
        if (signature == null) {
            throw error("ArgumentError: missing function signature for " + functionName);
        }
        List<TypeInfo> paramTypes = signature.parameterTypes();
        Scope savedScope = currentScope;
        currentScope = new Scope(savedScope);

        AstNode params = node.children().get(2);
        for (int i = 0; i < params.children().size(); i++) {
            AstNode param = params.children().get(i);
            TypeInfo type = resolveType(param.children().get(0));
            String name = getIdentifier(param.children().get(1));
            if (currentScope.isDeclaredInCurrentScope(name)) {
                throw error("ScopeError: duplicate parameter name " + name);
            }
            currentScope.declareVariable(name, type);
            if (!paramTypes.isEmpty()) {
                TypeInfo expected = paramTypes.get(i);
                if (!expected.equals(type)) {
                    throw error("ArgumentError: wrong parameter type for " + name + "; expected " + expected + ", got " + type);
                }
            }
        }

        analyzeBlock(node.children().get(3), returnType);

        if (!returnType.equals(VOID) && !containsReturn(node.children().get(3))) {
            throw error("ReturnError: missing return statement in function " + functionName);
        }

        currentScope = savedScope;
    }

    private void analyzeVariableDeclaration(AstNode node, boolean isConst) {
        TypeInfo type = resolveType(node.children().get(0));
        String name = getIdentifier(node.children().get(1));
        if (currentScope.isDeclaredInCurrentScope(name)) {
            throw error("ScopeError: duplicate variable declaration " + name);
        }
        if (node.children().size() == 4) {
            TypeInfo exprType = analyzeExpression(node.children().get(3));
            if (!type.equals(exprType)) {
                throw error("TypeError: cannot assign " + exprType + " to variable " + name + " of type " + type);
            }
        }
        currentScope.declareVariable(name, type);
    }

    private void analyzeBlock(AstNode block, TypeInfo currentFunctionReturnType) {
        if (!"Block".equals(block.label())) {
            throw error("SemanticError: expected Block node, got " + block.label());
        }
        Scope savedScope = currentScope;
        currentScope = new Scope(savedScope);
        for (AstNode statement : block.children()) {
            analyzeStatement(statement, currentFunctionReturnType);
        }
        currentScope = savedScope;
    }

    private void analyzeStatement(AstNode statement, TypeInfo currentFunctionReturnType) {
        switch (statement.label()) {
            case "Block":
                analyzeBlock(statement, currentFunctionReturnType);
                break;
            case "If":
                requireBooleanCondition(analyzeExpression(statement.children().get(0)));
                analyzeStatement(statement.children().get(1), currentFunctionReturnType);
                if (statement.children().size() == 3) {
                    analyzeStatement(statement.children().get(2), currentFunctionReturnType);
                }
                break;
            case "While":
                requireBooleanCondition(analyzeExpression(statement.children().get(0)));
                analyzeStatement(statement.children().get(1), currentFunctionReturnType);
                break;
            case "For":
                analyzeFor(statement, currentFunctionReturnType);
                break;
            case "Return":
                analyzeReturn(statement, currentFunctionReturnType);
                break;
            case "VarDecl":
                analyzeVariableDeclaration(statement, false);
                break;
            case "ConstDecl":
                analyzeVariableDeclaration(statement, true);
                break;
            case "AssignStmt":
                analyzeAssign(statement);
                break;
            case "ExprStmt":
                analyzeExpression(statement.children().get(0));
                break;
            default:
                throw error("SemanticError: unsupported statement " + statement.label());
        }
    }

    private void analyzeAssign(AstNode node) {
        TypeInfo leftType = analyzeExpression(node.children().get(0));
        TypeInfo rightType = analyzeExpression(node.children().get(2));
        if (!leftType.equals(rightType)) {
            throw error("TypeError: cannot assign " + rightType + " to " + leftType);
        }
    }

    private void analyzeFor(AstNode forNode, TypeInfo currentFunctionReturnType) {
        AstNode init = forNode.children().get(0);
        Scope savedScope = currentScope;
        currentScope = new Scope(savedScope);

        if ("InitDecl".equals(init.label())) {
            TypeInfo type = resolveType(init.children().get(0));
            String name = getIdentifier(init.children().get(1));
            if (currentScope.isDeclaredInCurrentScope(name)) {
                throw error("ScopeError: duplicate variable declaration " + name);
            }
            currentScope.declareVariable(name, type);
        } else if ("InitExpr".equals(init.label())) {
            analyzeExpression(init.children().get(0));
        } else {
            throw error("SemanticError: invalid for-init node " + init.label());
        }

        TypeInfo fromType = analyzeExpression(forNode.children().get(1).children().get(0));
        TypeInfo toType = analyzeExpression(forNode.children().get(1).children().get(1));
        TypeInfo stepType = analyzeExpression(forNode.children().get(2).children().get(0));

        if (!fromType.equals(toType) || !isNumeric(fromType) || !fromType.equals(stepType)) {
            throw error("OperatorError: invalid for loop range types");
        }
        analyzeStatement(forNode.children().get(3), currentFunctionReturnType);
        currentScope = savedScope;
    }

    private void analyzeReturn(AstNode node, TypeInfo expectedReturnType) {
        if (node.children().isEmpty()) {
            if (!expectedReturnType.equals(VOID)) {
                throw error("ReturnError: function must return " + expectedReturnType);
            }
            return;
        }
        TypeInfo exprType = analyzeExpression(node.children().get(0));
        if (expectedReturnType.equals(VOID)) {
            throw error("ReturnError: return value not allowed in void function");
        }
        if (!expectedReturnType.equals(exprType)) {
            throw error("ReturnError: expected " + expectedReturnType + " but got " + exprType);
        }
    }

    private TypeInfo analyzeExpression(AstNode node) {
        switch (node.label()) {
            case "Expr":
                return analyzeExprNode(node);
            case "Call":
                return analyzeCall(node);
            case "Index":
                return analyzeIndex(node);
            case "FieldAccess":
                return analyzeFieldAccess(node);
            case "ArrayConstructor":
                return analyzeArrayConstructor(node);
            default:
                return analyzeLeaf(node);
        }
    }

    private TypeInfo analyzeExprNode(AstNode node) {
        if (node.children().size() == 1) {
            return analyzeExpression(node.children().get(0));
        }
        if (node.children().size() == 2) {
            AstNode op = node.children().get(0);
            AstNode value = node.children().get(1);
            String operator = labelValue(op, "UnaryOperator");
            TypeInfo operandType = analyzeExpression(value);
            switch (operator) {
                case "not":
                    if (!operandType.equals(new TypeInfo("BOOL", false))) {
                        throw error("OperatorError: unary not requires boolean operand");
                    }
                    return operandType;
                case "-":
                    if (!isNumeric(operandType)) {
                        throw error("OperatorError: unary - requires numeric operand");
                    }
                    return operandType;
                default:
                    throw error("OperatorError: unknown unary operator " + operator);
            }
        }
        if (node.children().size() == 3) {
            TypeInfo left = analyzeExpression(node.children().get(0));
            AstNode op = node.children().get(1);
            TypeInfo right = analyzeExpression(node.children().get(2));
            if (op.label().startsWith("ArithmeticOperator")) {
                String operator = labelValue(op, "ArithmeticOperator");
                if (!left.equals(right) || !isNumeric(left)) {
                    throw error("OperatorError: arithmetic operator " + operator + " requires same numeric operand types");
                }
                return left;
            }
            if (op.label().startsWith("ComparisonOperator")) {
                String operator = labelValue(op, "ComparisonOperator");
                if (!left.equals(right)) {
                    throw error("OperatorError: comparison operator " + operator + " requires operands of identical type");
                }
                if (operator.equals("<") || operator.equals("<=") || operator.equals(">") || operator.equals(">=")) {
                    if (!isNumeric(left)) {
                        throw error("OperatorError: operator " + operator + " requires numeric operands");
                    }
                }
                return new TypeInfo("BOOL", false);
            }
            if (op.label().startsWith("LogicalOperator")) {
                if (!left.equals(new TypeInfo("BOOL", false)) || !right.equals(new TypeInfo("BOOL", false))) {
                    throw error("OperatorError: logical operator requires boolean operands");
                }
                return new TypeInfo("BOOL", false);
            }
            throw error("OperatorError: unsupported binary operator " + op.label());
        }
        throw error("SemanticError: malformed expression node");
    }

    private TypeInfo analyzeCall(AstNode node) {
        AstNode base = node.children().get(0);
        AstNode args = node.children().get(1);
        String callableName;
        if (base.label().startsWith("Identifier")) {
            callableName = labelValue(base, "Identifier");
            FunctionSignature signature = functions.get(callableName);
            if (signature == null) {
                throw error("ScopeError: undefined function " + callableName);
            }
            List<TypeInfo> actual = new ArrayList<>();
            for (AstNode arg : args.children()) {
                actual.add(analyzeExpression(arg));
            }
            if ("println".equals(callableName)) {
                if (actual.size() != 1) {
                    throw error("ArgumentError: function println expects 1 arguments, got " + actual.size());
                }
                return VOID;
            }
            if (actual.size() != signature.parameterTypes().size()) {
                throw error("ArgumentError: function " + callableName + " expects " + signature.parameterTypes().size() + " arguments, got " + actual.size());
            }
            for (int i = 0; i < actual.size(); i++) {
                if (!actual.get(i).equals(signature.parameterTypes().get(i))) {
                    throw error("ArgumentError: argument " + (i + 1) + " of " + callableName + " must be " + signature.parameterTypes().get(i) + " but got " + actual.get(i));
                }
            }
            return signature.returnType();
        }
        if (base.label().startsWith("CollectionName")) {
            callableName = labelValue(base, "CollectionName");
            CollectionInfo collection = collections.get(callableName);
            if (collection == null) {
                throw error("ScopeError: undefined collection type " + callableName);
            }
            List<TypeInfo> actual = new ArrayList<>();
            for (AstNode arg : args.children()) {
                actual.add(analyzeExpression(arg));
            }
            if (actual.size() != collection.fields.size()) {
                throw error("ArgumentError: constructor for " + callableName + " expects " + collection.fields.size() + " arguments, got " + actual.size());
            }
            for (int i = 0; i < actual.size(); i++) {
                TypeInfo expected = collection.fields.get(i).type();
                if (!expected.equals(actual.get(i))) {
                    throw error("ArgumentError: constructor argument " + (i + 1) + " for " + callableName + " must be " + expected + " but got " + actual.get(i));
                }
            }
            return new TypeInfo(callableName, false);
        }
        throw error("ScopeError: invalid call base " + base.label());
    }

    private TypeInfo analyzeIndex(AstNode node) {
        TypeInfo baseType = analyzeExpression(node.children().get(0));
        TypeInfo indexType = analyzeExpression(node.children().get(1));
        if (!indexType.equals(new TypeInfo("INT", false))) {
            throw error("TypeError: index expression must be INT");
        }
        if (!baseType.isArray()) {
            throw error("TypeError: cannot index non-array type " + baseType);
        }
        return new TypeInfo(baseType.name(), false);
    }

    private TypeInfo analyzeFieldAccess(AstNode node) {
        TypeInfo baseType = analyzeExpression(node.children().get(0));
        String fieldName = getIdentifier(node.children().get(1));
        if (!collections.containsKey(baseType.name())) {
            throw error("TypeError: cannot access field " + fieldName + " on non-collection type " + baseType);
        }
        CollectionInfo collection = collections.get(baseType.name());
        TypeInfo fieldType = collection.getFieldType(fieldName);
        if (fieldType == null) {
            throw error("ScopeError: unknown field " + fieldName + " on type " + baseType.name());
        }
        return fieldType;
    }

    private TypeInfo analyzeArrayConstructor(AstNode node) {
        TypeInfo baseType = resolveType(node.children().get(0));
        TypeInfo sizeType = analyzeExpression(node.children().get(1));
        if (!sizeType.equals(new TypeInfo("INT", false))) {
            throw error("TypeError: array size must be INT");
        }
        return new TypeInfo(baseType.name(), true);
    }

    private TypeInfo analyzeLeaf(AstNode node) {
        String label = node.label();
        if (label.startsWith("Integer,")) {
            return new TypeInfo("INT", false);
        }
        if (label.startsWith("Float,")) {
            return new TypeInfo("FLOAT", false);
        }
        if (label.startsWith("String,")) {
            return new TypeInfo("STRING", false);
        }
        if (label.startsWith("Boolean,")) {
            return new TypeInfo("BOOL", false);
        }
        if (label.startsWith("Identifier,")) {
            String name = labelValue(node, "Identifier");
            TypeInfo type = currentScope.lookup(name);
            if (type == null) {
                throw error("ScopeError: undefined variable " + name);
            }
            return type;
        }
        if (label.startsWith("CollectionName,")) {
            String collectionName = labelValue(node, "CollectionName");
            if (!collections.containsKey(collectionName)) {
                throw error("ScopeError: undefined collection type " + collectionName);
            }
            return new TypeInfo(collectionName, false);
        }
        if (label.startsWith("Type,")) {
            return resolveType(node);
        }
        throw error("SemanticError: unsupported leaf " + label);
    }

    private TypeInfo resolveType(AstNode node) {
        if (!node.label().startsWith("Type,")) {
            throw error("TypeError: expected type node, got " + node.label());
        }
        String typeName = labelValue(node, "Type");
        boolean isArray = false;
        if (typeName.endsWith("[]")) {
            isArray = true;
            typeName = typeName.substring(0, typeName.length() - 2);
        }
        if (!BUILTIN_TYPES.contains(typeName) && !collections.containsKey(typeName)) {
            throw error("TypeError: unknown type " + typeName);
        }
        return new TypeInfo(typeName, isArray);
    }

    private TypeInfo resolveReturnType(AstNode node) {
        if (!"Type, VOID".equals(node.label())) {
            return resolveType(node);
        }
        return VOID;
    }

    private FunctionSignature parseFunctionSignature(AstNode node) {
        TypeInfo returnType = resolveReturnType(node.children().get(0));
        AstNode params = node.children().get(2);
        List<TypeInfo> parameterTypes = new ArrayList<>();
        for (AstNode param : params.children()) {
            parameterTypes.add(resolveType(param.children().get(0)));
        }
        return new FunctionSignature(returnType, parameterTypes);
    }

    private boolean containsReturn(AstNode node) {
        if ("Return".equals(node.label())) {
            return true;
        }
        for (AstNode child : node.children()) {
            if (containsReturn(child)) {
                return true;
            }
        }
        return false;
    }

    private void requireBooleanCondition(TypeInfo condType) {
        if (!condType.equals(new TypeInfo("BOOL", false))) {
            throw error("MissingConditionError: condition must be BOOL");
        }
    }

    private String getIdentifier(AstNode node) {
        if (!node.label().startsWith("Identifier,")) {
            throw error("SemanticError: expected identifier, got " + node.label());
        }
        return labelValue(node, "Identifier");
    }

    private String getCollectionName(AstNode node) {
        if (!node.label().startsWith("CollectionName,")) {
            throw error("SemanticError: expected collection name, got " + node.label());
        }
        return labelValue(node, "CollectionName");
    }

    private String labelValue(AstNode node, String prefix) {
        String label = node.label();
        String delimWithSpace = prefix + ", ";
        if (label.startsWith(delimWithSpace)) {
            return label.substring(delimWithSpace.length());
        }
        String delimNoSpace = prefix + ",";
        if (label.startsWith(delimNoSpace)) {
            return label.substring(delimNoSpace.length());
        }
        throw error("SemanticError: expected " + prefix + " label, got " + label);
    }

    private boolean isNumeric(TypeInfo type) {
        return !type.isArray() && ("INT".equals(type.name()) || "FLOAT".equals(type.name()));
    }

    private SemanticException error(String message) {
        return new SemanticException(message);
    }

    private static final class TypeInfo {
        private final String name;
        private final boolean array;

        private TypeInfo(String name, boolean array) {
            this.name = Objects.requireNonNull(name);
            this.array = array;
        }

        public String name() {
            return name;
        }

        public boolean isArray() {
            return array;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypeInfo)) return false;
            TypeInfo other = (TypeInfo) o;
            return array == other.array && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, array);
        }

        @Override
        public String toString() {
            return name + (array ? "[]" : "");
        }
    }

    private static final class FunctionSignature {
        private final TypeInfo returnType;
        private final List<TypeInfo> parameterTypes;

        private FunctionSignature(TypeInfo returnType, List<TypeInfo> parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = Collections.unmodifiableList(new ArrayList<>(parameterTypes));
        }

        public TypeInfo returnType() {
            return returnType;
        }

        public List<TypeInfo> parameterTypes() {
            return parameterTypes;
        }
    }

    private static final class CollectionInfo {
        private final String name;
        private final List<FieldInfo> fields = new ArrayList<>();
        private final Map<String, TypeInfo> fieldMap = new HashMap<>();

        private CollectionInfo(String name) {
            this.name = name;
        }

        public void addField(String fieldName, TypeInfo type) {
            if (fieldMap.containsKey(fieldName)) {
                throw new SemanticException("CollectionError: duplicate field " + fieldName + " in collection " + name);
            }
            fields.add(new FieldInfo(fieldName, type));
            fieldMap.put(fieldName, type);
        }

        public TypeInfo getFieldType(String fieldName) {
            return fieldMap.get(fieldName);
        }
    }

    private static final class FieldInfo {
        private final String name;
        private final TypeInfo type;

        private FieldInfo(String name, TypeInfo type) {
            this.name = name;
            this.type = type;
        }

        public TypeInfo type() {
            return type;
        }
    }

    private static final class Scope {
        private final Scope parent;
        private final Map<String, TypeInfo> variables = new HashMap<>();

        private Scope(Scope parent) {
            this.parent = parent;
        }

        public boolean isDeclaredInCurrentScope(String name) {
            return variables.containsKey(name);
        }

        public void declareVariable(String name, TypeInfo type) {
            variables.put(name, type);
        }

        public TypeInfo lookup(String name) {
            TypeInfo type = variables.get(name);
            if (type != null) {
                return type;
            }
            return parent == null ? null : parent.lookup(name);
        }
    }
}

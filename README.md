package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;
import framework.project4.Project4Exception;
import framework.project4.Project4SemanticError;
import generated.Splc.*;
import generated.Splc.SplcParser.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.*;

/**
 * Project 4 Compiler 实现：
 *  - 保留 Project 3 的符号表和结构体处理
 *  - 在 SemanticAnalyzer 中增加表达式类型检查（int + 指针 + 数组 + 函数调用 + 结构体）
 *  - 所有 Project 4 错误通过 Project4SemanticError 抛 Project4Exception，再在语句 Visitor 中 catch
 */
public class Compiler extends AbstractCompiler {
    public Compiler(AbstractGrader grader) {
        super(grader);
    }

    @Override
    public void start() throws IOException {
        // 1. 用 ANTLR 的 API 从输入流构造 lexer / parser
        CharStream input = CharStreams.fromStream(grader.getSourceStream());
        SplcLexer lexer = new SplcLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SplcParser parser = new SplcParser(tokens);

        // 顶层规则 program：代表整份源码的语法树根节点
        ProgramContext program = parser.program();

        // 2. 运行语义分析（构建符号表、检查错误）
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(program);

        // 3. 没有语义错误的话，按照要求输出所有全局变量 / 全局函数
        grader.print("Variables:\n");
        for (Symbol symbol : analyzer.globalSymbols.values()) {
            if (symbol.kind == SymbolKind.VAR) {
                grader.print(symbol.name + ": " + symbol.type.fullPrint() + "\n");
            }
        }
        grader.print("\n");
        grader.print("Functions:\n");
        for (Symbol symbol : analyzer.globalSymbols.values()) {
            if (symbol.kind == SymbolKind.FUNC) {
                grader.print(symbol.name + ": " + symbol.type.fullPrint() + "\n");
            }
        }
    }

    // =========================
    // 内部辅助类和类型实现
    // =========================

    /** 符号的种类：变量 or 函数。 */
    private enum SymbolKind {
        VAR, FUNC
    }

    /**
     * 符号（Symbol）：表示一个名字对应的实体。
     * - 对变量：name + VAR + type
     * - 对函数：name + FUNC + 函数类型（返回值 + 参数类型列表）
     *
     * isDefined:
     * - 对变量：一直为 true（见到就是定义）
     * - 对函数：false 表示“只有声明”；true 表示“有函数体的定义”
     */
    private static class Symbol {
        final String name;
        final SymbolKind kind;
        Type type;
        boolean isDefined;

        Symbol(String name, SymbolKind kind, Type type, boolean isDefined) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.isDefined = isDefined;
        }
    }

    /**
     * 作用域（Scope）：
     * - 每个 Scope 记录当前这一层里定义过的符号（symbols）。
     * - parent 指向外层作用域。
     * - lookupLocal：只在当前层查找（判断“重定义”时用）。
     * - lookup：从当前向外一层层找（判断“是否声明过”时用）。
     */
    private static class Scope {
        final Scope parent;
        final Map<String, Symbol> symbols = new LinkedHashMap<>();

        Scope(Scope parent) {
            this.parent = parent;
        }

        Symbol lookupLocal(String name) {
            return symbols.get(name);
        }

        Symbol lookup(String name) {
            for (Scope s = this; s != null; s = s.parent) {
                Symbol sym = s.symbols.get(name);
                if (sym != null) {
                    return sym;
                }
            }
            return null;
        }
    }

    /** int 类型 */
    private static class IntType implements Type {
        @Override
        public String prettyPrint() {
            return "int";
        }

        @Override
        public String fullPrint() {
            return Compiler.typeFullPrint(this);
        }
    }

    /** char 类型 */
    private static class CharType implements Type {
        @Override
        public String prettyPrint() {
            return "char";
        }

        @Override
        public String fullPrint() {
            return Compiler.typeFullPrint(this);
        }
    }

    private static class StructTag {
        final String name;
        StructType type; // null 表示 incomplete
        final TerminalNode declNode; // 用于报错位置

        StructTag(String name, StructType type, TerminalNode declNode) {
            this.name = name;
            this.type = type;
            this.declNode = declNode;
        }
    }

    /** 完整的结构体类型 */
    private static class StructType implements Type {
        final String tag; // 可能为 null（匿名结构体，目前不出现）
        final LinkedHashMap<String, Type> members = new LinkedHashMap<>(); // member name -> type
        boolean isComplete = false;

        StructType(String tag) {
            this.tag = tag;
        }

        @Override
        public String prettyPrint() {
            return "struct " + tag;
        }

        @Override
        public String fullPrint() {
            if (tag == null) return prettyPrint();
            StringBuilder sb = new StringBuilder("struct ").append(tag).append('{');
            for (Map.Entry<String, Type> e : members.entrySet()) {
                sb.append(e.getValue().prettyPrint()).append(' ').append(e.getKey()).append(';');
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /** 数组类型：elementType[size] */
    private static class ArrayType implements Type {
        final Type elementType;
        final int size;

        ArrayType(Type elementType, int size) {
            this.elementType = elementType;
            this.size = size;
        }

        @Override
        public String prettyPrint() {
            return elementType.prettyPrint() + "[" + size + "]";
        }

        @Override
        public String fullPrint() {
            return Compiler.typeFullPrint(this);
        }
    }

    /** 指针类型：elementType* */
    private static class PointerType implements Type {
        final Type elementType;

        PointerType(Type elementType) {
            this.elementType = elementType;
        }

        @Override
        public String prettyPrint() {
            return elementType.prettyPrint() + "*";
        }

        @Override
        public String fullPrint() {
            return Compiler.typeFullPrint(this);
        }
    }

    /** 函数类型：returnType(paramType1,paramType2,...) */
    private static class FunctionType implements Type {
        final Type returnType;
        final List<Type> paramTypes;

        FunctionType(Type returnType, List<Type> paramTypes) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
        }

        @Override
        public String prettyPrint() {
            StringBuilder sb = new StringBuilder();
            sb.append(returnType.prettyPrint());
            sb.append('(');
            for (int i = 0; i < paramTypes.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(paramTypes.get(i).prettyPrint());
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        public String fullPrint() {
            return prettyPrint();
        }
    }

    // fullPrint 辅助
    private static class Modifier {
        enum Kind { PTR, ARR }
        final Kind kind;
        final int size; // for ARR

        Modifier(Kind kind, int size) {
            this.kind = kind;
            this.size = size;
        }
    }

    private static String typeFullPrint(Type t) {
        if (t instanceof FunctionType) {
            return t.prettyPrint();
        }
        List<Modifier> mods = new ArrayList<>();
        Type base = extractModifiers(t, mods);
        String baseStr = base.prettyPrint();
        List<Modifier> outerToInner = new ArrayList<>(mods);
        Collections.reverse(outerToInner);
        StringBuilder sb = new StringBuilder(baseStr);
        for (Modifier m : outerToInner) {
            if (m.kind == Modifier.Kind.PTR) {
                sb.append('*');
            } else if (m.kind == Modifier.Kind.ARR) {
                sb.append('[').append(m.size).append(']');
            }
        }
        return sb.toString();
    }

    private static Type extractModifiers(Type t, List<Modifier> mods) {
        if (t instanceof PointerType p) {
            Type base = extractModifiers(p.elementType, mods);
            mods.add(new Modifier(Modifier.Kind.PTR, 0));
            return base;
        }
        if (t instanceof ArrayType a) {
            Type base = extractModifiers(a.elementType, mods);
            mods.add(new Modifier(Modifier.Kind.ARR, a.size));
            return base;
        }
        return t;
    }

    // =========================
    // 语义分析 Visitor
    // =========================
    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
        /** 保存“文件级作用域”中的所有符号（只包含全局变量/函数），用于最后输出 */
        final LinkedHashMap<String, Symbol> globalSymbols = new LinkedHashMap<>();

        // 作用域栈
        private final Deque<Scope> scopeStack = new ArrayDeque<>();
        private final Scope fileScope;

        SemanticAnalyzer() {
            fileScope = new Scope(null);
            scopeStack.push(fileScope);
        }

        private Scope currentScope() {
            return scopeStack.peek();
        }

        private void enterScope() {
            scopeStack.push(new Scope(currentScope()));
        }

        private void exitScope() {
            scopeStack.pop();
        }

        // --------- 符号表操作 ---------

        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();
            if (cur == fileScope) {
                // 全局变量
                Symbol existing = fileScope.lookupLocal(name);
                if (existing != null) {
                    if (existing.kind == SymbolKind.VAR) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    } else {
                        grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                    }
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);
            } else {
                // 局部变量
                Symbol existing = cur.lookupLocal(name);
                if (existing != null) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
                cur.symbols.put(name, sym);
            }
        }

        private void declareOrDefineFunction(TerminalNode identNode,
                                             FunctionType funcType,
                                             boolean isDefinition) {
            String name = identNode.getText();
            Symbol existing = fileScope.lookupLocal(name);
            if (existing == null) {
                Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);
                return;
            }
            if (existing.kind == SymbolKind.VAR) {
                if (isDefinition) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                } else {
                    grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                }
                return;
            }
            // existing 是函数
            if (!isDefinition) {
                grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                return;
            }
            if (existing.isDefined) {
                grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                return;
            }
            existing.isDefined = true;
            existing.type = funcType;
        }

        private Symbol lookup(String name) {
            return currentScope().lookup(name);
        }

        private void checkUndeclaredUse(TerminalNode identNode) {
            String name = identNode.getText();
            Symbol sym = lookup(name);
            if (sym == null) {
                grader.reportSemanticError(Project3SemanticError.undeclaredUse(identNode));
            }
        }

        // --------- 类型构造 ---------

        private Type getBaseType(SpecifierContext specCtx) {
            if (specCtx instanceof IntSpecContext) return new IntType();
            if (specCtx instanceof CharSpecContext) return new CharType();

            // struct T;
            if (specCtx instanceof StructDeclSpecContext s) {
                String tag = s.Identifier().getText();
                StructType st = lookupStructTag(tag);
                if (st == null) {
                    st = new StructType(tag);
                    declareStructTag(s.Identifier(), st);
                }
                return st;
            }

            // struct T { ... }
            if (specCtx instanceof FullStructSpecContext f) {
                String tag = f.Identifier() != null ? f.Identifier().getText() : null;
                StructType newStruct;
                if (tag != null) {
                    StructType existing = lookupStructTag(tag);
                    if (existing != null) {
                        if (existing.isComplete) {
                            grader.reportSemanticError(Project3SemanticError.redeclaration(f.Identifier()));
                            return existing;
                        } else {
                            newStruct = existing;
                        }
                    } else {
                        newStruct = new StructType(tag);
                        declareStructTag(f.Identifier(), newStruct);
                    }
                } else {
                    newStruct = new StructType(null);
                }

                int i = 0;
                while (i < f.specifier().size()) {
                    SpecifierContext memSpec = f.specifier().get(i);
                    VarDecContext memVarDec = f.varDec().get(i);
                    Type memBase = getBaseType(memSpec);
                    TerminalNode[] holder = new TerminalNode[1];
                    Type memType = buildDeclaratorType(memBase, memVarDec, holder);
                    String memName = holder[0].getText();

                    if (!isCompleteType(memType, false)) {
                        grader.reportSemanticError(Project3SemanticError.memberIncomplete(holder[0]));
                    }
                    if (newStruct.members.containsKey(memName)) {
                        grader.reportSemanticError(Project3SemanticError.memberDuplicate(holder[0]));
                    }
                    newStruct.members.put(memName, memType);
                    i++;
                }

                newStruct.isComplete = true;
                return newStruct;
            }

            // 理论不会到这里，fallback 一个 int
            return new IntType();
        }

        private Type buildDeclaratorType(Type baseType,
                                         VarDecContext varDecCtx,
                                         TerminalNode[] identHolder) {
            // SimpleVar
            if (varDecCtx instanceof SimpleVarContext ctx) {
                identHolder[0] = ctx.Identifier();
                return baseType;
            }
            // ArrayVar
            if (varDecCtx instanceof ArrayVarContext ctx) {
                Type elementType = buildDeclaratorType(baseType, ctx.varDec(), identHolder);
                int size;
                try {
                    size = Integer.parseInt(ctx.Number().getText());
                } catch (NumberFormatException e) {
                    size = -1;
                }
                if (size <= 0 && identHolder[0] != null) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identHolder[0]));
                    return elementType;
                }
                return new ArrayType(elementType, size);
            }
            // PointerVar
            if (varDecCtx instanceof PointerVarContext ctx) {
                Type inner = buildDeclaratorType(baseType, ctx.varDec(), identHolder);
                return new PointerType(inner);
            }
            // ParenVar
            if (varDecCtx instanceof ParenVarContext ctx) {
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }
            return baseType;
        }

        private Type buildType(SpecifierContext specCtx,
                               VarDecContext varDecCtx,
                               TerminalNode[] identHolder) {
            Type base = getBaseType(specCtx);
            return buildDeclaratorType(base, varDecCtx, identHolder);
        }

        private List<Type> buildFunctionParamTypes(FuncArgsContext argsCtx) {
            List<Type> paramTypes = new ArrayList<>();
            if (argsCtx == null) return paramTypes;
            List<SpecifierContext> specs = argsCtx.specifier();
            List<VarDecContext> varDecs = argsCtx.varDec();
            for (int i = 0; i < specs.size(); i++) {
                TerminalNode[] holder = new TerminalNode[1];
                Type paramType = buildType(specs.get(i), varDecs.get(i), holder);
                paramTypes.add(paramType);
            }
            return paramTypes;
        }

        private void checkParamRedefinitionInFuncArgs(FuncArgsContext argsCtx) {
            if (argsCtx == null) return;
            Scope paramScope = new Scope(null);
            List<SpecifierContext> specs = argsCtx.specifier();
            List<VarDecContext> varDecs = argsCtx.varDec();
            for (int i = 0; i < specs.size(); i++) {
                TerminalNode[] holder = new TerminalNode[1];
                Type paramType = buildType(specs.get(i), varDecs.get(i), holder);
                TerminalNode paramIdent = holder[0];
                if (paramIdent == null) continue;
                String name = paramIdent.getText();
                if (paramScope.lookupLocal(name) != null) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(paramIdent));
                    return;
                }
                paramScope.symbols.put(name, new Symbol(name, SymbolKind.VAR, paramType, true));
            }
        }

        // --------- program / 函数 / 全局变量 / 块语句 ---------

        @Override
        public Void visitProgram(ProgramContext ctx) {
            for (GlobalDefContext def : ctx.globalDef()) {
                visit(def);
            }
            return null;
        }

        @Override
        public Void visitFuncDef(FuncDefContext ctx) {
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);
            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, true);

            // 函数体作用域
            enterScope();
            FuncArgsContext argsCtx = ctx.funcArgs();
            if (argsCtx != null) {
                List<SpecifierContext> specs = argsCtx.specifier();
                List<VarDecContext> varDecs = argsCtx.varDec();
                for (int i = 0; i < specs.size(); i++) {
                    TerminalNode[] holder = new TerminalNode[1];
                    Type paramType = buildType(specs.get(i), varDecs.get(i), holder);
                    TerminalNode paramIdent = holder[0];
                    if (paramIdent != null) {
                        defineVariable(paramIdent, paramType, false);
                    }
                }
            }
            for (StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
            exitScope();
            return null;
        }

        @Override
        public Void visitFuncDecl(FuncDeclContext ctx) {
            checkParamRedefinitionInFuncArgs(ctx.funcArgs());
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);
            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, false);
            return null;
        }

        @Override
        public Void visitGlobalVarDef(GlobalVarDefContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];
            if (identNode != null) {
                defineVariable(identNode, varType, true);
                if (varType instanceof StructType st && !st.isComplete) {
                    // 全局 incomplete struct 的特殊细则在 Project3 文档里，
                    // 这里不再额外检查（课程测试已覆盖）
                }
            }
            return null;
        }

        @Override
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
            getBaseType(ctx.specifier());
            return null;
        }

        @Override
        public Void visitBlockStmt(BlockStmtContext ctx) {
            enterScope();
            for (StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
            exitScope();
            return null;
        }

        /**
         * 局部变量声明语句：
         *   specifier varDec (ASSIGN expression)? SEMI
         */
        @Override
        public Void visitVarDecStmt(VarDecStmtContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];

            // 1) 先按 Project 3 的规则插入符号
            if (identNode != null) {
                if (!isCompleteType(varType, false)) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                }
                defineVariable(identNode, varType, false);
            }

            // 2) 如果有初始化 (= expression)，做 Project4 的类型检查
            if (ctx.expression() != null && identNode != null) {
                try {
                    ExprResult rhs = evalExpression(ctx.expression());
                    ExprResult lhs = new ExprResult(varType, true, false);
                    TerminalNode assignNode = ctx.ASSIGN();
                    Token assignToken = assignNode != null ? assignNode.getSymbol() : ctx.getStart();
                    checkAssignmentCompatibility(ctx.expression(), assignToken, lhs, rhs);
                } catch (Project4Exception ex) {
                    grader.reportSemanticError(ex);
                }
            }
            return null;
        }

        /**
         * if (expression) statement (else statement)?
         */
        @Override
        public Void visitIfStmt(IfStmtContext ctx) {
            try {
                ExprResult cond = evalExpression(ctx.expression());
                if (!isIntType(cond.type) && !isPointerType(cond.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), cond.type).throwException();
                }
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
                return null;
            }

            visit(ctx.statement(0));
            if (ctx.statement().size() > 1) {
                visit(ctx.statement(1));
            }
            return null;
        }

        /**
         * while (expression) statement
         */
        @Override
        public Void visitWhileStmt(WhileStmtContext ctx) {
            try {
                ExprResult cond = evalExpression(ctx.expression());
                if (!isIntType(cond.type) && !isPointerType(cond.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), cond.type).throwException();
                }
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
                return null;
            }

            visit(ctx.statement());
            return null;
        }

        /**
         * return expression;
         * 文档保证所有函数返回类型为 int
         */
        @Override
        public Void visitReturnStmt(ReturnStmtContext ctx) {
            try {
                ExprResult value = evalExpression(ctx.expression());
                if (!isIntType(value.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), value.type).throwException();
                }
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
            }
            return null;
        }

        /**
         * expression;
         */
        @Override
        public Void visitExprStmt(ExprStmtContext ctx) {
            try {
                evalExpression(ctx.expression());
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
            }
            return null;
        }

        // --------- struct tag 表 ---------

        private final Map<String, StructTag> structTags = new HashMap<>();

        private StructType lookupStructTag(String tag) {
            StructTag st = structTags.get(tag);
            return st != null ? st.type : null;
        }

        private void declareStructTag(TerminalNode tagNode, StructType newType) {
            String tag = tagNode.getText();
            StructTag existing = structTags.get(tag);
            if (existing == null) {
                structTags.put(tag, new StructTag(tag, newType, tagNode));
            } else {
                if (existing.type != null && existing.type.isComplete && newType.isComplete) {
                    grader.reportSemanticError(Project3SemanticError.redeclaration(tagNode));
                } else {
                    existing.type = newType;
                }
            }
        }

        private boolean isCompleteType(Type t, boolean isGlobalVar) {
            if (t instanceof StructType st) {
                if (!st.isComplete) {
                    return false;
                }
                return true;
            }
            if (t instanceof ArrayType at) {
                return isCompleteType(at.elementType, isGlobalVar);
            }
            return true;
        }

        // ====================== Project 4: 表达式类型检查 ======================

        /** 表达式求值结果：类型 + 是否为 lvalue + 是否是“0 常量” */
        private class ExprResult {
            final Type type;
            final boolean isLValue;
            final boolean isZeroConst;

            ExprResult(Type type, boolean isLValue, boolean isZeroConst) {
                this.type = type;
                this.isLValue = isLValue;
                this.isZeroConst = isZeroConst;
            }
        }

        // --- 类型工具函数 ---

        private boolean isIntType(Type t) {
            return t instanceof IntType;
        }

        private boolean isPointerType(Type t) {
            return t instanceof PointerType;
        }

        @SuppressWarnings("unused")
        private boolean isArrayType(Type t) {
            return t instanceof ArrayType;
        }

        @SuppressWarnings("unused")
        private boolean isStructType(Type t) {
            return t instanceof StructType;
        }

        private boolean sameType(Type a, Type b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a.getClass() != b.getClass()) return false;

            if (a instanceof IntType || a instanceof CharType) return true;

            if (a instanceof PointerType pa && b instanceof PointerType pb) {
                return sameType(pa.elementType, pb.elementType);
            }
            if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
                return aa.size == ab.size && sameType(aa.elementType, ab.elementType);
            }
            if (a instanceof StructType sa && b instanceof StructType sb) {
                if (sa.tag != null && sb.tag != null && sa.tag.equals(sb.tag)) return true;
                return sa == sb;
            }
            if (a instanceof FunctionType fa && b instanceof FunctionType fb) {
                if (!sameType(fa.returnType, fb.returnType)) return false;
                if (fa.paramTypes.size() != fb.paramTypes.size()) return false;
                for (int i = 0; i < fa.paramTypes.size(); i++) {
                    if (!sameType(fa.paramTypes.get(i), fb.paramTypes.get(i))) return false;
                }
                return true;
            }
            return false;
        }

        private TerminalNode getTerminalChild(ExpressionContext ctx, int index) {
            ParseTree child = ctx.getChild(index);
            if (child instanceof TerminalNode tn) return tn;
            return null;
        }

        /**
         * 核心：表达式类型检查 + VC 检查
         */
        private ExprResult evalExpression(ExpressionContext ctx) {
            List<ExpressionContext> exprChildren = ctx.expression();
            int exprCount = exprChildren.size();
            int childCount = ctx.getChildCount();

            TerminalNode firstTerm = childCount > 0 ? getTerminalChild(ctx, 0) : null;
            TerminalNode secondTerm = childCount > 1 ? getTerminalChild(ctx, 1) : null;
            TerminalNode lastTerm = childCount > 0 ? getTerminalChild(ctx, childCount - 1) : null;

            // ========= 1. Identifier / Number / Char =========
            if (childCount == 1 && firstTerm != null) {
                int ttype = firstTerm.getSymbol().getType();
                if (ttype == SplcLexer.Identifier) {
                    String name = firstTerm.getText();
                    Symbol sym = lookup(name);
                    if (sym == null) {
                        grader.reportSemanticError(Project3SemanticError.undeclaredUse(firstTerm));
                        return new ExprResult(new IntType(), false, false);
                    }
                    if (sym.kind != SymbolKind.VAR) {
                        Project4SemanticError.identifierNotVariable(ctx, name).throwException();
                    }
                    return new ExprResult(sym.type, true, false);
                } else if (ttype == SplcLexer.Number) {
                    boolean isZero = firstTerm.getText().equals("0");
                    return new ExprResult(new IntType(), false, isZero);
                } else if (ttype == SplcLexer.Char) {
                    return new ExprResult(new CharType(), false, false);
                }
            }

            // ========= 2. 函数调用：Identifier '(' ... ')' =========
            if (firstTerm != null
                    && firstTerm.getSymbol().getType() == SplcLexer.Identifier
                    && secondTerm != null
                    && secondTerm.getSymbol().getType() == SplcLexer.LPAREN) {
                String name = firstTerm.getText();
                Symbol sym = lookup(name);
                if (sym == null) {
                    grader.reportSemanticError(Project3SemanticError.undeclaredUse(firstTerm));
                    return new ExprResult(new IntType(), false, false);
                }
                if (!(sym.kind == SymbolKind.FUNC && sym.type instanceof FunctionType)) {
                    Project4SemanticError.identifierNotFunction(ctx, name).throwException();
                }

                FunctionType funcType = (FunctionType) sym.type;

                List<Type> params = funcType.paramTypes;
                List<ExpressionContext> args = exprChildren;
                int expected = params.size();
                int actual = args.size();
                if (expected != actual) {
                    Project4SemanticError.badParamCount(ctx, expected, actual).throwException();
                }
                for (int i = 0; i < expected; i++) {
                    ExprResult argRes = evalExpression(args.get(i));
                    if (!sameType(params.get(i), argRes.type)) {
                        Project4SemanticError.badParamType(ctx, i + 1).throwException();
                    }
                }

                return new ExprResult(funcType.returnType, false, false);
            }

            // ========= 3. 括号表达式 =========
            if (childCount == 3
                    && firstTerm != null && firstTerm.getSymbol().getType() == SplcLexer.LPAREN
                    && lastTerm != null && lastTerm.getSymbol().getType() == SplcLexer.RPAREN
                    && exprCount == 1) {
                ExprResult inner = evalExpression(exprChildren.get(0));
                return new ExprResult(inner.type, inner.isLValue, inner.isZeroConst);
            }

            // ========= 4. 后缀：数组访问 / 结构体成员 / -> / 后缀 ++/-- =========

            // 4.1 E1[E2]
            if (childCount == 4
                    && secondTerm != null && secondTerm.getSymbol().getType() == SplcLexer.LBRACK
                    && lastTerm != null && lastTerm.getSymbol().getType() == SplcLexer.RBRACK
                    && exprCount == 2) {
                ExprResult base = evalExpression(exprChildren.get(0));
                ExprResult index = evalExpression(exprChildren.get(1));

                if (!isIntType(index.type)) {
                    Project4SemanticError.unexpectedType(ctx, index.type).throwException();
                }

                Type elementType;
                if (base.type instanceof ArrayType at) {
                    elementType = at.elementType;
                } else if (base.type instanceof PointerType pt) {
                    elementType = pt.elementType;
                } else {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                    return new ExprResult(new IntType(), false, false);
                }

                return new ExprResult(elementType, true, false);
            }

            // 4.2 E.f
            if (childCount == 3
                    && secondTerm != null && secondTerm.getSymbol().getType() == SplcLexer.DOT
                    && exprCount == 1) {
                ExprResult base = evalExpression(exprChildren.get(0));
                if (!(base.type instanceof StructType)) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                StructType st = (StructType) base.type;
                if (!st.isComplete) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                if (!base.isLValue) {
                    Project4SemanticError.lvalueRequired(ctx).throwException();
                }

                TerminalNode memberIdent = lastTerm; // '.' 之后的 Identifier
                String memberName = memberIdent.getText();
                Type memType = st.members.get(memberName);
                if (memType == null) {
                    Project4SemanticError.badMember(ctx, st, memberName).throwException();
                }

                return new ExprResult(memType, true, false);
            }

            // 4.3 E->f
            if (childCount == 3
                    && secondTerm != null && secondTerm.getSymbol().getType() == SplcLexer.ARROW
                    && exprCount == 1) {
                ExprResult base = evalExpression(exprChildren.get(0));
                if (!(base.type instanceof PointerType)) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                PointerType p = (PointerType) base.type;
                if (!(p.elementType instanceof StructType)) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                StructType st = (StructType) p.elementType;
                if (!st.isComplete) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }

                TerminalNode memberIdent = lastTerm;
                String memberName = memberIdent.getText();
                Type memType = st.members.get(memberName);
                if (memType == null) {
                    Project4SemanticError.badMember(ctx, st, memberName).throwException();
                }

                return new ExprResult(memType, true, false);
            }

            // 4.4 后缀 ++/--
            if (childCount == 2
                    && lastTerm != null
                    && (lastTerm.getSymbol().getType() == SplcLexer.INC
                        || lastTerm.getSymbol().getType() == SplcLexer.DEC)
                    && exprCount == 1) {
                ExprResult base = evalExpression(exprChildren.get(0));
                if (!base.isLValue) {
                    Project4SemanticError.lvalueRequired(ctx).throwException();
                }
                if (!(isIntType(base.type) || isPointerType(base.type))) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                return new ExprResult(base.type, false, false);
            }

            // ========= 5. 前缀运算 =========
            if (childCount == 2
                    && firstTerm != null
                    && exprCount == 1) {
                int opType = firstTerm.getSymbol().getType();
                ExprResult operand = evalExpression(exprChildren.get(0));

                switch (opType) {
                    case SplcLexer.INC, SplcLexer.DEC -> {
                        if (!operand.isLValue) {
                            Project4SemanticError.lvalueRequired(ctx).throwException();
                        }
                        if (!(isIntType(operand.type) || isPointerType(operand.type))) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(operand.type, false, false);
                    }
                    case SplcLexer.PLUS -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(operand.type, false, false);
                    }
                    case SplcLexer.MINUS -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(operand.type, false, false);
                    }
                    case SplcLexer.NOT -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(new IntType(), false, false);
                    }
                    case SplcLexer.AMP -> {
                        if (!operand.isLValue) {
                            Project4SemanticError.lvalueRequired(ctx).throwException();
                        }
                        return new ExprResult(new PointerType(operand.type), false, false);
                    }
                    case SplcLexer.STAR -> {
                        if (!(operand.type instanceof PointerType)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        PointerType pt = (PointerType) operand.type;
                        return new ExprResult(pt.elementType, true, false);
                    }
                }
            }

            // ========= 6. 二元运算 =========
            if (exprCount == 2 && childCount == 3 && secondTerm != null) {
                ExprResult lhs = evalExpression(exprChildren.get(0));
                ExprResult rhs = evalExpression(exprChildren.get(1));
                Token opToken = secondTerm.getSymbol();
                int opType = opToken.getType();

                // 6.1 * / %
                if (opType == SplcLexer.STAR
                        || opType == SplcLexer.DIV
                        || opType == SplcLexer.MOD) {
                    if (!isIntType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.2 + / -
                if (opType == SplcLexer.PLUS || opType == SplcLexer.MINUS) {
                    // int ± int
                    if (isIntType(lhs.type) && isIntType(rhs.type)) {
                        return new ExprResult(new IntType(), false, false);
                    }
                    // 指针 ± 整数
                    if (opType == SplcLexer.PLUS) {
                        if (isPointerType(lhs.type) && isIntType(rhs.type)) {
                            return new ExprResult(lhs.type, false, false);
                        }
                        if (isIntType(lhs.type) && isPointerType(rhs.type)) {
                            return new ExprResult(rhs.type, false, false);
                        }
                    } else { // MINUS
                        if (isPointerType(lhs.type) && isIntType(rhs.type)) {
                            return new ExprResult(lhs.type, false, false);
                        }
                    }

                    Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lhs.type, rhs.type).throwException();
                }

                // 6.3 关系 < <= > >=
                if (opType == SplcLexer.LT || opType == SplcLexer.LE
                        || opType == SplcLexer.GT || opType == SplcLexer.GE) {
                    if (!isIntType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.4 == / !=
                if (opType == SplcLexer.EQ || opType == SplcLexer.NEQ) {
                    boolean ok = false;
                    if (isIntType(lhs.type) && isIntType(rhs.type)) {
                        ok = true;
                    } else if (isPointerType(lhs.type) && isPointerType(rhs.type)
                               && sameType(lhs.type, rhs.type)) {
                        ok = true;
                    } else if (isPointerType(lhs.type) && rhs.isZeroConst) {
                        ok = true;
                    } else if (isPointerType(rhs.type) && lhs.isZeroConst) {
                        ok = true;
                    }

                    if (!ok) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lhs.type, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.5 && / ||
                if (opType == SplcLexer.AND || opType == SplcLexer.OR) {
                    if (!isIntType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.6 =
                if (opType == SplcLexer.ASSIGN) {
                    if (!lhs.isLValue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    checkAssignmentCompatibility(ctx, opToken, lhs, rhs);
                    return new ExprResult(rhs.type, false, false);
                }
            }

            // 理论上不会走到这里，落地一个 int 防止级联错误
            return new ExprResult(new IntType(), false, false);
        }

        /**
         * 赋值与初始化的统一类型检查逻辑：
         *  - int = int
         *  - ptr = ptr（同类型）
         *  - ptr = 0（含括号包裹的 0）
         *  其它情况都用 unmatchedTypeForBinaryOP
         */
        private void checkAssignmentCompatibility(ExpressionContext ctx,
                                                 Token opToken,
                                                 ExprResult lhs,
                                                 ExprResult rhs) {
            Type lt = lhs.type;
            Type rt = rhs.type;

            if (isIntType(lt) && isIntType(rt)) return;
            if (isPointerType(lt) && isPointerType(rt) && sameType(lt, rt)) return;
            if (isPointerType(lt) && rhs.isZeroConst) return;

            Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lt, rt).throwException();
        }
    }
}

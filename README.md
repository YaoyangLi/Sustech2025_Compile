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
 * Project 4 Compiler 实现。
 *
 * 整体思路：
 *  1. 词法分析 + 语法分析，得到 ANTLR 的 AST（program 结点）。
 *  2. SemanticAnalyzer（内部类）继承 SplcBaseVisitor<Void>，负责：
 *      - Project 3 的符号表管理、结构体完整性检查等
 *      - Project 4 的表达式类型检查（int / pointer / array / function / struct）
 *  3. Project 4 的错误统一通过 Project4SemanticError.xxx(...) 生成，再 throwException()
 *     抛出 Project4Exception，在 visitXXX(Stmt) 中 catch 并使用 grader.reportSemanticError 输出。
 *
 * 注意：
 *  - Project 4 的标准输出只要求打印语义错误行，不再要求打印 Variables/Functions。
 *  - 我们已经关闭了 ANTLR 默认的 ConsoleErrorListener，避免输出诸如
 *      line 20:4 missing ';' at 'im_a_function'
 *    这样的语法错误信息。
 */
public class Compiler extends AbstractCompiler {
    public Compiler(AbstractGrader grader) {
        super(grader);
    }

    @Override
    public void start() throws IOException {
        // 1. 从输入流读取源代码，构造 ANTLR 的 lexer / parser
        CharStream input = CharStreams.fromStream(grader.getSourceStream());
        SplcLexer lexer = new SplcLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SplcParser parser = new SplcParser(tokens);

        // ★ 关闭 ANTLR 默认的 ConsoleErrorListener。否则语法错误会直接打印在标准输出上，
        //   与助教提供的 txt 不一致（例如 missing ';'...）。
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        // 2. 解析顶层规则 program：代表整个源文件
        ProgramContext program = parser.program();

        // 3. 语义分析（构建符号表 + Project3 check + Project4 类型检查）
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(program);

        // 4. Project4 不再需要输出 Variables / Functions，标准输出只有错误行。
    }

    // =========================
    // 内部类型定义（符号表 + 类型系统）
    // =========================

    /** 符号的种类：变量 or 函数。 */
    private enum SymbolKind {
        VAR, FUNC
    }

    /**
     * 符号（Symbol）：记录一个名字代表的实体（变量 / 函数）。
     *
     * 对变量：
     *  - kind = VAR
     *  - type = 变量类型（int / char / pointer / array / struct ...）
     *  - isDefined 始终为 true
     *
     * 对函数：
     *  - kind = FUNC
     *  - type = FunctionType（返回值 + 参数类型列表）
     *  - isDefined = false 表示只有声明；true 表示已经有函数体定义。
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
     * Scope：作用域。
     *  - 每个 Scope 维护本层的符号表（Map<String, Symbol>）
     *  - parent 指向外层作用域
     *
     * lookupLocal(name) 只在当前层查找，用于检测重定义
     * lookup(name) 会向 parent 一层层查找，用于变量/函数使用时的“是否已声明”
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
                if (sym != null) return sym;
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

    /** file-scope 的 struct tag 信息（单独的 namespace） */
    private static class StructTag {
        final String name;
        StructType type;          // null 表示 incomplete
        final TerminalNode declNode; // 用于报错行号

        StructTag(String name, StructType type, TerminalNode declNode) {
            this.name = name;
            this.type = type;
            this.declNode = declNode;
        }
    }

    /** 结构体类型：记录 tag + 成员列表 + 是否完整定义（isComplete） */
    private static class StructType implements Type {
        final String tag; // 结构体名，例如 struct s0，中 tag = "s0"
        final LinkedHashMap<String, Type> members = new LinkedHashMap<>();
        boolean isComplete = false; // 是否已经有完整定义（带 { ... }）

        StructType(String tag) {
            this.tag = tag;
        }

        @Override
        public String prettyPrint() {
            return "struct " + tag;
        }

        @Override
        public String fullPrint() {
            // 按 Project3 的要求打印完整 struct 定义
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

    /** 函数类型：returnType(paramType1, paramType2, ...) */
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

    // fullPrint 辅助结构：记录“指针/数组”修饰符
    private static class Modifier {
        enum Kind { PTR, ARR }
        final Kind kind;
        final int size; // 仅在 ARR 时使用

        Modifier(Kind kind, int size) {
            this.kind = kind;
            this.size = size;
        }
    }

    /**
     * 用于类型的 fullPrint：
     *  - 把 ArrayType / PointerType 从外到里拆开，记录修饰序列
     *  - 再组合成字符串，使复杂指针+数组的打印顺序符合助教的预期
     */
    private static String typeFullPrint(Type t) {
        // 函数类型直接用 prettyPrint
        if (t instanceof FunctionType) {
            return t.prettyPrint();
        }
        List<Modifier> mods = new ArrayList<>();
        Type base = extractModifiers(t, mods);
        String baseStr = base.prettyPrint();

        // mods 是 inner-to-outer，这里反转成 outer-to-inner 再打印
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

    /** 递归拆解 ArrayType / PointerType，记录修饰符，并返回最底层的 baseType。 */
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
        return t; // int / char / struct / function
    }

    // =========================
    // 语义分析 Visitor
    // =========================
    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
        /**
         * 文件级作用域（全局变量 + 函数），用于：
         *  - 符号查找
         *  - 函数声明/定义、全局变量定义的冲突检查
         *
         * P4 不再打印这些内容，但仍然需要作为内部结构存在。
         */
        final LinkedHashMap<String, Symbol> globalSymbols = new LinkedHashMap<>();

        // 作用域栈，栈顶是当前活动作用域
        private final Deque<Scope> scopeStack = new ArrayDeque<>();
        private final Scope fileScope;

        SemanticAnalyzer() {
            fileScope = new Scope(null); // 最外层 file scope
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

        // --------- 符号表操作（变量 / 函数） ---------

        /**
         * 定义变量（全局 or 局部）。
         *  - 全局在 fileScope
         *  - 局部在当前作用域
         * 检查：
         *  - 同一作用域内的重定义（变量 vs 变量 / 变量 vs 函数）
         */
        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();
            if (cur == fileScope) {
                // 全局变量
                Symbol existing = fileScope.lookupLocal(name);
                if (existing != null) {
                    if (existing.kind == SymbolKind.VAR) {
                        // 同名全局变量
                        grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    } else {
                        // 与全局函数同名
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
                    // 块内重定义
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
                cur.symbols.put(name, sym);
            }
        }

        /**
         * 声明或定义函数。
         *  - isDefinition = false => 函数声明（无函数体）
         *  - isDefinition = true  => 函数定义（有函数体）
         *
         * 规则：
         *  - 变量 + 函数 冲突：报 redeclaration/redefinition
         *  - 函数声明重复：Redeclaration
         *  - 函数定义重复：Redefinition
         *  - 声明后第一次定义：合法
         */
        private void declareOrDefineFunction(TerminalNode identNode,
                                             FunctionType funcType,
                                             boolean isDefinition) {
            String name = identNode.getText();
            Symbol existing = fileScope.lookupLocal(name);
            if (existing == null) {
                // 第一次见到这个名字
                Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);
                return;
            }
            if (existing.kind == SymbolKind.VAR) {
                // 先有变量，再来函数
                if (isDefinition) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                } else {
                    grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                }
                return;
            }
            // 已经是函数
            if (!isDefinition) {
                // 再来一个声明
                grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                return;
            }
            if (existing.isDefined) {
                // 已经有定义，又来定义
                grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                return;
            }
            // 之前只有声明，现在第一次定义
            existing.isDefined = true;
            existing.type = funcType;
        }

        private Symbol lookup(String name) {
            return currentScope().lookup(name);
        }

        /** P3 的“使用未声明变量/函数”检测（对 Identifier 的使用而言） */
        private void checkUndeclaredUse(TerminalNode identNode) {
            String name = identNode.getText();
            Symbol sym = lookup(name);
            if (sym == null) {
                grader.reportSemanticError(Project3SemanticError.undeclaredUse(identNode));
            }
        }

        // --------- 类型构造（specifier + varDec） ---------

        /**
         * 从 specifier（如 int / char / struct ...）得到“基础类型”。
         * 对 struct 有三种情况：
         *  - struct T;                => StructDeclSpecContext
         *  - struct T { ... }         => FullStructSpecContext
         */
        private Type getBaseType(SpecifierContext specCtx) {
            if (specCtx instanceof IntSpecContext) return new IntType();
            if (specCtx instanceof CharSpecContext) return new CharType();

            // struct T;
            if (specCtx instanceof StructDeclSpecContext s) {
                String tag = s.Identifier().getText();
                StructType st = lookupStructTag(tag);
                if (st == null) {
                    // 第一次见到：不完整 struct 声明
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
                            // 已定义过完整 struct T {...}
                            grader.reportSemanticError(Project3SemanticError.redeclaration(f.Identifier()));
                            return existing;
                        } else {
                            // 之前有 struct T; 现在补全定义
                            newStruct = existing;
                        }
                    } else {
                        // 第一次见到 struct T {...}
                        newStruct = new StructType(tag);
                        declareStructTag(f.Identifier(), newStruct);
                    }
                } else {
                    // 匿名 struct
                    newStruct = new StructType(null);
                }

                // 填充成员列表
                int i = 0;
                while (i < f.specifier().size()) {
                    SpecifierContext memSpec = f.specifier().get(i);
                    VarDecContext memVarDec = f.varDec().get(i);
                    Type memBase = getBaseType(memSpec);
                    TerminalNode[] holder = new TerminalNode[1];
                    Type memType = buildDeclaratorType(memBase, memVarDec, holder);
                    String memName = holder[0].getText();

                    if (!isCompleteType(memType, false)) {
                        // 成员类型不能是不完整 struct
                        grader.reportSemanticError(Project3SemanticError.memberIncomplete(holder[0]));
                    }
                    if (newStruct.members.containsKey(memName)) {
                        // 成员重名
                        grader.reportSemanticError(Project3SemanticError.memberDuplicate(holder[0]));
                    }
                    newStruct.members.put(memName, memType);
                    i++;
                }

                newStruct.isComplete = true;
                return newStruct;
            }

            // 理论不会到这里，安全返回 int
            return new IntType();
        }

        /**
         * 在已有类型上“在最里面”再包一层数组维度。
         *
         * 示例：
         *   inner = int[20][10]
         *   addInnermostArray(inner, 30) -> int[30][20][10]
         *
         * 这样：
         *   int arr[10][20][30];
         * 解析顺序大致类似：(((int)[10])[20])[30]
         * 经过本函数处理后，最终 prettyPrint 是 int[30][20][10]
         * 与老师的输出一致；此时 arr[2] 的类型是 int[30][20]。
         */
        private Type addInnermostArray(Type inner, int size) {
            if (inner instanceof ArrayType at) {
                // inner 仍然是数组，则递归到最里层去插入新维度
                Type newElem = addInnermostArray(at.elementType, size);
                return new ArrayType(newElem, at.size);
            } else {
                // inner 不是数组时，这就是最里层
                return new ArrayType(inner, size);
            }
        }

        /**
         * 根据 varDec 对 baseType 进行“包裹”（数组 / 指针 / 括号）。
         * identHolder[0] 用于保存变量名（SimpleVarContext 下的 Identifier）。
         */
        private Type buildDeclaratorType(Type baseType,
                                         VarDecContext varDecCtx,
                                         TerminalNode[] identHolder) {
            // SimpleVar: 只是一个标识符，仅记录名字
            if (varDecCtx instanceof SimpleVarContext ctx) {
                identHolder[0] = ctx.Identifier();
                return baseType;
            }
            // ArrayVar: 在当前已有类型的“最里面”插入一个数组维度
            if (varDecCtx instanceof ArrayVarContext ctx) {
                // 先处理内部的 varDec
                Type inner = buildDeclaratorType(baseType, ctx.varDec(), identHolder);

                int size;
                try {
                    size = Integer.parseInt(ctx.Number().getText());
                } catch (NumberFormatException e) {
                    size = -1;
                }
                if (size <= 0 && identHolder[0] != null) {
                    // 数组长度非法 => Definition of incomplete type
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identHolder[0]));
                    return inner;
                }

                // 真正的关键：把这个 size 插入到最里层数组维度
                return addInnermostArray(inner, size);
            }
            // PointerVar: ★ 扩展2：指针
            if (varDecCtx instanceof PointerVarContext ctx) {
                Type inner = buildDeclaratorType(baseType, ctx.varDec(), identHolder);
                return new PointerType(inner);
            }
            // ParenVar: 括号改变优先级，类型构造递归下去即可
            if (varDecCtx instanceof ParenVarContext ctx) {
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }
            return baseType;
        }

        /** specifier + varDec => 完整类型 + identHolder 内有变量名 */
        private Type buildType(SpecifierContext specCtx,
                               VarDecContext varDecCtx,
                               TerminalNode[] identHolder) {
            Type base = getBaseType(specCtx);
            return buildDeclaratorType(base, varDecCtx, identHolder);
        }

        /** 从 funcArgs 中构造参数类型列表（只关心类型，不关心参数名） */
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

        /**
         * 专门用于“函数声明”的参数重名检查。
         * 要求：同一个函数参数列表中，参数名不能重复。
         */
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
                    // 参数重复定义
                    grader.reportSemanticError(Project3SemanticError.redefinition(paramIdent));
                    return;
                }
                paramScope.symbols.put(name, new Symbol(name, SymbolKind.VAR, paramType, true));
            }
        }

        // --------- 顶层 program / 函数 / 全局变量 / 块语句 ---------

        @Override
        public Void visitProgram(ProgramContext ctx) {
            for (GlobalDefContext def : ctx.globalDef()) {
                visit(def);
            }
            return null;
        }

        /** 函数定义：specifier Identifier '(' funcArgs ')' '{' statement* '}' */
        @Override
        public Void visitFuncDef(FuncDefContext ctx) {
            // 1) 构造函数类型
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);
            TerminalNode identNode = ctx.Identifier();
            // 2) 在全局作用域登记这个“函数定义”
            declareOrDefineFunction(identNode, funcType, true);

            // 3) 函数体的局部作用域
            enterScope();
            //    将参数作为局部变量插入符号表
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
            // 4) 访问函数体中的每一条语句（Project4 类型检查在这些 Visit 里进行）
            for (StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
            exitScope();
            return null;
        }

        /** 函数声明：specifier Identifier '(' funcArgs ')' ';' */
        @Override
        public Void visitFuncDecl(FuncDeclContext ctx) {
            // 先检测参数名是否重复
            checkParamRedefinitionInFuncArgs(ctx.funcArgs());
            // 构造函数类型
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);
            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, false);
            return null;
        }

        /** 全局变量定义：specifier varDec ';' */
        @Override
        public Void visitGlobalVarDef(GlobalVarDefContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];
            if (identNode != null) {
                defineVariable(identNode, varType, true);
                if (varType instanceof StructType st && !st.isComplete) {
                    // Project3 中对“不完整 struct 的全局变量”的特殊规则这里不额外处理
                }
            }
            return null;
        }

        /** 全局 struct 声明：specifier ';'（其中 specifier 是 struct 相关） */
        @Override
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
            getBaseType(ctx.specifier());
            return null;
        }

        /** 语句块：'{' statement* '}' -> 新作用域 */
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
         *   specifier varDec (ASSIGN expression)? ';'
         *
         * 处理流程：
         *  1) 按 Project3 构造类型 + 检查 incomplete struct（局部变量必须 complete）
         *  2) 插入符号表
         *  3) 如果带初始化表达式，对 RHS 做表达式类型检查，再按“赋值”规则检查类型兼容
         */
        @Override
        public Void visitVarDecStmt(VarDecStmtContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];

            // 第一步：Project3 的局部变量检查
            if (identNode != null) {
                if (!isCompleteType(varType, false)) {
                    // 局部变量定义时类型必须 complete
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                }
                defineVariable(identNode, varType, false);
            }

            // 第二步：如果存在初始化表达式 (= expression)，按“赋值表达式”规则检查
            if (ctx.expression() != null && identNode != null) {
                try {
                    ExprResult rhs = evalExpression(ctx.expression());
                    ExprResult lhs = new ExprResult(varType, true, false);
                    TerminalNode assignNode = ctx.ASSIGN();
                    Token assignToken = assignNode != null ? assignNode.getSymbol() : ctx.getStart();
                    checkAssignmentCompatibility(ctx.expression(), assignToken, lhs, rhs);
                } catch (Project4Exception ex) {
                    // 捕获 Project4Exception，改用 grader.reportSemanticError 输出
                    grader.reportSemanticError(ex);
                }
            }
            return null;
        }

        /** if (expression) statement (else statement)? */
        @Override
        public Void visitIfStmt(IfStmtContext ctx) {
            try {
                // 条件表达式必须是 int 或 pointer
                ExprResult cond = evalExpression(ctx.expression());
                if (!isIntType(cond.type) && !isPointerType(cond.type)) {
                    Project4SemanticError.unexpectedType(ctx.expression(), cond.type).throwException();
                }
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
                return null;
            }

            // then 分支
            visit(ctx.statement(0));
            // else 分支（如果有）
            if (ctx.statement().size() > 1) {
                visit(ctx.statement(1));
            }
            return null;
        }

        /** while (expression) statement */
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

        /** return expression; —— 课程保证所有函数返回类型都是 int */
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

        /** expression; —— 纯表达式语句，仅做表达式类型检查 */
        @Override
        public Void visitExprStmt(ExprStmtContext ctx) {
            try {
                evalExpression(ctx.expression());
            } catch (Project4Exception ex) {
                grader.reportSemanticError(ex);
            }
            return null;
        }

        // --------- struct tag 表（file-scope） ---------

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
                    // 重复完整定义
                    grader.reportSemanticError(Project3SemanticError.redeclaration(tagNode));
                } else {
                    // 不完整 -> 完整 的补全情况，直接替换引用
                    existing.type = newType;
                }
            }
        }

        /**
         * 判断一个类型是否“完整”。
         *  - StructType：必须 isComplete = true
         *  - ArrayType：其 elementType 必须 complete
         *  - 指针 / int / char / function：都认为是 complete
         */
        private boolean isCompleteType(Type t, boolean isGlobalVar) {
            if (t instanceof StructType st) {
                return st.isComplete;
            }
            if (t instanceof ArrayType at) {
                return isCompleteType(at.elementType, isGlobalVar);
            }
            return true;
        }

        // ====================== Project 4: 表达式类型检查 ======================

        /**
         * ExprResult：表达式求值的“静态类型结果”，包含：
         *  - type       : 表达式的 Type（int / pointer / array / struct ...）
         *  - isLValue   : 是否可以作为左值（能否出现在赋值符号左边）
         *  - isZeroConst: 是否是整型常量 0（用于 ptr == 0 / ptr = 0 这种特殊情况）
         */
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

        /**
         * 粗略的“类型相等”判断，主要用于：
         *  - 指针 = 指针
         *  - 指针 == 指针
         *  - 函数类型比较（如果需要）
         */
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

        /** 获取 ExpressionContext 的第 index 个子节点，如果是终结符则返回 TerminalNode */
        private TerminalNode getTerminalChild(ExpressionContext ctx, int index) {
            ParseTree child = ctx.getChild(index);
            if (child instanceof TerminalNode tn) return tn;
            return null;
        }

        /**
         * evalExpression：核心表达式类型检查函数。
         * 思路：
         *  - 根据 childCount / exprChildren 数量 + 中间终结符，判断当前是哪个形式：
         *      · 标识符 / 数字常量 / 字符常量
         *      · 函数调用
         *      · 括号表达式 (expr)
         *      · E1[E2] / E.f / E->f / 后缀++ --
         *      · 前缀 ++/--/+/ -/!/ &/*
         *      · 二元运算（+ - * / % < <= > >= == != && || =）
         *  - 对每种形式做相应的类型规则检查。
         */
        private ExprResult evalExpression(ExpressionContext ctx) {
            List<ExpressionContext> exprChildren = ctx.expression();
            int exprCount = exprChildren.size();
            int childCount = ctx.getChildCount();

            TerminalNode firstTerm = childCount > 0 ? getTerminalChild(ctx, 0) : null;
            TerminalNode secondTerm = childCount > 1 ? getTerminalChild(ctx, 1) : null;
            TerminalNode lastTerm = childCount > 0 ? getTerminalChild(ctx, childCount - 1) : null;

            // ========= 1. 基本项：Identifier / Number / Char =========
            if (childCount == 1 && firstTerm != null) {
                int ttype = firstTerm.getSymbol().getType();
                if (ttype == SplcLexer.Identifier) {
                    // 使用一个标识符，先查符号表
                    String name = firstTerm.getText();
                    Symbol sym = lookup(name);
                    if (sym == null) {
                        // P3 的“未声明使用”
                        grader.reportSemanticError(Project3SemanticError.undeclaredUse(firstTerm));
                        // 返回一个 dummy int，避免报一条错后产生太多连锁错误
                        return new ExprResult(new IntType(), false, false);
                    }
                    if (sym.kind != SymbolKind.VAR) {
                        // 这里只是裸用 Identifier，当成“变量使用”，如果符号是函数则报错
                        Project4SemanticError.identifierNotVariable(ctx, name).throwException();
                    }
                    // 变量使用 -> lvalue
                    return new ExprResult(sym.type, true, false);
                } else if (ttype == SplcLexer.Number) {
                    // 数字常量：类型为 int，lvalue = false，isZeroConst 取决于是否为 0
                    boolean isZero = firstTerm.getText().equals("0");
                    return new ExprResult(new IntType(), false, isZero);
                } else if (ttype == SplcLexer.Char) {
                    // 字符常量：类型为 char，非 lvalue
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
                    // 未声明函数
                    grader.reportSemanticError(Project3SemanticError.undeclaredUse(firstTerm));
                    return new ExprResult(new IntType(), false, false);
                }
                if (!(sym.kind == SymbolKind.FUNC && sym.type instanceof FunctionType)) {
                    // 标识符存在，但不是函数
                    Project4SemanticError.identifierNotFunction(ctx, name).throwException();
                }

                FunctionType funcType = (FunctionType) sym.type;

                // 实参列表是当前 expression 结点的 expression() 子节点
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

                // 函数调用表达式的类型 == 函数返回值类型，非 lvalue，也不是“0 常量”
                return new ExprResult(funcType.returnType, false, false);
            }

            // ========= 3. 括号表达式： '(' expression ')' =========
            if (childCount == 3
                    && firstTerm != null && firstTerm.getSymbol().getType() == SplcLexer.LPAREN
                    && lastTerm != null && lastTerm.getSymbol().getType() == SplcLexer.RPAREN
                    && exprCount == 1) {
                ExprResult inner = evalExpression(exprChildren.get(0));
                // 括号不会改变 lvalue / isZeroConst 的性质
                return new ExprResult(inner.type, inner.isLValue, inner.isZeroConst);
            }

            // ========= 4. 后缀运算：下标、结构体成员、->、后缀 ++/-- =========

            // 4.1 E1[E2]
            if (childCount == 4
                    && secondTerm != null && secondTerm.getSymbol().getType() == SplcLexer.LBRACK
                    && lastTerm != null && lastTerm.getSymbol().getType() == SplcLexer.RBRACK
                    && exprCount == 2) {
                ExprResult base = evalExpression(exprChildren.get(0));
                ExprResult index = evalExpression(exprChildren.get(1));

                // 下标必须是 int
                if (!isIntType(index.type)) {
                    Project4SemanticError.unexpectedType(ctx, index.type).throwException();
                }

                Type elementType;
                if (base.type instanceof ArrayType at) {
                    // 数组下标：结果类型是元素类型，lvalue
                    elementType = at.elementType;
                } else if (base.type instanceof PointerType pt) {
                    // 指针下标：等价 *(p + i)
                    elementType = pt.elementType;
                } else {
                    // 既不是数组也不是指针
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
                    // 访问成员需要结构体对象本身是 lvalue
                    Project4SemanticError.lvalueRequired(ctx).throwException();
                }

                TerminalNode memberIdent = lastTerm; // '.' 后面的 Identifier
                String memberName = memberIdent.getText();
                Type memType = st.members.get(memberName);
                if (memType == null) {
                    // 不是 struct 的成员
                    Project4SemanticError.badMember(ctx, st, memberName).throwException();
                }

                // 成员表达式是 lvalue
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

                // E->f 同样是 lvalue
                return new ExprResult(memType, true, false);
            }

            // 4.4 后缀 ++ / --
            if (childCount == 2
                    && lastTerm != null
                    && (lastTerm.getSymbol().getType() == SplcLexer.INC
                        || lastTerm.getSymbol().getType() == SplcLexer.DEC)
                    && exprCount == 1) {
                ExprResult base = evalExpression(exprChildren.get(0));
                if (!base.isLValue) {
                    Project4SemanticError.lvalueRequired(ctx).throwException();
                }
                // int 或 pointer 才能自增/自减
                if (!(isIntType(base.type) || isPointerType(base.type))) {
                    Project4SemanticError.unexpectedType(ctx, base.type).throwException();
                }
                // 后缀 ++/-- 结果类型与操作数相同，但不是 lvalue
                return new ExprResult(base.type, false, false);
            }

            // ========= 5. 前缀运算（INC/DEC/+/ -/!/ &/*） =========
            if (childCount == 2
                    && firstTerm != null
                    && exprCount == 1) {
                int opType = firstTerm.getSymbol().getType();
                ExprResult operand = evalExpression(exprChildren.get(0));

                switch (opType) {
                    // 前缀 ++ / --
                    case SplcLexer.INC, SplcLexer.DEC -> {
                        if (!operand.isLValue) {
                            Project4SemanticError.lvalueRequired(ctx).throwException();
                        }
                        if (!(isIntType(operand.type) || isPointerType(operand.type))) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        // 结果不是 lvalue
                        return new ExprResult(operand.type, false, false);
                    }
                    // 一元 +：要求操作数为 int
                    case SplcLexer.PLUS -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(operand.type, false, false);
                    }
                    // 一元 -：要求操作数为 int
                    case SplcLexer.MINUS -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        return new ExprResult(operand.type, false, false);
                    }
                    // 逻辑非 !
                    case SplcLexer.NOT -> {
                        if (!isIntType(operand.type)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        // 结果是 int
                        return new ExprResult(new IntType(), false, false);
                    }
                    // 取地址 &
                    case SplcLexer.AMP -> {
                        if (!operand.isLValue) {
                            Project4SemanticError.lvalueRequired(ctx).throwException();
                        }
                        // &E 的类型是 “指向 E 类型的指针”
                        return new ExprResult(new PointerType(operand.type), false, false);
                    }
                    // 解引用 *
                    case SplcLexer.STAR -> {
                        if (!(operand.type instanceof PointerType)) {
                            Project4SemanticError.unexpectedType(ctx, operand.type).throwException();
                        }
                        PointerType pt = (PointerType) operand.type;
                        // *p 是 lvalue
                        return new ExprResult(pt.elementType, true, false);
                    }
                }
            }

            // ========= 6. 二元运算（expr op expr） =========
            if (exprCount == 2 && childCount == 3 && secondTerm != null) {
                ExprResult lhs = evalExpression(exprChildren.get(0));
                ExprResult rhs = evalExpression(exprChildren.get(1));
                Token opToken = secondTerm.getSymbol();
                int opType = opToken.getType();

                // 6.1 乘 / 除 / 取模：* / / / %
                if (opType == SplcLexer.STAR
                        || opType == SplcLexer.DIV
                        || opType == SplcLexer.MOD) {
                    if (!isIntType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    // 结果是 int
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.2 加 / 减：+
                if (opType == SplcLexer.PLUS || opType == SplcLexer.MINUS) {
                    // int ± int -> int
                    if (isIntType(lhs.type) && isIntType(rhs.type)) {
                        return new ExprResult(new IntType(), false, false);
                    }
                    // 指针 ± 整数
                    if (opType == SplcLexer.PLUS) {
                        // 指针 + int
                        if (isPointerType(lhs.type) && isIntType(rhs.type)) {
                            return new ExprResult(lhs.type, false, false);
                        }
                        // int + 指针
                        if (isIntType(lhs.type) && isPointerType(rhs.type)) {
                            return new ExprResult(rhs.type, false, false);
                        }
                    } else { // MINUS
                        // 指针 - int
                        if (isPointerType(lhs.type) && isIntType(rhs.type)) {
                            return new ExprResult(lhs.type, false, false);
                        }
                    }

                    // 其它组合非法
                    Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lhs.type, rhs.type).throwException();
                }

                // 6.3 关系运算 < <= > >=
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

                // 6.4 相等性运算 == / !=
                if (opType == SplcLexer.EQ || opType == SplcLexer.NEQ) {
                    boolean ok = false;
                    if (isIntType(lhs.type) && isIntType(rhs.type)) {
                        // int 与 int 比较
                        ok = true;
                    } else if (isPointerType(lhs.type) && isPointerType(rhs.type)
                               && sameType(lhs.type, rhs.type)) {
                        // 同类型指针之间比较
                        ok = true;
                    } else if (isPointerType(lhs.type) && rhs.isZeroConst) {
                        // 指针 vs 0
                        ok = true;
                    } else if (isPointerType(rhs.type) && lhs.isZeroConst) {
                        // 0 vs 指针
                        ok = true;
                    }

                    if (!ok) {
                        Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lhs.type, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.5 逻辑与 / 或：&& / ||
                if (opType == SplcLexer.AND || opType == SplcLexer.OR) {
                    if (!isIntType(lhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, lhs.type).throwException();
                    }
                    if (!isIntType(rhs.type)) {
                        Project4SemanticError.unexpectedType(ctx, rhs.type).throwException();
                    }
                    return new ExprResult(new IntType(), false, false);
                }

                // 6.6 赋值 =
                if (opType == SplcLexer.ASSIGN) {
                    if (!lhs.isLValue) {
                        Project4SemanticError.lvalueRequired(ctx).throwException();
                    }
                    checkAssignmentCompatibility(ctx, opToken, lhs, rhs);
                    // 赋值表达式的结果类型为 RHS 的类型
                    return new ExprResult(rhs.type, false, false);
                }
            }

            // 理论上不会走到这里，为防止 NPE，返回一个 dummy int
            return new ExprResult(new IntType(), false, false);
        }

        /**
         * 赋值和初始化的通用检查逻辑：
         *  允许的情况：
         *   - int = int
         *   - ptr = ptr（同类型）
         *   - ptr = 0（或形如 (0) 这种括号包裹的 0）
         *
         *  其它情况都用 unmatchedTypeForBinaryOP 报 Unexpected Type for operator =。
         */
        private void checkAssignmentCompatibility(ExpressionContext ctx,
                                                 Token opToken,
                                                 ExprResult lhs,
                                                 ExprResult rhs) {
            Type lt = lhs.type;
            Type rt = rhs.type;

            // int = int
            if (isIntType(lt) && isIntType(rt)) return;
            // pointer = pointer（完全同类型）
            if (isPointerType(lt) && isPointerType(rt) && sameType(lt, rt)) return;
            // pointer = 0
            if (isPointerType(lt) && rhs.isZeroConst) return;

            Project4SemanticError.unmatchedTypeForBinaryOP(ctx, opToken, lt, rt).throwException();
        }
    }
}

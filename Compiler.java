package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;

import generated.Splc.*;
import generated.Splc.SplcParser.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project3 的编译器入口类。
 *
 * 整体流程：
 *   1. 从 grader 拿到输入流，用 ANTLR 生成语法树（program）。
 *   2. 调用 SemanticAnalyzer（Visitor）做语义分析和符号表构建。
 *   3. 如果语义分析中没有报错，按要求打印所有全局变量和函数。
 *
 * 为了“最小改动”，所有 Project3 的新逻辑都写在这个文件里，
 * 其他框架类（AbstractCompiler / Grader / Type 接口等）保持不变。
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

    /**
     * 符号的种类：变量 or 函数。
     */
    private enum SymbolKind {
        VAR, FUNC
    }

    /**
     * 符号（Symbol）：表示一个名字对应的实体。
     *   - 对变量：name + VAR + type
     *   - 对函数：name + FUNC + 函数类型（返回值 + 参数类型列表）
     *
     * isDefined:
     *   - 对变量：一直为 true（见到就是定义）
     *   - 对函数：false 表示“只有声明”；true 表示“有函数体的定义”
     *
     * identNode：
     *   - 记录这个符号定义位置的 Identifier 节点，方便报错。
     */
    private static class Symbol {
        final String name;
        final SymbolKind kind;
        Type type;
        boolean isDefined;
        final TerminalNode identNode;

        Symbol(String name, SymbolKind kind, Type type, boolean isDefined, TerminalNode identNode) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.isDefined = isDefined;
            this.identNode = identNode;
        }
    }

    /**
     * 作用域（Scope）：
     *   - 每个 Scope 记录当前这一层里定义过的符号（symbols）。
     *   - parent 指向外层作用域。
     *   - lookupLocal：只在当前层查找（判断“重定义”时用）。
     *   - lookup：从当前向外一层层找（判断“是否声明过”时用）。
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

    /**
     * 简单类型实现：int / char / 数组 / 指针 / 结构体 / 函数。
     * 这些类实现 Type 接口，负责告诉我们类型的打印方式。
     */

    /** int 类型 */
    private static class IntType implements Type {
        @Override
        public String prettyPrint() {
            return "int";
        }
    }

    /** char 类型 */
    private static class CharType implements Type {
        @Override
        public String prettyPrint() {
            return "char";
        }
    }

    /**
     * 数组类型：elementType[size]
     * 例如 int[10]、char[5]。
     */
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
    }

    /**
     * 指针类型：R*
     * 例如 int*、struct s0*。
     */
    private static class PointerType implements Type {
        final Type base;

        PointerType(Type base) {
            this.base = base;
        }

        @Override
        public String prettyPrint() {
            return base.prettyPrint() + "*";
        }
    }

    /**
     * 结构体类型：struct tag{fieldType fieldName;...}
     *
     * isComplete:
     *   - false：只有 tag 声明（struct qwq;），成员未知
     *   - true：有完整定义（struct qwq { ... };）
     *
     * fullPrint：
     *   - 不完整：只打印 "struct name"
     *   - 完整：打印 "struct name{...;}"，用于变量 / typedef 的输出
     */
    private static class StructType implements Type {
        final String tag;
        final LinkedHashMap<String, Type> fields = new LinkedHashMap<>();
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
            if (!isComplete || fields.isEmpty()) {
                return prettyPrint();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("struct ").append(tag).append('{');
            boolean first = true;
            for (Map.Entry<String, Type> e : fields.entrySet()) {
                if (!first) {
                    sb.append(';');
                }
                first = false;
                sb.append(e.getValue().prettyPrint())
                        .append(' ')
                        .append(e.getKey());
            }
            sb.append(";}");
            return sb.toString();
        }
    }

    /**
     * 函数类型：returnType(paramType1,paramType2,...)
     * 例如：int(int,int[1])
     */
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
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(paramTypes.get(i).prettyPrint());
            }
            sb.append(')');
            return sb.toString();
        }
    }

    /**
     * 语义分析 Visitor：
     *   - 继承 ANTLR 生成的 SplcBaseVisitor<Void>。
     *   - 在 visitXXX 方法里：
     *       * 构建符号表（Scope 嵌套）
     *       * 检测语义错误（Undeclared / Redeclaration / Redefinition / IncompleteType）
     *       * 收集全局变量 & 函数，供最后输出使用。
     */
    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
        /**
         * 保存“文件级作用域”中的所有符号（只包含全局变量/函数），
         * 按插入顺序记录，便于最后输出时保持顺序。
         */
        final LinkedHashMap<String, Symbol> globalSymbols = new LinkedHashMap<>();

        // 作用域栈，栈底是 fileScope（文件级作用域）
        private final Deque<Scope> scopeStack = new ArrayDeque<>();
        private final Scope fileScope;

        /**
         * struct tag 信息：记录某个 tag 对应的 StructType，以及是否已有完整定义。
         */
        private static class StructInfo {
            final String tag;
            final StructType type;
            boolean isDefined;

            StructInfo(String tag, StructType type, boolean isDefined) {
                this.tag = tag;
                this.type = type;
                this.isDefined = isDefined;
            }
        }

        // struct tag 命名空间（file-scope）
        private final Map<String, StructInfo> structTable = new LinkedHashMap<>();

        // 对于全局的“可能依赖之后定义的 struct 变量”，先记录起来，最后统一检查是否仍然 incomplete
        private final List<Symbol> pendingIncompleteGlobals = new ArrayList<>();

        SemanticAnalyzer() {
            // 顶层作用域：全局（文件级）
            fileScope = new Scope(null);
            scopeStack.push(fileScope);
        }

        /** 当前所在的作用域 */
        private Scope currentScope() {
            return scopeStack.peek();
        }

        /** 新建一个子作用域并压栈（进入函数体 / block 时） */
        private void enterScope() {
            scopeStack.push(new Scope(currentScope()));
        }

        /** 退出当前作用域（离开函数体 / block 时） */
        private void exitScope() {
            scopeStack.pop();
        }

        // --------- 符号表操作工具函数 ---------

        /**
         * 判断某个类型是否“不完整的 struct 类型”（包括数组元素为不完整 struct 的情况）。
         *
         * 注意：
         *   - pointer type 总是 complete（即使指向不完整 struct）。
         *   - array type 的 element type 必须 complete，否则算 Definition of incomplete type。
         */
        private boolean isIncompleteStructType(Type t) {
            if (t instanceof StructType) {
                return !((StructType) t).isComplete;
            }
            if (t instanceof ArrayType) {
                ArrayType at = (ArrayType) t;
                return isIncompleteStructType(at.elementType);
            }
            // pointer / int / char / function 等都视作 complete
            return false;
        }

        /**
         * 处理“变量定义”：
         *   - isGlobal = true  表示在 fileScope 中定义（全局变量）
         *   - isGlobal = false 表示在当前局部作用域中定义（局部变量或参数）
         *
         * 主要检查：
         *   - 同一作用域内的重名定义（Redefinition）
         *   - 全局变量名与全局函数名冲突（Redeclaration）
         *   - Definition of incomplete type（incomplete struct / array-of-incomplete-struct）
         */
        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();

            // 先做 incomplete type 的检查:
            // 1) 如果是数组，且元素是 incomplete struct -> 立即报 Definition of incomplete type
            if (type instanceof ArrayType) {
                ArrayType at = (ArrayType) type;
                if (isIncompleteStructType(at.elementType)) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                    return;
                }
            }

            // 2) 非数组情况下：
            //    - 局部变量使用 incomplete struct -> 立即报错
            //    - 全局变量允许“暂时”是 incomplete struct，稍后再统一检查
            if (!(type instanceof ArrayType) && isIncompleteStructType(type)) {
                if (!isGlobal) {
                    // 局部变量不允许使用不完整 struct 类型
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                    return;
                }
                // 全局 + 不完整 struct：先允许，记到 pendingIncompleteGlobals 里，最后统一检查
            }

            if (cur == fileScope) {
                // ======= 全局变量 =======
                Symbol existing = fileScope.lookupLocal(name);
                if (existing != null) {
                    if (existing.kind == SymbolKind.VAR) {
                        // 同一个 file scope 里已经有同名变量 -> 重定义
                        grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    } else {
                        // 已有同名函数 -> 变量和函数名冲突
                        grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                    }
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true, identNode);
                fileScope.symbols.put(name, sym);
                // globalSymbols 用于最后打印，只记录一次
                globalSymbols.putIfAbsent(name, sym);

                // 如果是“全局 + 不完整 struct”，后面要统一检查
                if (!(type instanceof ArrayType) && isIncompleteStructType(type)) {
                    pendingIncompleteGlobals.add(sym);
                }
            } else {
                // ======= 局部变量 or 参数 =======
                Symbol existing = cur.lookupLocal(name);
                if (existing != null) {
                    // 同一个 block 内重复定义（参数与局部之间的冲突也算）
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true, identNode);
                cur.symbols.put(name, sym);
            }
        }

        /**
         * 处理“函数声明 / 定义”。
         *
         * isDefinition = false  -> 函数声明（没有函数体）
         * isDefinition = true   -> 函数定义（有函数体）
         *
         * 检查规则：
         *   - 如果原来是变量：
         *       * 变量 + 函数声明（int c; int c();）-> Redeclaration（示例 5）
         *       * 变量 + 函数定义（int z; int z() {}）-> Redefinition（示例 3）
         *   - 如果都是函数：
         *       * 已有声明，后面再来一个声明 -> Redeclaration
         *       * 已有定义，后面再来一个定义 -> Redefinition
         *       * 先声明再第一次定义 -> 合法，更新 isDefined = true
         */
        private void declareOrDefineFunction(TerminalNode identNode,
                                             FunctionType funcType,
                                             boolean isDefinition) {
            String name = identNode.getText();
            Symbol existing = fileScope.lookupLocal(name);

            if (existing == null) {
                // 第一次在文件级作用域出现这个名字
                Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition, identNode);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);
                return;
            }

            if (existing.kind == SymbolKind.VAR) {
                // 已经有变量，再出现同名函数
                if (isDefinition) {
                    // 示例 3: int z; int z() {} -> Redefinition
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                } else {
                    // 示例 5: int c; int c(); -> Redeclaration
                    grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                }
                return;
            }

            // 接下来 existing.kind == FUNC 的情况

            if (!isDefinition) {
                // 已经有函数符号，又来一个声明（不区分之前是否已定义） -> Redeclaration
                grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                return;
            }

            if (existing.isDefined) {
                // 已经有定义，又来一个定义 -> Redefinition
                grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                return;
            }

            // 原来只有声明，现在第一次定义 -> 合法
            existing.isDefined = true;
            existing.type = funcType;
        }

        /** 在当前作用域链中查找某名字对应的符号（包含外层作用域） */
        private Symbol lookup(String name) {
            return currentScope().lookup(name);
        }

        /**
         * 检查 Identifier 是否已经声明过。
         * 任何地方使用到 Identifier（变量 / 函数调用），都会走到这里。
         */
        private void checkUndeclaredUse(TerminalNode identNode) {
            String name = identNode.getText();
            Symbol sym = lookup(name);
            if (sym == null) {
                grader.reportSemanticError(Project3SemanticError.undeclaredUse(identNode));
            }
        }

        // --------- 类型构造工具函数 ---------

        /**
         * 获取最底层的“基本类型”（int / char / struct）。
         * struct：
         *   - struct Identifier;               -> 不完整 struct 类型
         *   - struct Identifier {...};        -> 完整 struct 类型（同时填充 structTable）
         */
        private Type getBaseType(SpecifierContext specCtx) {
            // specifier
            //   : INT                           # IntSpec
            //   | CHAR                          # CharSpec
            //   | STRUCT Identifier             # StructDeclSpec
            //   | STRUCT Identifier LBRACE (specifier varDec SEMI)* RBRACE  # FullStructSpec

            if (specCtx instanceof IntSpecContext) {
                return new IntType();
            }
            if (specCtx instanceof CharSpecContext) {
                return new CharType();
            }

            if (specCtx instanceof StructDeclSpecContext) {
                // 结构体不完整声明：struct T;
                StructDeclSpecContext ctx = (StructDeclSpecContext) specCtx;
                String tag = ctx.Identifier().getText();
                StructInfo info = structTable.get(tag);
                if (info == null) {
                    StructType t = new StructType(tag);
                    info = new StructInfo(tag, t, false);
                    structTable.put(tag, info);
                }
                return info.type;
            }

            if (specCtx instanceof FullStructSpecContext) {
                // 完整结构体定义：struct T { ... };
                FullStructSpecContext ctx = (FullStructSpecContext) specCtx;
                String tag = ctx.Identifier().getText();
                StructInfo info = structTable.get(tag);
                StructType structType;

                if (info == null) {
                    structType = new StructType(tag);
                    info = new StructInfo(tag, structType, true);
                    structTable.put(tag, info);
                } else {
                    structType = info.type;
                    if (info.isDefined) {
                        // 同一个 tag 多次完整定义 -> Redeclaration
                        grader.reportSemanticError(Project3SemanticError.redefinition(ctx.Identifier()));
                        // 返回已有类型，避免 NPE
                        return structType;
                    }
                    info.isDefined = true;
                }

                // 重新填充 structType 的字段
                structType.fields.clear();
                structType.isComplete = true;

                List<SpecifierContext> specs = ctx.specifier();
                List<VarDecContext> varDecs = ctx.varDec();
                for (int i = 0; i < specs.size(); i++) {
                    TerminalNode[] holder = new TerminalNode[1];
                    Type fieldType = buildType(specs.get(i), varDecs.get(i), holder);
                    TerminalNode fieldIdent = holder[0];
                    if (fieldIdent == null) {
                        continue;
                    }
                    String fname = fieldIdent.getText();

                    // 成员重名 -> 用 Redefinition 报错
                    if (structType.fields.containsKey(fname)) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(fieldIdent));
                        continue;
                    }

                    // 成员类型为 incomplete struct -> Definition of incomplete type
                    if (isIncompleteStructType(fieldType)) {
                        grader.reportSemanticError(Project3SemanticError.definitionIncomplete(fieldIdent));
                        // 仍然可以把它放进字段列表里，以防后续打印需要
                    }

                    structType.fields.put(fname, fieldType);
                }

                return structType;
            }

            // 理论上不会走到这里
            return new IntType();
        }

        /**
         * 把一个 varDec 和基本类型组合成“完整类型”，并顺便把变量名取出来。
         *
         * 例如：
         *   - specifier: int
         *   - varDec: a              -> int,  name = "a"
         *   - varDec: a[10][20]      -> int[10][20], name = "a"
         *   - varDec: *a[10]         -> int*[10]
         *   - varDec: (*a)[10]       -> int[10]*
         *
         * identHolder 用来“输出”变量名（因为 Java 里没有多返回值）。
         *
         * 这里对 pointer / array 的组合做了一个较为直观的实现，
         * 能够正确处理文档中给出的典型例子。
         */
        private Type buildDeclaratorType(Type baseType,
                                         VarDecContext varDecCtx,
                                         TerminalNode[] identHolder) {
            // grammar：
            // varDec
            //   : Identifier                        # SimpleVar
            //   | varDec LBRACK Number RBRACK      # ArrayVar
            //   | STAR varDec                      # PointerVar
            //   | LPAREN varDec RPAREN             # ParenVar

            if (varDecCtx instanceof SimpleVarContext) {
                SimpleVarContext ctx = (SimpleVarContext) varDecCtx;
                identHolder[0] = ctx.Identifier();
                return baseType;
            }

            if (varDecCtx instanceof ParenVarContext) {
                ParenVarContext ctx = (ParenVarContext) varDecCtx;
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }

            if (varDecCtx instanceof PointerVarContext) {
                PointerVarContext ctx = (PointerVarContext) varDecCtx;
                Type inner = buildDeclaratorType(baseType, ctx.varDec(), identHolder);
                return new PointerType(inner);
            }

            if (varDecCtx instanceof ArrayVarContext) {
                ArrayVarContext ctx = (ArrayVarContext) varDecCtx;

                Type elementType = buildDeclaratorType(baseType, ctx.varDec(), identHolder);

                int size;
                try {
                    size = Integer.parseInt(ctx.Number().getText());
                } catch (NumberFormatException e) {
                    size = -1;
                }

                if (size <= 0 && identHolder[0] != null) {
                    // 数组长度非法（<= 0）-> Definition of incomplete type
                    grader.reportSemanticError(
                            Project3SemanticError.definitionIncomplete(identHolder[0])
                    );
                    return elementType;
                }

                return new ArrayType(elementType, size);
            }

            // 理论上不会走到这里，兜底返回 baseType
            return baseType;
        }

        /**
         * 根据 specifier + varDec 组合出来的完整类型。
         */
        private Type buildType(SpecifierContext specCtx,
                               VarDecContext varDecCtx,
                               TerminalNode[] identHolder) {
            Type base = getBaseType(specCtx);
            return buildDeclaratorType(base, varDecCtx, identHolder);
        }

        /**
         * 把 funcArgs（参数列表）中的每个 (specifier varDec)
         * 转换为对应的参数 Type 列表。
         */
        private List<Type> buildFunctionParamTypes(FuncArgsContext argsCtx) {
            List<Type> paramTypes = new ArrayList<>();
            if (argsCtx == null) {
                return paramTypes;
            }
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
         * 专门用于检查“函数参数列表内部”的重定义（v3 新增示例要求）：
         *
         *   int main(int a, int a);              // Error: Redefinition of 'a'
         *   int main2(int a, char a[20]) { … }   // 参数内部重名 -> Redefinition
         *
         * 这里只检查参数之间是否重名，不把这些符号放入真正的作用域栈。
         */
        private void checkParamRedefinitionInFuncArgs(FuncArgsContext argsCtx) {
            if (argsCtx == null) {
                return;
            }

            // 临时 scope，只用于检查参数是否互相重名
            Scope paramScope = new Scope(null);

            List<SpecifierContext> specs = argsCtx.specifier();
            List<VarDecContext> varDecs = argsCtx.varDec();

            for (int i = 0; i < specs.size(); i++) {
                TerminalNode[] holder = new TerminalNode[1];
                Type paramType = buildType(specs.get(i), varDecs.get(i), holder);
                TerminalNode paramIdent = holder[0];
                if (paramIdent == null) {
                    continue;
                }

                String name = paramIdent.getText();
                if (paramScope.lookupLocal(name) != null) {
                    // 参数列表内部重名 -> Redefinition Error
                    grader.reportSemanticError(Project3SemanticError.redefinition(paramIdent));
                    return;
                }

                paramScope.symbols.put(name, new Symbol(name, SymbolKind.VAR, paramType, true, paramIdent));
            }
        }

        /**
         * 遍历完 program 后，检查所有“全局 + 不完整 struct”的变量，
         * 若此时仍然 incomplete，则报告 Definition of incomplete type。
         */
        private void checkPendingIncompleteGlobals() {
            for (Symbol sym : pendingIncompleteGlobals) {
                if (isIncompleteStructType(sym.type)) {
                    grader.reportSemanticError(
                            Project3SemanticError.definitionIncomplete(sym.identNode)
                    );
                }
            }
        }

        // --------- Visitor 具体实现 ---------

        /**
         * program: globalDef* EOF
         * 逐个访问每个 globalDef（函数定义 / 函数声明 / 全局变量定义 / struct 声明）。
         */
        @Override
        public Void visitProgram(ProgramContext ctx) {
            for (GlobalDefContext def : ctx.globalDef()) {
                visit(def);
            }
            // 所有声明 / 定义都处理完之后，再检查那些“可能依赖之后定义的 struct”的全局变量
            checkPendingIncompleteGlobals();
            return null;
        }

        /**
         * 函数定义：
         *   specifier Identifier LPAREN funcArgs RPAREN LBRACE statement* RBRACE
         *
         * 步骤：
         *   1. 构造函数类型（返回值 + 参数类型列表）。
         *   2. 在 fileScope 中登记“函数定义”（可能触发 Redeclaration / Redefinition）。
         *   3. 为函数体创建一个新作用域，把所有参数加入符号表。
         *   4. 依次 visit 函数体中的所有语句。
         */
        @Override
        public Void visitFuncDef(FuncDefContext ctx) {
            // 构造函数类型
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);

            // 在全局作用域登记这个函数（定义）
            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, true);

            // 进入函数体作用域
            enterScope();

            // 参数也视为函数体内部的变量定义
            FuncArgsContext argsCtx = ctx.funcArgs();
            if (argsCtx != null) {
                List<SpecifierContext> specs = argsCtx.specifier();
                List<VarDecContext> varDecs = argsCtx.varDec();
                for (int i = 0; i < specs.size(); i++) {
                    TerminalNode[] holder = new TerminalNode[1];
                    Type paramType = buildType(specs.get(i), varDecs.get(i), holder);
                    TerminalNode paramIdent = holder[0];
                    if (paramIdent != null) {
                        // isGlobal = false -> 局部（参数）
                        defineVariable(paramIdent, paramType, false);
                    }
                }
            }

            // 访问函数体中的每条语句
            for (StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }

            // 离开函数体作用域
            exitScope();
            return null;
        }

        /**
         * 函数声明：
         *   specifier Identifier LPAREN funcArgs RPAREN SEMI
         */
        @Override
        public Void visitFuncDecl(FuncDeclContext ctx) {
            // 先检查参数之间是否互相重名（只针对声明）
            checkParamRedefinitionInFuncArgs(ctx.funcArgs());

            // 构造函数类型（只有声明，没有函数体）
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);

            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, false);
            return null;
        }

        /**
         * 全局变量定义：
         *   specifier varDec SEMI
         */
        @Override
        public Void visitGlobalVarDef(GlobalVarDefContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];
            if (identNode != null) {
                defineVariable(identNode, varType, true);
            }
            return null;
        }

        /**
         * 全局 struct 声明：
         *   specifier SEMI （其中 specifier 是 struct 相关）
         *
         * 这里通过 getBaseType 来驱动：
         *   - struct T;                  -> 在 structTable 中登记不完整 struct
         *   - struct T { ... };          -> 完整定义 struct，检查成员错误 / 重定义
         */
        @Override
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
            getBaseType(ctx.specifier());
            return null;
        }

        /**
         * 语句块：
         *   '{' statement* '}'
         * 进入新作用域，访问所有子语句，退出作用域。
         */
        @Override
        public Void visitBlockStmt(BlockStmtContext ctx) {
            // 进入 block 作用域
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
            if (identNode != null) {
                defineVariable(identNode, varType, false);
            }
            // 如果有初始化表达式，也要继续访问（里面可能使用标识符）
            if (ctx.expression() != null) {
                visit(ctx.expression());
            }
            return null;
        }

        /**
         * 表达式节点：
         *   这里只做一件事：如果当前这个 expression 带有一个 Identifier，
         *   就认为它是一次“使用”，做未声明检查。
         *
         *   具体的运算优先级、类型匹配等高级检查，在本次 Project 中不需要做。
         */
        @Override
        public Void visitExpression(ExpressionContext ctx) {
            TerminalNode id = ctx.Identifier();
            if (id != null) {
                checkUndeclaredUse(id);
            }
            // 继续递归访问子表达式（比如数组下标里的表达式等）
            return visitChildren(ctx);
        }
    }
}

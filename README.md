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
     * 简单类型实现：int / char / 数组 / 函数。

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
     * 函数类型：returnType(paramType1,paramType2,...)
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
         * 处理“变量定义”：
         *   - isGlobal = true  表示在 fileScope 中定义（全局变量）
         *   - isGlobal = false 表示在当前局部作用域中定义（局部变量或参数）
         *
         * 主要检查：
         *   - 同一作用域内的重名定义（Redefinition）
         *   - 全局变量名与全局函数名冲突（Redeclaration）
         */
        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();

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
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
                fileScope.symbols.put(name, sym);
                // globalSymbols 用于最后打印，只记录一次
                globalSymbols.putIfAbsent(name, sym);
            } else {
                // ======= 局部变量 or 参数 =======
                Symbol existing = cur.lookupLocal(name);
                if (existing != null) {
                    // 同一个 block 内重复定义（参数与局部之间的冲突也算）
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
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
                Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);
                return;
            }

            if (existing.kind == SymbolKind.VAR) {
                // 已经有变量，再出现同名函数
                if (isDefinition) {
           
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                } else {
         
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

        /** 在当前作用域链中查找某名字对应的符号 */
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
         * 获取最底层的“基本类型”（int / char）。
         * 对 struct 相关的分支，这里统一返回 int 作为占位，
         */
        private Type getBaseType(SpecifierContext specCtx) {
            // grammar：
            // specifier
            //   : INT                           # IntSpec
            //   | CHAR                          # CharSpec
            //   | STRUCT Identifier             # StructDeclSpec
            //   | STRUCT Identifier LBRACE ...  # FullStructSpec
            if (specCtx instanceof IntSpecContext) {
                return new IntType();
            }
            if (specCtx instanceof CharSpecContext) {
                return new CharType();
            }

            // struct 相关在基础部分不处理：返回占位类型
            return new IntType();
        }

        /**
         * 把一个 varDec 和基本类型组合成“完整类型”，并顺便把变量名取出来。
         * identHolder 用来“输出”变量名（因为 Java 里没有多返回值）。
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

            // 1) 简单变量：只保存名字，不改变类型
            if (varDecCtx instanceof SimpleVarContext) {
                SimpleVarContext ctx = (SimpleVarContext) varDecCtx;
                identHolder[0] = ctx.Identifier();
                return baseType;
            }

            // 2) 数组：递归得到里层的元素类型，然后再包一层 ArrayType
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
                    return elementType; // 实际上不会执行到这里，前一行报错会终止
                }

                return new ArrayType(elementType, size);
            }

            // 3) 指针
            if (varDecCtx instanceof PointerVarContext) {
                PointerVarContext ctx = (PointerVarContext) varDecCtx;
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }

            // 4) 括号
            if (varDecCtx instanceof ParenVarContext) {
                ParenVarContext ctx = (ParenVarContext) varDecCtx;
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }

   
            return baseType;
        }

        /**
         *  specifier + varDec 
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

                paramScope.symbols.put(name, new Symbol(name, SymbolKind.VAR, paramType, true));
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
         *
         * 与函数定义类似，但不进入函数体，也不建立新的作用域。
         * 这里额外需要：
         *   - 对参数列表内部做一次“重定义”检查（v3 新增的 Redefinition 示例）。
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
         */
        @Override
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
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
         *   具体的运算优先级、类型匹配等高级检查，在 80 分基础要求中不需要做。
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

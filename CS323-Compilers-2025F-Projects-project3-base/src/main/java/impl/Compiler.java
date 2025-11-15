package impl;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import framework.lang.Type;
import framework.project3.Project3SemanticError;
import generated.Splc.SplcBaseVisitor;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
import generated.Splc.SplcParser.ProgramContext;
import generated.Splc.SplcParser.GlobalDefContext;
import generated.Splc.SplcParser.FuncDefContext;
import generated.Splc.SplcParser.FuncDeclContext;
import generated.Splc.SplcParser.GlobalVarDefContext;
import generated.Splc.SplcParser.GlobalStructDeclContext;
import generated.Splc.SplcParser.SpecifierContext;
import generated.Splc.SplcParser.VarDecContext;
import generated.Splc.SplcParser.FuncArgsContext;
import generated.Splc.SplcParser.StatementContext;
import generated.Splc.SplcParser.BlockStmtContext;
import generated.Splc.SplcParser.VarDecStmtContext;
import generated.Splc.SplcParser.ExpressionContext;
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
        // 1. 用 grader 的输入流构造 lexer & parser
        CharStream input = CharStreams.fromStream(grader.getSourceStream());
        SplcLexer lexer = new SplcLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SplcParser parser = new SplcParser(tokens);

        ProgramContext program = parser.program();

        // 2. 运行语义分析
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(program);

        // 3. 若没有语义错误，被测程序会继续到这里，打印全局变量 & 函数
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
    // 内部辅助类
    // =========================

    private enum SymbolKind {
        VAR, FUNC
    }

    /**
     * other 命名空间里的符号（变量 + 函数）。
     */
    private static class Symbol {
        final String name;
        final SymbolKind kind;
        Type type;
        boolean isDefined; // 函数：是否有定义（有函数体）；变量：一直为 true

        Symbol(String name, SymbolKind kind, Type type, boolean isDefined) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.isDefined = isDefined;
        }
    }

    /**
     * 作用域。
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
     * 简单类型实现：int / char / 数组 / 函数
     */
    private static class IntType implements Type {
        @Override
        public String prettyPrint() {
            return "int";
        }
    }

    private static class CharType implements Type {
        @Override
        public String prettyPrint() {
            return "char";
        }
    }

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
     *  - 建符号表（作用域）
     *  - 收集全局变量 & 函数用于最后输出
     */
    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
        // file scope 内的符号，按出现顺序记录
        final LinkedHashMap<String, Symbol> globalSymbols = new LinkedHashMap<>();

        // 作用域栈，底部是 file scope
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

        // --------- 符号管理工具函数 ---------

        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();

            if (cur == fileScope) {
                Symbol existing = fileScope.lookupLocal(name);
                if (existing != null) {
                    if (existing.kind == SymbolKind.VAR) {
                        // 同一 file scope 内变量被重复定义
                        grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    } else {
                        // file scope 中已经有同名函数
                        grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                    }
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true);
                fileScope.symbols.put(name, sym);
                // 记录输出顺序
                globalSymbols.putIfAbsent(name, sym);
            } else {
                // 局部变量 / 参数
                Symbol existing = cur.lookupLocal(name);
                if (existing != null) {
                    // 同一 block 作用域内重定义（包括参数 + 局部变量的冲突）
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

            // file scope 中已经有同名符号
            if (existing.kind == SymbolKind.VAR) {
                // 变量与函数同名
                grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                return;
            }

            // 有函数
            if (!isDefinition) {
                // 又来一个声明（无论之前是否已经定义） -> Redeclaration
                grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                return;
            }

            // 定义
            if (existing.isDefined) {
                // 多次定义 -> Redefinition
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

        // --------- 类型构造工具函数 ---------

        private Type getBaseType(SpecifierContext specCtx) {
            if (specCtx.INT() != null) {
                return new IntType();
            }
            if (specCtx.CHAR() != null) {
                return new CharType();
            }
            // struct /
            // 返回占位类型
            return new IntType();
        }

        private Type buildDeclaratorType(Type baseType,
                                         VarDecContext varDecCtx,
                                         TerminalNode[] identHolder) {
            if (varDecCtx.Identifier() != null) {
                identHolder[0] = varDecCtx.Identifier();
                return baseType;
            }

            // 数组：varDec '[' Number ']'
            if (varDecCtx.varDec() != null && varDecCtx.Number() != null) {
                Type elementType = buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
                int size;
                try {
                    size = Integer.parseInt(varDecCtx.Number().getText());
                } catch (NumberFormatException e) {
                    size = -1;
                }
                if (size <= 0 && identHolder[0] != null) {
                    // 数组长度非法 -> Definition of incomplete type
                    grader.reportSemanticError(
                            Project3SemanticError.definitionIncomplete(identHolder[0])
                    );
                    return elementType; 
                }
                return new ArrayType(elementType, size);
            }

            // 指针：'*' varDec
            if (varDecCtx.varDec() != null && varDecCtx.STAR() != null) {
                return buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
            }

            // 括号：'(' varDec ')'
            if (varDecCtx.varDec() != null && varDecCtx.LPAREN() != null) {
                return buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
            }

            // 兜底：直接返回 baseType
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



        @Override
        public Void visitProgram(ProgramContext ctx) {
            for (GlobalDefContext def : ctx.globalDef()) {
                visit(def);
            }
            return null;
        }

        @Override
        public Void visitFuncDef(FuncDefContext ctx) {
            // 构造函数类型
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);

            // 在 file scope 声明/定义函数
            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, true);

            // 进入函数体作用域
            enterScope();

            // 参数是函数体作用域内的定义
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

            // 访问函数体的语句
            for (StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }

            // 离开函数作用域
            exitScope();
            return null;
        }

        @Override
        public Void visitFuncDecl(FuncDeclContext ctx) {
            // 构造函数类型
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);

            // 仅声明，不进入新作用域，也不把参数名加到符号表
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
            }
            return null;
        }

        @Override
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
      
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

        @Override
        public Void visitVarDecStmt(VarDecStmtContext ctx) {
            TerminalNode[] holder = new TerminalNode[1];
            Type varType = buildType(ctx.specifier(), ctx.varDec(), holder);
            TerminalNode identNode = holder[0];
            if (identNode != null) {
                // 局部变量定义
                defineVariable(identNode, varType, false);
            }
            // 如果有初始化表达式，递归访问表达式（会检查未声明使用）
            if (ctx.expression() != null) {
                visit(ctx.expression());
            }
            return null;
        }

        @Override
        public Void visitExpression(ExpressionContext ctx) {
            // 标识符使用（变量、函数调用等）
            if (ctx.Identifier() != null) {
                // 区分函数调用 "Identifier(" 与简单标识符
                if (ctx.LPAREN() != null) {
                    // 函数调用
                    checkUndeclaredUse(ctx.Identifier());
                } else if (ctx.getChildCount() == 1) {
                    // 简单变量/常量引用
                    checkUndeclaredUse(ctx.Identifier());
                }
            }

            return visitChildren(ctx);
        }
    }
}

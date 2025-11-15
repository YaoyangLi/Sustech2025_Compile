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
    // 1. 从 grader 的输入流创建 ANTLR 的 lexer 和 parser
    CharStream input = CharStreams.fromStream(grader.getSourceStream());
    SplcLexer lexer = new SplcLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    SplcParser parser = new SplcParser(tokens);

    ProgramContext program = parser.program();

    // 2. 运行语义分析
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    analyzer.visit(program);

    // 3. 如果没有语义错误，打印全局变量和函数
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

private enum SymbolKind {
    VAR, FUNC
}

/**
 * 符号：表示 other 命名空间里的变量或函数。
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
 * 简单类型实现：int / char / 数组 / 函数。
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
 *  - 做 80 分要求的语义检查
 *  - 收集全局变量 & 函数用于最后输出
 */
private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
    // file scope 内的符号（按出现顺序记录，用于输出）
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

    // --------- 符号表操作工具函数 ---------

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
            // 局部变量或参数
            Symbol existing = cur.lookupLocal(name);
            if (existing != null) {
                // 同一 block 内重定义（包括参数和局部变量冲突）
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
            // 第一次在 file scope 见到这个名字
            Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition);
            fileScope.symbols.put(name, sym);
            globalSymbols.putIfAbsent(name, sym);
            return;
        }

        if (existing.kind == SymbolKind.VAR) {
            // 变量和函数同名
            grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
            return;
        }

        // 已经有函数符号了
        if (!isDefinition) {
            // 再来一个声明，无论以前是声明还是定义 -> Redeclaration
            grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
            return;
        }

        // 这是定义
        if (existing.isDefined) {
            // 多次定义
            grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
            return;
        }

        // 之前是声明，现在是第一次定义 -> 合法
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

        // struct 相关先不做（80 分基础不要求），返回占位类型
        return new IntType();
    }

    private Type buildDeclaratorType(Type baseType,
                                     VarDecContext varDecCtx,
                                     TerminalNode[] identHolder) {
        // grammar：
        // varDec
        //   : Identifier                        # SimpleVar
        //   | varDec LBRACK Number RBRACK      # ArrayVar
        //   | STAR varDec                      # PointerVar
        //   | LPAREN varDec RPAREN             # ParenVar

        // 1) 简单变量
        if (varDecCtx instanceof SimpleVarContext) {
            SimpleVarContext ctx = (SimpleVarContext) varDecCtx;
            identHolder[0] = ctx.Identifier();
            return baseType;
        }

        // 2) 数组
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
                // 数组长度非法 -> Definition of incomplete type
                grader.reportSemanticError(
                        Project3SemanticError.definitionIncomplete(identHolder[0])
                );
                return elementType; // 理论上不会执行到这里
            }

            return new ArrayType(elementType, size);
        }

        // 3) 指针（80 分不做指针语义，直接往里递归）
        if (varDecCtx instanceof PointerVarContext) {
            PointerVarContext ctx = (PointerVarContext) varDecCtx;
            return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
        }

        // 4) 括号
        if (varDecCtx instanceof ParenVarContext) {
            ParenVarContext ctx = (ParenVarContext) varDecCtx;
            return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
        }

        // 兜底
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

    // --------- Visitor 覆盖 ---------

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

        // 在 file scope 里声明/定义函数
        TerminalNode identNode = ctx.Identifier();
        declareOrDefineFunction(identNode, funcType, true);

        // 进入函数体作用域
        enterScope();

        // 参数是函数体作用域内的变量定义
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

        // 访问函数体语句
        for (StatementContext stmt : ctx.statement()) {
            visit(stmt);
        }

        // 离开函数体作用域
        exitScope();
        return null;
    }

    @Override
    public Void visitFuncDecl(FuncDeclContext ctx) {
        // 构造函数类型（只有声明，没有函数体）
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
        }
        return null;
    }

    @Override
    public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
        // struct 的扩展在 80 分要求中不做语义检查，这里什么也不做
        return null;
    }

    @Override
    public Void visitBlockStmt(BlockStmtContext ctx) {
        // 进入 block scope
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
            defineVariable(identNode, varType, false);
        }
        if (ctx.expression() != null) {
            visit(ctx.expression());
        }
        return null;
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {
        // 对于出现的所有 Identifier，都视为一次“使用”，进行未声明检查
        TerminalNode id =ctx.Identifier();
        if(id!= null){
            checkUndeclaredUse(id);
        }
        return visitChildren(ctx);
    }
}

}

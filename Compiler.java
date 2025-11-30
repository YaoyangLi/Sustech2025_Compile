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
 */
public class Compiler extends AbstractCompiler {

    public Compiler(AbstractGrader grader) {
        super(grader);
    }

    @Override
    public void start() throws IOException {
        CharStream input = CharStreams.fromStream(grader.getSourceStream());
        SplcLexer lexer = new SplcLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SplcParser parser = new SplcParser(tokens);

        ProgramContext program = parser.program();

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(program);

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

    private enum SymbolKind {
        VAR, FUNC
    }

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

    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
        final LinkedHashMap<String, Symbol> globalSymbols = new LinkedHashMap<>();

        private final Deque<Scope> scopeStack = new ArrayDeque<>();
        private final Scope fileScope;

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

        private final Map<String, StructInfo> structTable = new LinkedHashMap<>();
        private final List<Symbol> pendingIncompleteGlobals = new ArrayList<>();

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

        private boolean isIncompleteStructType(Type t) {
            if (t instanceof StructType) {
                return !((StructType) t).isComplete;
            }
            if (t instanceof ArrayType) {
                ArrayType at = (ArrayType) t;
                return isIncompleteStructType(at.elementType);
            }
            return false;
        }

        private void defineVariable(TerminalNode identNode, Type type, boolean isGlobal) {
            String name = identNode.getText();
            Scope cur = currentScope();

            if (type instanceof ArrayType) {
                ArrayType at = (ArrayType) type;
                if (isIncompleteStructType(at.elementType)) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                    return;
                }
            }

            if (!(type instanceof ArrayType) && isIncompleteStructType(type)) {
                if (!isGlobal) {
                    grader.reportSemanticError(Project3SemanticError.definitionIncomplete(identNode));
                    return;
                }
            }

            if (cur == fileScope) {
                Symbol existing = fileScope.lookupLocal(name);
                if (existing != null) {
                    if (existing.kind == SymbolKind.VAR) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    } else {
                        grader.reportSemanticError(Project3SemanticError.redeclaration(identNode));
                    }
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true, identNode);
                fileScope.symbols.put(name, sym);
                globalSymbols.putIfAbsent(name, sym);

                if (!(type instanceof ArrayType) && isIncompleteStructType(type)) {
                    pendingIncompleteGlobals.add(sym);
                }
            } else {
                Symbol existing = cur.lookupLocal(name);
                if (existing != null) {
                    grader.reportSemanticError(Project3SemanticError.redefinition(identNode));
                    return;
                }
                Symbol sym = new Symbol(name, SymbolKind.VAR, type, true, identNode);
                cur.symbols.put(name, sym);
            }
        }

        private void declareOrDefineFunction(TerminalNode identNode,
                                             FunctionType funcType,
                                             boolean isDefinition) {
            String name = identNode.getText();
            Symbol existing = fileScope.lookupLocal(name);

            if (existing == null) {
                Symbol sym = new Symbol(name, SymbolKind.FUNC, funcType, isDefinition, identNode);
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

        private Type getBaseType(SpecifierContext specCtx) {
            if (specCtx instanceof IntSpecContext) {
                return new IntType();
            }
            if (specCtx instanceof CharSpecContext) {
                return new CharType();
            }

            if (specCtx instanceof StructDeclSpecContext) {
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
                        grader.reportSemanticError(Project3SemanticError.redefinition(ctx.Identifier()));
                        return structType;
                    }
                    info.isDefined = true;
                }

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

                    if (structType.fields.containsKey(fname)) {
                        grader.reportSemanticError(Project3SemanticError.redefinition(fieldIdent));
                        continue;
                    }

                    if (isIncompleteStructType(fieldType)) {
                        grader.reportSemanticError(Project3SemanticError.definitionIncomplete(fieldIdent));
                    }

                    structType.fields.put(fname, fieldType);
                }

                return structType;
            }

            return new IntType();
        }

        /**
         * 把一个 varDec 和基本类型组合成“完整类型”，并顺便把变量名取出来。
         *
         * 为了满足文档中指针和数组的优先级（[] 高于 *），这里对几种典型形状做了特殊处理：
         *
         *   1) *v[123]        -> int*[123]      （数组元素是指针）
         *   2) (*v)[123]      -> int[123]*      （指向数组的指针）
         *
         * 其他组合仍然按递归规则处理。
         */
        private Type buildDeclaratorType(Type baseType,
                                         VarDecContext varDecCtx,
                                         TerminalNode[] identHolder) {
            if (varDecCtx instanceof SimpleVarContext) {
                SimpleVarContext ctx = (SimpleVarContext) varDecCtx;
                identHolder[0] = ctx.Identifier();
                return baseType;
            }

            if (varDecCtx instanceof ParenVarContext) {
                ParenVarContext ctx = (ParenVarContext) varDecCtx;
                return buildDeclaratorType(baseType, ctx.varDec(), identHolder);
            }

            // 特殊形状 2：(*v)[N]  => Pointer( Array(baseType, N) )
            if (varDecCtx instanceof ArrayVarContext) {
                ArrayVarContext ctx = (ArrayVarContext) varDecCtx;
                int size;
                try {
                    size = Integer.parseInt(ctx.Number().getText());
                } catch (NumberFormatException e) {
                    size = -1;
                }

                VarDecContext inner = ctx.varDec();

                // 识别形状：ArrayVar( ParenVar( PointerVar( SimpleVar ) ) )
                if (inner instanceof ParenVarContext) {
                    VarDecContext insideParen = ((ParenVarContext) inner).varDec();
                    if (insideParen instanceof PointerVarContext) {
                        PointerVarContext pvc = (PointerVarContext) insideParen;
                        if (pvc.varDec() instanceof SimpleVarContext) {
                            SimpleVarContext sv = (SimpleVarContext) pvc.varDec();
                            identHolder[0] = sv.Identifier();

                            if (size <= 0 && identHolder[0] != null) {
                                grader.reportSemanticError(
                                        Project3SemanticError.definitionIncomplete(identHolder[0])
                                );
                                return baseType;
                            }

                            Type arrayType = new ArrayType(baseType, size);
                            return new PointerType(arrayType);
                        }
                    }
                }

                // 默认：数组类型，元素类型按 inner 递归
                Type elementType = buildDeclaratorType(baseType, inner, identHolder);

                if (size <= 0 && identHolder[0] != null) {
                    grader.reportSemanticError(
                            Project3SemanticError.definitionIncomplete(identHolder[0])
                    );
                    return elementType;
                }

                return new ArrayType(elementType, size);
            }

            // 特殊形状 1：*v[N]  => Array( Pointer(baseType), N )
            if (varDecCtx instanceof PointerVarContext) {
                PointerVarContext ctx = (PointerVarContext) varDecCtx;
                VarDecContext child = ctx.varDec();

                if (child instanceof ArrayVarContext) {
                    ArrayVarContext actx = (ArrayVarContext) child;

                    int size;
                    try {
                        size = Integer.parseInt(actx.Number().getText());
                    } catch (NumberFormatException e) {
                        size = -1;
                    }

                    // 先构造“元素”的类型（不含这个 *）
                    Type elementBase = buildDeclaratorType(baseType, actx.varDec(), identHolder);
                    Type elementPtr = new PointerType(elementBase);

                    if (size <= 0 && identHolder[0] != null) {
                        grader.reportSemanticError(
                                Project3SemanticError.definitionIncomplete(identHolder[0])
                        );
                        return elementBase;
                    }

                    return new ArrayType(elementPtr, size);
                }

                // 其他情况：普通 PointerType 包住 child 的类型
                Type inner = buildDeclaratorType(baseType, child, identHolder);
                return new PointerType(inner);
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

        private void checkParamRedefinitionInFuncArgs(FuncArgsContext argsCtx) {
            if (argsCtx == null) {
                return;
            }

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
                    grader.reportSemanticError(Project3SemanticError.redefinition(paramIdent));
                    return;
                }

                paramScope.symbols.put(name, new Symbol(name, SymbolKind.VAR, paramType, true, paramIdent));
            }
        }

        private void checkPendingIncompleteGlobals() {
            for (Symbol sym : pendingIncompleteGlobals) {
                if (isIncompleteStructType(sym.type)) {
                    grader.reportSemanticError(
                            Project3SemanticError.definitionIncomplete(sym.identNode)
                    );
                }
            }
        }

        @Override
        public Void visitProgram(ProgramContext ctx) {
            for (GlobalDefContext def : ctx.globalDef()) {
                visit(def);
            }
            checkPendingIncompleteGlobals();
            return null;
        }

        @Override
        public Void visitFuncDef(FuncDefContext ctx) {
            Type returnType = getBaseType(ctx.specifier());
            List<Type> paramTypes = buildFunctionParamTypes(ctx.funcArgs());
            FunctionType funcType = new FunctionType(returnType, paramTypes);

            TerminalNode identNode = ctx.Identifier();
            declareOrDefineFunction(identNode, funcType, true);

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
            TerminalNode id = ctx.Identifier();
            if (id != null) {
                checkUndeclaredUse(id);
            }
            return visitChildren(ctx);
        }
    }
}

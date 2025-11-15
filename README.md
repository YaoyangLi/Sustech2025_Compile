可以，咱们先不管什么“扩展分”，先搞清楚在 **虚拟机里的 VSCode 上，怎么把现在的 Project3 编译 + 跑起来**。我按你现在的状态一步步来讲（只刚安装好 VSCode）。

> 下面用的是一个**不依赖 Makefile 的通用方式**：直接 `javac` + `java`。只要你的目录结构是之前发的那样，就能用。

---

## 0. 前置：确认环境

在虚拟机 Linux 里打开一个终端（可以用 VSCode 自带 Terminal）：

```bash
java -version
javac -version
```

* 如果两个命令都能输出版本号，就说明 JDK 已经装好了，可以跳过安装。
* 如果提示 command not found，可以装一个默认的 JDK（以 Ubuntu/Debian 为例）：

```bash
sudo apt update
sudo apt install default-jdk
```

---

## 1. 在 VSCode 里打开工程目录

1. 在虚拟机里，把你的项目（比如 `CS323-Compilers-2025F-Projects-project3-base`）放在某个目录下。
2. 打开 VSCode → 左上角 **File → Open Folder...**
   选中这个项目目录（根目录，有 `src/`、`Makefile`、`testcases/` 那个）。
3. 打开之后，你应该在左边能看到类似：

   ```
   src/
     main/
       java/
         framework/
         impl/
         Main.java
   testcases/
     project3/
       ok_01.splc
       ...
   ```

---

## 2. 用 VSCode 终端编译所有 Java 文件

1. 在 VSCode 顶部菜单：**Terminal → New Terminal**（新建终端）

2. 确认当前路径是在项目根目录，比如：

   ```bash
   pwd
   # 输出类似 /home/xxx/CS323-Compilers-2025F-Projects-project3-base
   ```

3. 编译所有源码到一个 `out` 目录（建议这样做）：

   ```bash
   mkdir -p out

   javac -cp libs/antlr-4.13.2-complete.jar \
         -d out \
         $(find src/main/java -name "*.java")
   ```

解释一下：

* `-cp libs/antlr-4.13.2-complete.jar`：把老师给的 antlr jar 加到 classpath，编译器才能找到 `generated.Splc.*` 这些类。
* `-d out`：所有 `.class` 文件输出到 `out` 目录（比如 `out/framework/...`、`out/impl/...`、`out/Main.class`）
* `find src/main/java -name "*.java"`：把 `src/main/java` 下所有 Java 文件全部编进来（包括你改过的 `Compiler.java`）。

如果这一步没有报错，说明代码至少可以编译通过了 ✅

---

## 3. 运行当前的 `Main`（默认跑 ok_01.splc）

你现在的 `Main.java` 内容是：

```java
public class Main {
    public static void main(String[] args) throws IOException {
        {
            InputStream input = new FileInputStream("testcases/project3/ok_01.splc");
            AbstractGrader grader = new Grader(input, System.out);
            grader.run();
        }
    }
}
```

也就是说：
**它硬编码了 `testcases/project3/ok_01.splc` 这个文件。**

从项目根目录运行：

```bash
java -cp "out:libs/antlr-4.13.2-complete.jar" Main
```

注意：

* `-cp "out:libs/antlr-4.13.2-complete.jar"`

  * `out`：你刚才编译出来的 `.class` 目录
  * `libs/antlr-4.13.2-complete.jar`：antlr 的 jar
  * Linux 下 classpath 用 `:` 分隔，Windows 下是 `;`，但你现在在 Linux 虚拟机里，所以就是 `:`

如果一切正常，你会看到 `Grader` 的输出：

* 要么是一些 **错误信息**（如果你故意让语义错了）
* 要么是：

  ```text
  Variables:
  ...

  Functions:
  ...
  ```

之类的结果。

---

## 4. 测试不同的样例（80 分相关的 testcases）

### 简单方式：改 Main 里的文件名

你可以临时改 `Main.java`：

```java
InputStream input = new FileInputStream("testcases/project3/ok_02.splc");
```

或：

```java
InputStream input = new FileInputStream("testcases/project3/error_01.splc");
```

然后重新编译 + 运行：

```bash
# 重新编译（每次改了 .java 都要重新编译一次）
javac -cp libs/antlr-4.13.2-complete.jar -d out $(find src/main/java -name "*.java")

# 再运行
java -cp "out:libs/antlr-4.13.2-complete.jar" Main
```

这样你就可以一个一个手动测试 testcases/project3 里的用例。

---

## 5. 如果你想更方便一点（可选）

你也可以把 `Main` 改成支持命令行参数：

```java
public class Main {
    public static void main(String[] args) throws IOException {
        String path;
        if (args.length > 0) {
            path = args[0];
        } else {
            path = "testcases/project3/ok_01.splc";
        }
        InputStream input = new FileInputStream(path);
        AbstractGrader grader = new Grader(input, System.out);
        grader.run();
    }
}
```

然后你可以在终端中这样测试不同文件：

```bash
# 编译（同上）
javac -cp libs/antlr-4.13.2-complete.jar -d out $(find src/main/java -name "*.java")

# 测一个“正确样例”
java -cp "out:libs/antlr-4.13.2-complete.jar" Main testcases/project3/ok_01.splc

# 再测一个“语义错误样例”
java -cp "out:libs/antlr-4.13.2-complete.jar" Main testcases/project3/error_03.splc
```

---

## 6. 这几步跟“80 分要求”的关系

* **80 分要求**主要是：

  * 类型系统（int/char/数组/函数）
  * 符号表 + 作用域
  * 5 类语义错误中的基础部分
* **怎么测试自己做对了？**

  * 跑老师给的 `testcases/project3` 目录下的：

    * `ok_*.splc`：应该不报错，最后打印 Variables / Functions
    * `error_*.splc`：应该在某个地方触发相应的 `Project3SemanticError`，输出对应错误信息（而且往往只报第一个错误就退出）

现在你已经有了：

1. 编译命令
2. 运行命令
3. 手动切换测试文件的方法

后面你就可以边改 `Compiler.java`，边编译 + 运行这些用例，来调试你自己的语义分析。

---

如果你愿意，你可以把你跑某个 `ok_xx.splc` 或 `error_xx.splc` 时的终端输出贴给我，我可以帮你一起对照一下：

* 这个输出是不是和 80 分设计的行为一致
* 出现的错误是你代码逻辑的问题，还是测试用例本身暴露出的语义点你还没实现。


cs323@deb-cs323-compilers:~/Desktop/Project3/Sustech2025_Compile-project3/CS323-Compilers-2025F-Projects-project3-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
src/main/java/framework/project2/Grader.java:11: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcBaseVisitor;
                     ^
src/main/java/framework/project2/Grader.java:12: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcLexer;
                     ^
src/main/java/framework/project2/Grader.java:13: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcParser;
                     ^
src/main/java/impl/Compiler.java:7: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcBaseVisitor;
                     ^
src/main/java/impl/Compiler.java:8: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcLexer;
                     ^
src/main/java/impl/Compiler.java:9: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcParser;
                     ^
src/main/java/impl/Compiler.java:10: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.ProgramContext;
                                ^
src/main/java/impl/Compiler.java:11: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.GlobalDefContext;
                                ^
src/main/java/impl/Compiler.java:12: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.FuncDefContext;
                                ^
src/main/java/impl/Compiler.java:13: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.FuncDeclContext;
                                ^
src/main/java/impl/Compiler.java:14: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.GlobalVarDefContext;
                                ^
src/main/java/impl/Compiler.java:15: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.GlobalStructDeclContext;
                                ^
src/main/java/impl/Compiler.java:16: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.SpecifierContext;
                                ^
src/main/java/impl/Compiler.java:17: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.VarDecContext;
                                ^
src/main/java/impl/Compiler.java:18: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.FuncArgsContext;
                                ^
src/main/java/impl/Compiler.java:19: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.StatementContext;
                                ^
src/main/java/impl/Compiler.java:20: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.BlockStmtContext;
                                ^
src/main/java/impl/Compiler.java:21: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.VarDecStmtContext;
                                ^
src/main/java/impl/Compiler.java:22: 错误: 程序包generated.Splc.SplcParser不存在
import generated.Splc.SplcParser.ExpressionContext;
                                ^
src/main/java/impl/Compiler.java:186: 错误: 找不到符号
    private class SemanticAnalyzer extends SplcBaseVisitor<Void> {
                                           ^
  符号:   类 SplcBaseVisitor
  位置: 类 Compiler
src/main/java/impl/Compiler.java:299: 错误: 找不到符号
        private Type getBaseType(SpecifierContext specCtx) {
                                 ^
  符号:   类 SpecifierContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:312: 错误: 找不到符号
                                         VarDecContext varDecCtx,
                                         ^
  符号:   类 VarDecContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:352: 错误: 找不到符号
        private Type buildType(SpecifierContext specCtx,
                               ^
  符号:   类 SpecifierContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:353: 错误: 找不到符号
                               VarDecContext varDecCtx,
                               ^
  符号:   类 VarDecContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:359: 错误: 找不到符号
        private List<Type> buildFunctionParamTypes(FuncArgsContext argsCtx) {
                                                   ^
  符号:   类 FuncArgsContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:377: 错误: 找不到符号
        public Void visitProgram(ProgramContext ctx) {
                                 ^
  符号:   类 ProgramContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:385: 错误: 找不到符号
        public Void visitFuncDef(FuncDefContext ctx) {
                                 ^
  符号:   类 FuncDefContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:424: 错误: 找不到符号
        public Void visitFuncDecl(FuncDeclContext ctx) {
                                  ^
  符号:   类 FuncDeclContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:437: 错误: 找不到符号
        public Void visitGlobalVarDef(GlobalVarDefContext ctx) {
                                      ^
  符号:   类 GlobalVarDefContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:448: 错误: 找不到符号
        public Void visitGlobalStructDecl(GlobalStructDeclContext ctx) {
                                          ^
  符号:   类 GlobalStructDeclContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:454: 错误: 找不到符号
        public Void visitBlockStmt(BlockStmtContext ctx) {
                                   ^
  符号:   类 BlockStmtContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:464: 错误: 找不到符号
        public Void visitVarDecStmt(VarDecStmtContext ctx) {
                                    ^
  符号:   类 VarDecStmtContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:480: 错误: 找不到符号
        public Void visitExpression(ExpressionContext ctx) {
                                    ^
  符号:   类 ExpressionContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/project2/ConstExprVisitor.java:3: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcBaseVisitor;
                     ^
src/main/java/impl/project2/ConstExprVisitor.java:4: 错误: 程序包generated.Splc不存在
import generated.Splc.SplcParser;
                     ^
src/main/java/impl/project2/ConstExprVisitor.java:7: 错误: 找不到符号
public class ConstExprVisitor extends SplcBaseVisitor<Integer> {
                                      ^
  符号: 类 SplcBaseVisitor
src/main/java/impl/project2/ConstExprVisitor.java:9: 错误: 程序包SplcParser不存在
    public Integer visitExpression(SplcParser.ExpressionContext ctx) {
                                             ^
src/main/java/impl/project2/ConstExprVisitor.java:13: 错误: 程序包SplcParser不存在
    private Integer evaluate(SplcParser.ExpressionContext ctx) {
                                       ^
src/main/java/framework/project2/Grader.java:40: 错误: 找不到符号
        SplcLexer lexer = new SplcLexer(input);
        ^
  符号:   类 SplcLexer
  位置: 类 Grader
src/main/java/framework/project2/Grader.java:40: 错误: 找不到符号
        SplcLexer lexer = new SplcLexer(input);
                              ^
  符号:   类 SplcLexer
  位置: 类 Grader
src/main/java/framework/project2/Grader.java:42: 错误: 找不到符号
        SplcParser parser = new SplcParser(tokens);
        ^
  符号:   类 SplcParser
  位置: 类 Grader
src/main/java/framework/project2/Grader.java:42: 错误: 找不到符号
        SplcParser parser = new SplcParser(tokens);
                                ^
  符号:   类 SplcParser
  位置: 类 Grader
src/main/java/framework/project2/Grader.java:67: 错误: 找不到符号
            new SplcBaseVisitor<Void>() {
                ^
  符号:   类 SplcBaseVisitor
  位置: 类 Grader
src/main/java/framework/project2/Grader.java:71: 错误: 程序包SplcParser不存在
                public Void visitVarDecStmt(SplcParser.VarDecStmtContext ctx) {
                                                      ^
src/main/java/framework/project2/Grader.java:68: 错误: 方法不会覆盖或实现超类型的方法
                @Override
                ^
src/main/java/framework/project2/Grader.java:72: 错误: 程序包SplcParser不存在
                    SplcParser.ExpressionContext expression = ctx.expression();
                              ^
src/main/java/impl/Compiler.java:46: 错误: 找不到符号
        SplcLexer lexer = new SplcLexer(input);
        ^
  符号:   类 SplcLexer
  位置: 类 Compiler
src/main/java/impl/Compiler.java:46: 错误: 找不到符号
        SplcLexer lexer = new SplcLexer(input);
                              ^
  符号:   类 SplcLexer
  位置: 类 Compiler
src/main/java/impl/Compiler.java:48: 错误: 找不到符号
        SplcParser parser = new SplcParser(tokens);
        ^
  符号:   类 SplcParser
  位置: 类 Compiler
src/main/java/impl/Compiler.java:48: 错误: 找不到符号
        SplcParser parser = new SplcParser(tokens);
                                ^
  符号:   类 SplcParser
  位置: 类 Compiler
src/main/java/impl/Compiler.java:50: 错误: 找不到符号
        ProgramContext program = parser.program();
        ^
  符号:   类 ProgramContext
  位置: 类 Compiler
src/main/java/impl/Compiler.java:364: 错误: 找不到符号
            List<SpecifierContext> specs = argsCtx.specifier();
                 ^
  符号:   类 SpecifierContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:365: 错误: 找不到符号
            List<VarDecContext> varDecs = argsCtx.varDec();
                 ^
  符号:   类 VarDecContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:376: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:378: 错误: 找不到符号
            for (GlobalDefContext def : ctx.globalDef()) {
                 ^
  符号:   类 GlobalDefContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:384: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:399: 错误: 找不到符号
            FuncArgsContext argsCtx = ctx.funcArgs();
            ^
  符号:   类 FuncArgsContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:401: 错误: 找不到符号
                List<SpecifierContext> specs = argsCtx.specifier();
                     ^
  符号:   类 SpecifierContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:402: 错误: 找不到符号
                List<VarDecContext> varDecs = argsCtx.varDec();
                     ^
  符号:   类 VarDecContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:414: 错误: 找不到符号
            for (StatementContext stmt : ctx.statement()) {
                 ^
  符号:   类 StatementContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:423: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:436: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:447: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:453: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:456: 错误: 找不到符号
            for (StatementContext stmt : ctx.statement()) {
                 ^
  符号:   类 StatementContext
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:463: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/Compiler.java:479: 错误: 方法不会覆盖或实现超类型的方法
        @Override
        ^
src/main/java/impl/project2/ConstExprVisitor.java:8: 错误: 方法不会覆盖或实现超类型的方法
    @Override
    ^
68 个错误





很好，这一大坨报错其实都在说一件事：

> **你还没有生成 ANTLR 的解析器代码，所以 `generated.Splc` 这个包根本不存在。**

所以不是你 `Compiler.java` 写坏了，而是 **少了一步“先用 ANTLR 生成 Java 源码，再用 javac 编译”**。

我一步步帮你把坑填上。

---

## 1️⃣ 先理解错误的根本原因

报错里一堆这样的东西：

```text
错误: 程序包generated.Splc不存在
import generated.Splc.SplcBaseVisitor;
...
错误: 找不到符号  类 SplcLexer / SplcParser / ProgramContext / ...
```

意思就是：

* 代码里写了 `import generated.Splc.*;`
* 但是 `src/main/java/generated/Splc/` 下面 **没有** `SplcLexer.java、SplcParser.java、SplcBaseVisitor.java` 这些文件
* 它们本来应该是 **由 ANTLR 根据你的 `Splc.g4` 自动生成的**

在你新的虚拟机环境里，还没执行“生成 parser”的那一步，所以现在看起来就像：

> “我要编译一个引用 `generated.Splc.*` 的工程，但这个包根本不存在。”

---

## 2️⃣ 第一步：检查一下目录里有没有生成代码

在项目根目录执行：

```bash
ls src/main/java/generated
```

如果显示 `No such file or directory` 或者里面没有 `Splc` 目录，那就说明确实还没生成。

再试：

```bash
ls src/main/java/generated/Splc
```

如果也没有东西，100% 说明：**ANTLR 还没跑过或用了旧路径**。

---

## 3️⃣ 第二步：用 ANTLR 生成 Splc 的 Java 源码

在项目根目录（就是有 `Splc.g4`、`libs/`、`src/` 的那个目录）执行：

```bash
mkdir -p src/main/java/generated/Splc

java -jar libs/antlr-4.13.2-complete.jar \
  -Dlanguage=Java \
  -visitor \
  -package generated.Splc \
  -o src/main/java/generated/Splc \
  Splc.g4
```

解释一下每一项：

* `java -jar libs/antlr-4.13.2-complete.jar`
  调用 ANTLR 工具，jar 文件老师已经放在 `libs/` 里了
* `-Dlanguage=Java`
  生成 Java 代码
* `-visitor`
  生成 `SplcBaseVisitor`，你的 `Compiler` 和 Project2 的代码都要用它
* `-package generated.Splc`
  生成的类的包名就是 `generated.Splc`，跟代码里的 import 对上
* `-o src/main/java/generated/Splc`
  输出目录，也就是 VSCode/编译时会看到的包路径
* `Splc.g4`
  你的语法文件（已经换成我们刚写好的完整版本）

执行完之后，你可以再看一下：

```bash
ls src/main/java/generated/Splc
```

应该能看到一堆文件，比如：

* `SplcLexer.java`
* `SplcParser.java`
* `SplcBaseVisitor.java`
* `SplcVisitor.java`
* 以及一些内部类相关文件

这一步成功之后，“generated.Splc 不存在”的问题就解决了 ✅

> ⚠️ 以后如果你再改 `Splc.g4`，也要重新跑一遍这个命令生成新的 Java 文件。

---

## 4️⃣ 第三步：重新编译所有 Java 文件

现在再执行你之前的编译命令：

```bash
mkdir -p out

javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
```

这次如果 `generated.Splc` 里有东西，前面那一长串“找不到 SplcParser/SplcLexer/SplcBaseVisitor”的错误应该都会消失。

如果还有报错，多半就是我们 `Compiler.java` 里的小语法问题了，那时我们再具体看。

---

## 5️⃣ 第四步：运行程序测试

编译成功后，运行：

```bash
java -cp "out:libs/antlr-4.13.2-complete.jar" Main
```

如果 `Main` 还指向 `testcases/project3/ok_01.splc`，你应该看到类似：

```text
Variables:
...

Functions:
...
```

或者，如果你改成某个 error 用例，应该会看到 Project3 的语义错误输出。

---

## 6️⃣ 补充：有 Makefile 的话，也可以偷懒

有些课程项目的 `Makefile` 已经帮你写好“生成 parser + 编译 + 运行”的命令了，比如：

```bash
make parser     # 生成 SplcLexer/SplcParser 等
make project3   # 或 make all / make
```

如果你想用 Makefile，可以：

```bash
cat Makefile
```

看里面有没有类似：

```make
parser:
    java -jar libs/antlr-4.13.2-complete.jar ...
```

有的话直接 `make parser` 也行，本质上做的是和上面第 3 步一样的事情。

---

## ✅ 小结（一句话版）

* **现在所有错误的根本原因**：没有生成 ANTLR 的 Java 源码 → `generated.Splc` 包不存在
* **解决步骤**：

  1. 在项目根目录执行 ANTLR 命令：

     ```bash
     mkdir -p src/main/java/generated/Splc
     java -jar libs/antlr-4.13.2-complete.jar \
       -Dlanguage=Java \
       -visitor \
       -package generated.Splc \
       -o src/main/java/generated/Splc \
       Splc.g4
     ```

  2. 再执行：

     ```bash
     javac -cp libs/antlr-4.13.2-complete.jar -d out $(find src/main/java -name "*.java")
     ```

  3. 然后：

     ```bash
     java -cp "out:libs/antlr-4.13.2-complete.jar" Main
     ```

你可以先跑完上面三步，如果中间还有新的报错，把错误贴出来，我帮你继续逐条排。



cs323@deb-cs323-compilers:~/Desktop/Project3/Sustech2025_Compile-project3/CS323-Compilers-2025F-Projects-project3-base$ 
javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
src/main/java/impl/Compiler.java:300: 错误: 找不到符号
            if (specCtx.INT() != null) {
                       ^
  符号:   方法 INT()
  位置: 类型为SpecifierContext的变量 specCtx
src/main/java/impl/Compiler.java:303: 错误: 找不到符号
            if (specCtx.CHAR() != null) {
                       ^
  符号:   方法 CHAR()
  位置: 类型为SpecifierContext的变量 specCtx
src/main/java/impl/Compiler.java:314: 错误: 找不到符号
            if (varDecCtx.Identifier() != null) {
                         ^
  符号:   方法 Identifier()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:315: 错误: 找不到符号
                identHolder[0] = varDecCtx.Identifier();
                                          ^
  符号:   方法 Identifier()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:320: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.Number() != null) {
                         ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:320: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.Number() != null) {
                                                       ^
  符号:   方法 Number()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:321: 错误: 找不到符号
                Type elementType = buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
                                                                          ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:324: 错误: 找不到符号
                    size = Integer.parseInt(varDecCtx.Number().getText());
                                                     ^
  符号:   方法 Number()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:339: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.STAR() != null) {
                         ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:339: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.STAR() != null) {
                                                       ^
  符号:   方法 STAR()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:340: 错误: 找不到符号
                return buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
                                                              ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:344: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.LPAREN() != null) {
                         ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:344: 错误: 找不到符号
            if (varDecCtx.varDec() != null && varDecCtx.LPAREN() != null) {
                                                       ^
  符号:   方法 LPAREN()
  位置: 类型为VarDecContext的变量 varDecCtx
src/main/java/impl/Compiler.java:345: 错误: 找不到符号
                return buildDeclaratorType(baseType, varDecCtx.varDec(), identHolder);
                                                              ^
  符号:   方法 varDec()
  位置: 类型为VarDecContext的变量 varDecCtx
14 个错误




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
            for (TerminalNode id : ctx.Identifier()) {
                checkUndeclaredUse(id);
            }
            return visitChildren(ctx);
        }
    }
}


太好了，这张图说明几件关键的好事：

1. **程序成功运行了，没有崩溃** ✅
2. 读到了 `ok_01.splc`，并且按要求打印出了

   * `Variables:` 列表
   * `Functions:` 列表
3. 类型打印格式也对：

   * `a: int`
   * `c: char`
   * `d: int[123]`
   * `main: int(int,int[1])`

`v0 ~ v5` 这些奇怪的名字大概率是测试文件里的局部变量 / 数组变量（老师专门写的用来测数组/作用域的），**按出现顺序打印出来是对的**。

到这一步，可以说：

> ✅ 你的编译、运行环境 + 语义分析基础逻辑已经打通了。

---

接下来你可以做两件事来进一步确认 80 分的逻辑是否正确：

### 1️⃣ 跑几个其它 OK 样例

在 `Main.java` 里改一下测试文件：

```java
InputStream input = new FileInputStream("testcases/project3/ok_02.splc");
// 或 ok_03.splc, ok_04.splc ...
```

然后：

```bash
javac -cp libs/antlr-4.13.2-complete.jar -d out $(find src/main/java -name "*.java")
java -cp "out:libs/antlr-4.13.2-complete.jar" Main
```

预期：所有 `ok_xx.splc` 都应该 **不报语义错误**，正常打印 Variables / Functions。

---

### 2️⃣ 跑 error 样例，看能否正确报错

同样改 `Main.java`：

```java
InputStream input = new FileInputStream("testcases/project3/error_01.splc");
```

重新编译+运行，看看终端输出的错误类型是不是合理，比如：

* 使用未声明变量 → `undeclared use`
* 同一作用域重复定义 → `redefinition`
* 函数声明 / 定义冲突 → `redeclaration`
* 数组长度非法 → `definition of incomplete type`

如果你愿意，可以把某个 `error_xx.splc` 的运行输出贴出来，我可以帮你一起判断：

* 这个错误类型对不对
* 如果不对，是哪一类语义检查还差一步。

总之：现在这一步已经非常关键了，你的 Project3 代码已经“真正跑起来”了 🎉


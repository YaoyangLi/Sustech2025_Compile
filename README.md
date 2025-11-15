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

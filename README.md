# Sustech2025_Compile
你现在点运行按钮时报的这一串：

> CharStream cannot be resolved to a type
> CharStreams cannot be resolved
> SplcLexer cannot be resolved to a type
> CommonTokenStream cannot be resolved to a type
> SplcParser cannot be resolved to a type
> ProgramContext cannot be resolved to a type

**不是代码错了，而是 VSCode 帮你运行时没把依赖塞进 classpath 里。**

---

## 先解释一下发生了什么

你点击 `Main.java` 的小绿色 ▶️ 时，VSCode 真正在干的事情（你日志里已经写出来了）是：

```bash
/usr/lib/jvm/java-21-openjdk-amd64/bin/java \
  -cp /home/.../jdt_ws/CS323-.../bin \
  Main
```

注意它的 `-cp`：

* **只有** `.../jdt_ws/.../bin` 这个 VSCode 自己的输出目录
* **没有** 我们之前在命令行里加的那玩意儿：

  ```bash
  out:libs/antlr-4.13.2-complete.jar
  ```

所以当 `Compiler.start()` 里写：

```java
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import generated.Splc.SplcLexer;
import generated.Splc.SplcParser;
```

VSCode 运行时的 JVM 会说：

> 我根本没加载 antlr 的 jar，也没看到 generated 的这些类，所以找不到！

在命令行你自己跑的是：

```bash
java -cp "out:libs/antlr-4.13.2-complete.jar" Main
```

那里 classpath 是正确的，所以能跑；
VSCode 的“点运行”版本 classpath 不包含 `libs/antlr-4.13.2-complete.jar`，所以直接炸。

---

## ✅ 解决方案：告诉 VSCode “这些 jar 也是项目的一部分”

只要做一次，之后就可以放心点运行键。

### 第一步：确认生成的 Splc 代码在正确的位置

在项目根目录：

```bash
ls src/main/java/generated/Splc
```

应该能看到：

```text
SplcLexer.java
SplcParser.java
SplcBaseVisitor.java
SplcVisitor.java
...
```

如果没有，说明你还没跑 ANTLR 生成步骤（或者生成到了别的目录），那就先再跑一遍：

```bash
mkdir -p src/main/java/generated/Splc

java -jar libs/antlr-4.13.2-complete.jar \
  -Dlanguage=Java \
  -visitor \
  -package generated.Splc \
  -o src/main/java/generated/Splc \
  Splc.g4
```

这个你之前已经做过基本没问题，我只是再强调一下。

---

### 第二步：在 VSCode 里把 antlr jar 加到 classpath

在项目根目录创建（或修改）文件：
`.vscode/settings.json`

如果没有 `.vscode` 目录，就自己建一个：

```bash
mkdir -p .vscode
```

然后编辑：

```bash
nano .vscode/settings.json
```

填入这一段 JSON（如果已有内容，就合并一下）：

```json
{
  "java.project.sourcePaths": [
    "src/main/java"
  ],
  "java.project.referencedLibraries": [
    "libs/antlr-4.13.2-complete.jar"
  ]
}
```

含义：

* `"src/main/java"` 告诉 VSCode：这里是源码目录（它会把 `generated/Splc` 也一起编译，因为在里面）
* `"libs/antlr-4.13.2-complete.jar"` 告诉 VSCode：这是一个依赖库，编译和运行时都要加上

保存文件后，回到 VSCode：

* 右上角可能会提示 Java 项目配置更新
* 或者你可以按 `Ctrl+Shift+P` → 输入 `Java: Clean Java Language Server Workspace` → 回车 → 重启 VSCode

这样 VSCode 的 Java 插件会重新分析项目，把 antlr 的 jar 和 generated 源码一起当成项目的一部分。

---

### 第三步：再点一次 `Main.java` 的运行按钮

现在再点击 Main 旁边的 ▶️：

* VSCode 会自动编译项目到它自己的 `.../jdt_ws/.../bin` 下
* 同时会把 `libs/antlr-4.13.2-complete.jar` 加到运行 classpath 里
* 之前那些 `CharStream cannot be resolved`、`SplcLexer cannot be resolved` 之类的问题就没了

你应该能看到和你用命令行跑时一样的输出，比如：

```text
Variables:
a: int
...

Functions:
main: int(int,int[123])
...
```

---

## 一个现实建议（不想折腾的话）

你现在已经：

* 会用 `javac -cp ...` 编译
* 会用 `java -cp ...` 运行
* 项目本来就是命令行驱动的（Grader）

所以 **完全可以继续用命令行测试**，把点击运行键当成“可选的附加功能”，不是必须。

但是如果你已经加好 `.vscode/settings.json`，那点运行键也会很爽：

* 想改测试样例：改 `Main.java` 里默认路径 / 支持 args
* 想看输出：直接在终端面板看

---

## 小总结（一句话版）

* 你点运行按钮报错，是因为 VSCode 运行时的 classpath 里 **没有 `libs/antlr-4.13.2-complete.jar`**，也没正确加载 `generated.Splc` 的源码。
* 把这个 jar 加到 VSCode 的 `java.project.referencedLibraries` 里，并确认 Splc 生成在 `src/main/java/generated/Splc`，问题就解决了。

如果你愿意，你可以把你当前 `.vscode/settings.json` 的内容发给我，我帮你检查一下有没有写错路径。



cs323@deb-cs323-compilers:~/Desktop/Project3/Sustech2025_Compile-project3_v2/CS323-Compilers-2025F-Projects-project3-base$  /usr/bin/env /usr/lib/jvm/java-21-openjdk-amd64/bin/java @/tmp/cp_bplba8bh6jpkv80k8ssa2ysdc.argfile Main 
Exception in thread "main" java.lang.Error: Unresolved compilation problems: 
        SplcLexer cannot be resolved to a type
        SplcLexer cannot be resolved to a type
        SplcParser cannot be resolved to a type
        SplcParser cannot be resolved to a type
        ProgramContext cannot be resolved to a type

        at impl.Compiler.start(Compiler.java:34)
        at framework.project3.Grader.run(Grader.java:21)
        at Main.main(Main.java:13)


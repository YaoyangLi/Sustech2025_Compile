# Sustech2025_Compile
这是正常现象，不是你写坏了 😂
VS Code 报的这两类红线，其实是**两个独立的问题**：

1. `generated.Splc.*`：**还没生成 ANTLR 的 Java 文件**
2. `org.antlr.v4.runtime.*`：VS Code 的 Java 插件**还不知道 antlr 的 jar 在哪**

我一步一步帮你把它变绿。

---

## ✅ 第一步：生成 `generated.Splc` 下的 Java 代码

`import generated.Splc.SplcParser;` 报错，通常是因为：
**`generated/Splc` 这个包还不存在**——你还没从 `Splc.g4` 生成 parser/lexer 代码。

在 Linux 虚拟机里：

1. 打开 VS Code 终端，确保当前目录是项目根目录（能看到 `Splc.g4`, `libs/`, `src/` 等）。

2. 建一个目录给生成代码（一般已经有，没的话建一下）：

   ```bash
   mkdir -p src/main/java/generated/Splc
   ```

3. 运行 ANTLR，把 `Splc.g4` 生成为 Java 代码：

   ```bash
   java -jar libs/antlr-4.13.2-complete.jar \
     -Dlanguage=Java \
     -visitor \
     -no-listener \
     -package generated.Splc \
     -o src/main/java/generated/Splc \
     Splc.g4
   ```

运行成功后，你应该在这里看到这些文件：

```text
src/main/java/generated/Splc/
  SplcLexer.java
  SplcParser.java
  SplcBaseVisitor.java
  SplcVisitor.java
  ...
```

> 这样 `import generated.Splc.SplcParser;`、`SplcBaseVisitor` 等才有“实体”。

如果 VS Code 还是发红，可以试下：

* `Ctrl+Shift+P` → 输入 `Reload Window` → 回车，重载一下 VS Code 窗口。

---

## ✅ 第二步：让 VS Code 知道 ANTLR 的 jar（解决 `org.antlr.v4.runtime`）

你项目里已经有 `libs/antlr-4.13.2-complete.jar`，
但是 VS Code 的 Java 插件**默认不把它当作依赖**，所以：

```java
import org.antlr.v4.runtime.CharStream;
```

会提示 `The import org.antlr.v4.runtime cannot be resolved`。

解决办法（最简单适合你现在的项目结构）：

1. 在项目根目录创建或编辑：`.vscode/settings.json`
   如果 `.vscode` 文件夹不存在，先建一个：

   ```bash
   mkdir -p .vscode
   ```

2. 写入（或合并）如下内容：

   ```json
   {
     "java.project.referencedLibraries": [
       "libs/**/*.jar"
     ]
   }
   ```

保存后，VS Code 的 Java 扩展会把 `libs` 目录下的 jar 都当成依赖，
`org.antlr.v4.runtime.*` 的红线就会消失。

> 这不会影响你用命令行编译，只是让编辑器“别再瞎担心”。

---

## ✅ 第三步：验证一下编译 / 运行（顺便检查 imports 真的 OK）

在项目根目录终端运行：

```bash
mkdir -p out

javac -cp libs/antlr-4.13.2-complete.jar:src/main/java \
  -d out \
  $(find src/main/java -name "*.java") Main.java
```

如果这一步**没有报错**：

* 说明 `generated.Splc.*` 和 `org.antlr.*` 都已经在编译时可用 ✅
* VS Code 再红就只是缓存问题（重载窗口一般能解决）。

然后你可以直接运行一个样例，比如 `err_01`：

```bash
java -cp libs/antlr-4.13.2-complete.jar:out Main testcases/project3/err_01.splc
```

看到：

```text
2:4: error: Undeclared use of 'y'
```

就说明整条链路（读取 → 解析 → 语义分析 → 报错）已经通了。

---

如果你愿意，下一步你可以：

* 把 `javac` 或 `java` 命令的报错贴上来
* 或者告诉我 VS Code 现在还剩下哪些红线（类名我能一眼看出是哪个环节的问题），
  我可以继续帮你做“错误清理”。

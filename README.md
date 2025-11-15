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


很好，你现在遇到的是两个“层次”不同的问题：

* **编译器层面**：`javac` 能不能找到 `org.antlr.v4.runtime.tree.TerminalNode`
* **VS Code 提示层面**：Java 扩展知不知道这个 jar

目前 VS Code 在 `Project3SemanticError.java` 上报：

```java
import org.antlr.v4.runtime.tree.TerminalNode;
// The import org.antlr... cannot be resolved
public static Project3SemanticError undeclaredUse(TerminalNode identifier);
// TerminalNode cannot be resolved to a type
```

这几乎可以肯定是：**VS Code 的 Java 扩展还不知道 antlr 的 jar 在哪**，而不是你代码写错了。

我分两步帮你解决：先确认编译是否真的 OK，再把 VS Code 的红线清掉。

---

## ① 先确认“真实的编译”是不是 OK（最重要）

先别管 VS Code 红线，在 **项目根目录**（能看到 `libs/`, `src/`, `Splc.g4` 的那一层）打开终端，执行：

```bash
mkdir -p out

javac -cp libs/antlr-4.13.2-complete.jar:src/main/java \
  -d out \
  $(find src/main/java -name "*.java") Main.java
```

* 如果这一条 **成功执行，没有任何报错**
  ➜ 说明 `org.antlr.v4.runtime.tree.TerminalNode` 在编译时是能被找到的，
  VS Code 的红线只是“编辑器不开心”，但不会影响真实编译和运行。

* 如果这一条 **报错类似：package org.antlr.v4.runtime.tree does not exist**
  ➜ 那说明 classpath 还没配对，我们再调整命令（但你之前能跑 ANTLR，那一般 Java 是正常的）。

先按上面这条跑一次，如果成功了，接着做第 ② 步来“安抚” VS Code。

---

## ② 告诉 VS Code：`libs` 里的 jar 是 Java 依赖

### 2.1 在 `.vscode/settings.json` 声明 jar

在项目根目录：

```bash
mkdir -p .vscode
```

然后在 `.vscode/settings.json` 写入（或追加）：

```json
{
  "java.project.referencedLibraries": [
    "libs/**/*.jar"
  ]
}
```

注意几点：

* 路径是 **相对于你在 VS Code 里打开的那个文件夹** 的
  所以你必须是 **直接把项目根目录打开成 workspace**，而不是它的上级目录。
* `libs/**/*.jar` 会把 `libs` 下面所有 jar 都加进依赖里，包括 `antlr-4.13.2-complete.jar`

保存文件后：

1. 在 VS Code 命令面板中（Ctrl+Shift+P）执行：
   **Java: Clean Java Language Server Workspace**
2. VS Code 会提示重启窗口，点“Restart” / “Reload Window”。

重启后 Java 扩展会重新扫描 project + referencedLibraries，
`org.antlr.v4.runtime.tree.TerminalNode` 那行多半就不红了。

---

## ③ 再看 Project3SemanticError.java 的情况

`Project3SemanticError.java` 里类似这样的代码：

```java
import org.antlr.v4.runtime.tree.TerminalNode;

public static Project3SemanticError undeclaredUse(TerminalNode identifier) { ... }
```

这个类本身就是老师给框架用来报语义错误的工具类：

* 它**必须**依赖 ANTLR 的 runtime（`org.antlr.v4.runtime.*`）
* 只要：

  * antlr 的 jar 在 `libs/`
  * VS Code 的 `java.project.referencedLibraries` 已经指向 `libs/**/*.jar`

  ➜ 红线就会消失。

你不用改 `Project3SemanticError.java` 的任何内容，它写得是对的。

---

## ④ 小结一下你现在要做的两件事

1. **验证编译是否没问题**（真实标准）：

   ```bash
   javac -cp libs/antlr-4.13.2-complete.jar:src/main/java -d out \
     $(find src/main/java -name "*.java") Main.java
   ```

   ➜ 成功说明 TerminalNode 其实已经正常被编译引用。

2. **让 VS Code 别再乱标红**：

   * 在 `.vscode/settings.json` 中加：

     ```json
     {
       "java.project.referencedLibraries": [
         "libs/**/*.jar"
       ]
     }
     ```

   * 然后用 `Java: Clean Java Language Server Workspace` 清缓存并重启窗口。

---

如果你愿意，下一步你可以把：

* `javac` 命令的输出
* 或 `.vscode/settings.json` 的内容

贴一小段出来，我可以帮你确认：

* 是否已经“真正编译通过”
* VS Code 还剩下的红线是不是可以直接无视，还是还有路径问题要调。

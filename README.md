# Sustech2025_Compile
好，现在你已经把代码放进 Linux 虚拟机里，用 VS Code 打开了项目，接下来就是“**怎么编译 + 怎么拿样例测试**”的问题，对吧。

我分两部分说：

* A. 不改任何 Java 代码，直接用命令行喂样例（推荐做法）
* B. 如果你更习惯改 `Main.java` 里用 `FileInputStream` 也可以，我会告诉你怎么改

---

## A. 推荐做法：不改代码，用命令行测试样例

> ✅ 适合：以后交作业也不用改回来，保持和老师给的框架一致

### 1. 在 VS Code 里打开终端

在你的 Linux VM 中：

```bash
cd CS323-Compilers-2025F-Projects-project3-base
```

（就是项目根目录，能看到 `src`, `testcases`, `Makefile` 的那一层）

### 2. 编译所有 Java 源码

你用的是 antlr + 普通 Java 项目，可以直接用 `javac`：

```bash
mkdir -p out

javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
```

解释一下：

* `-cp libs/antlr-4.13.2-complete.jar`
  告诉编译器：类路径里要包含 ANTLR 的 jar
* `-d out`
  把 `.class` 编译产物放到 `out/` 目录
* `$(find src/main/java -name "*.java")`
  把所有源文件都编译掉（包括你刚改的 `Compiler.java`）

如果没有报错，说明编译成功。

### 3. 用 `Main` + 重定向运行某个测试样例

假设 Main.java 没有 `package` 语句（你给我的 Main 也是这样），那么运行：

```bash
# 运行 err_01.splc
java -cp libs/antlr-4.13.2-complete.jar:out Main < testcases/project3/err_01.splc

# 运行 ok_01.splc
java -cp libs/antlr-4.13.2-complete.jar:out Main < testcases/project3/ok_01.splc
```

注意 Linux 下类路径用 `:` 分隔，顺序是：

1. ANTLR 的 jar
2. 你刚编好的 `out` 目录

这样 `Main` 里的代码其实还是：

```java
InputStream input = System.in;
AbstractGrader grader = new Grader(input, System.out);
grader.run();
```

而你通过 `< 文件名` 把文件内容送进 stdin，相当于“伪装成键盘输入整个源代码”。

### 4. 对比输出和期望 `.txt`

例如：

```bash
java -cp libs/antlr-4.13.2-complete.jar:out Main < testcases/project3/err_01.splc
```

你应该看到类似：

```text
2:4: error: Undeclared use of 'y'
```

和 `testcases/project3/err_01.txt` 一致就说明这条过了。

---

## B. 你说的方式：在 Main.java 里写 `FileInputStream`

> ✅ 可以用，方便你临时调试
> ⚠️ 但最后交作业前记得改回从 `System.in` 读

你提到的做法类似这样：

```java
InputStream input = new FileInputStream("testcases/project3/ok_01.splc");
```

这是完全可以的（Main 里通常已经 `import java.io.*;` 了），完整大概像：

```java
import framework.AbstractGrader;
import framework.project3.Grader;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        InputStream input = new FileInputStream("testcases/project3/ok_01.splc");
        AbstractGrader grader = new Grader(input, System.out);
        grader.run();
    }
}
```

这样：

1. 重新编译：

   ```bash
   javac -cp libs/antlr-4.13.2-complete.jar -d out $(find src/main/java -name "*.java")
   ```

2. 运行时就不需要 `<` 重定向了：

   ```bash
   java -cp libs/antlr-4.13.2-complete.jar:out Main
   ```

3. 想换测试文件，只要改 `FileInputStream("...")` 里的路径，比如：

   ```java
   new FileInputStream("testcases/project3/err_01.splc");
   ```

4. 你说“通过修改 ok_01.splc 测试不同样例”也可以：

   * 直接修改 `testcases/project3/ok_01.splc` 内容
   * 每次保存后重新运行 `java ... Main` 看输出变化

👉 **但不太推荐你长期改官方样例文件**，容易忘记原始内容。
更干净的做法是：

* 自己新建一个 `my_test.splc` 放在 `testcases/project3/` 下
* 然后用 `FileInputStream("testcases/project3/my_test.splc")`
* 官方 `ok_01.splc` 保持原样，方便以后对比。

---

## 小结一句话

* **如果只是想跑老师给的样例**：
  ➜ 推荐方式：
  编译后用命令行：

  ```bash
  java -cp libs/antlr-4.13.2-complete.jar:out Main < testcases/project3/ok_01.splc
  ```

* **如果更喜欢“写死一个文件路径”**：
  ➜ 可以在 `Main.java` 里改成 `new FileInputStream("...")` 测试，
  记得交作业前改回用 `System.in` 比较安全。

---

如果你愿意，下一步我可以帮你：

* 设计一个**自定义小样例**（只用 int/char/数组，不碰 struct & 指针），你可以照着操作一遍，看 80 分功能是不是都正常工作。

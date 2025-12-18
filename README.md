下面给你一套**在 VMware Workstation 的 Linux 系统 + VS Code** 里，从“放入初始代码 + 仅新增 test100~109”到“编译 + 跑自带检查脚本”的**完整可复制粘贴流程**（按新手照做即可）。

> 假设你在 Linux 里用的是 Ubuntu/Debian 系列（能用 `apt`）。如果是 CentOS/Fedora 我也能给对应命令。

---

## 0）在 VS Code 里打开终端

VS Code 顶部菜单：**Terminal → New Terminal**
后续命令都在这个终端里粘贴执行。

---

## 1）安装依赖（只需要做一次）

```bash
sudo apt update
sudo apt install -y git make curl tar gzip \
  openjdk-17-jdk maven \
  clang-19 \
  dos2unix
```

检查是否装好：

```bash
java -version
javac -version
clang-19 --version
make --version
```

> 如果 `clang-19` 装不上（源里没有），把报错贴我，我按你的系统版本给你装 clang-19 的官方源方法。

---

## 2）放入你的项目代码并进入目录

假设你的项目目录叫 `SUSTech-CS323-Compiler-my-project5`：

```bash
cd /path/to/SUSTech-CS323-Compiler-my-project5
```

用 `ls` 确认目录里至少有这些（大概即可）：

* `Splc.g4`
* `libs/antlr-4.13.2-complete.jar`
* `src/main/java/`
* `project5_testcases/`
* `conf/`

---

## 3）防坑：把脚本转成 Linux 换行 + 加可执行权限（强烈建议）

从 Windows 拷贝过来的项目，常见问题是 `CRLF` 换行导致 `.sh` 报错。

```bash
dos2unix project5_testcases/*.sh 2>/dev/null || true
chmod +x project5_testcases/*.sh
```

---

## 4）生成 ANTLR 解析器代码（Makefile 顶层负责这一步）

在项目根目录运行：

```bash
make
```

它会读取 `*.g4`（比如 `Splc.g4`），然后用 `libs/antlr-4.13.2-complete.jar` 生成 Parser/Visitor 等文件到：
`src/main/java/generated/`（以及其子目录）

如果你想确认生成了文件，可以：

```bash
find src/main/java/generated -type f | head
```

---

## 5）编译 Java（生成可被测试脚本调用的 .class）

### 方案 A（推荐）：用 Maven 编译（如果你项目有 pom.xml）

```bash
mvn -q -DskipTests package
```

编译产物一般会在：
`target/classes`

### 方案 B：用 javac 手动编译（最通用）

如果你没有 pom.xml 或 Maven 报错，用这个：

```bash
mkdir -p build/classes

javac -encoding UTF-8 \
  -cp "libs/antlr-4.13.2-complete.jar" \
  -d build/classes \
  $(find src/main/java -name "*.java")
```

编译成功后：

* 产物在 `build/classes`

---

## 6）运行“参考程序合法性检查”（只测测试样例本身是否正确）

进入测试目录：

```bash
cd project5_testcases
```

先生成参考可执行文件（普通/UBSan/ASan）：

```bash
make refs
```

然后跑合法性检查：

```bash
./check_testcases.sh
```

✅ 这一步通过，说明：你的 `test100~test109`（以及其它 testXX）在参考程序下**输入输出匹配且 sanitizer 不报错**。

---

## 7）运行“你的编译器 IR 测试”（真正测你的编译器对不对）

### 7.1 让 Makefile 能找到你的 .class（IDEA_TARGET）

* 如果你用 **Maven**：`IDEA_TARGET=../target/classes`
* 如果你用 **javac 手动编译**：`IDEA_TARGET=../build/classes`

下面给你两套命令，选你用的那套执行即可。

### A）你用 Maven 编译的

```bash
make genir IDEA_TARGET=../target/classes
make compileir
./testir.sh
```

### B）你用 javac 手动编译的

```bash
make genir IDEA_TARGET=../build/classes
make compileir
./testir.sh
```

✅ `./testir.sh` 通过，说明：你编译器生成的 LLVM IR 被 clang 编译运行后，输出与 `.out` 全部匹配。

---

## 8）确认你的 test100~109 真的“在仓库里且会被提交”

你之前线上 “Submitted testcases = 0” 最常见原因是：测试文件没被 git 跟踪/没 commit，导致 `git archive HEAD` 打包时根本没带上。

在项目根目录执行：

```bash
git status -sb
```

如果看到 `test100~test109` 相关文件在 “Untracked” 或 “not staged”，就需要：

```bash
git add project5_testcases/test10{0..9}
git commit -m "Add test100-test109"
```

再确认提交里确实包含：

```bash
git ls-tree -r --name-only HEAD | grep -E '^project5_testcases/test10[0-9]/' | head
```

---

## 9）最后：如果你要在 Linux VM 上直接提交

（前提：你已经把需要的文件都 commit 了）

```bash
make handin
```

它会提示你输入 API key（保存到 `.myapi.key`），然后用 curl 上传。失败的话会提示你手动上传生成的 `Project5-handin.tar.gz`。

---


如果你在第 4～7 步任何一步报错：把**报错信息原样复制粘贴**给我（尤其是第一行错误 + 最后几行），我会按你报错位置给你“应该改哪条命令/缺哪个包/哪个路径不对”的最短修复方案。


# 1) 更新软件源索引
sudo apt update

# 2) 安装 add-apt-repository（有些系统默认没有）
sudo apt install -y software-properties-common

# 3) 启用 universe（Ubuntu 常见问题：没开 universe 导致找不到很多包）
sudo add-apt-repository -y universe

# 4) 再次更新索引
sudo apt update

# 5) 再安装 OpenJDK 17
sudo apt install -y openjdk-17-jdk


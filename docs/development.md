# Java 21 本地构建与 IDE 配置

后端要求完整 JDK 21。JRE 只能运行 Java，不提供默认构建所需的 `javac`。默认 Maven 编译器为 `javac`，`release`固定为21；CI也使用完整JDK21和默认编译器。

## IntelliJ IDEA

建议使用 IntelliJ IDEA 2023.3 或更新版本，并执行以下设置：

1. `File → Project Structure → Project`：Project SDK选择完整JDK21，Language level选择21，不启用Preview。
2. 同一窗口的`Modules → mall → Dependencies`：Module SDK继承项目JDK21；`Sources`中的语言级别继承项目设置。
3. `Settings → Build, Execution, Deployment → Compiler → Java Compiler`：`Use compiler`选择`Javac`，`mall`的Target bytecode version为21。
4. `Settings → Build Tools → Maven`：Importer及Runner使用项目JDK21。不要选择IDE自带的旧JRE。
5. Maven面板中不要勾选`cloud-ecj`，执行`Reload All Maven Projects`，再执行`Build → Rebuild Project`。

旧版IDE可能保留之前选择的`Eclipse`编译器，即使POM已更新，也要手动改为`Javac`。如果IDE没有Java21选项，请先升级IDE；调整语言级别为18无法编译本项目。

报错`source level should be in ... '18': 21`并同时显示`Eclipse compiler was used`，表示IDE调用的旧Eclipse编译器不支持Java21。这不同于业务代码或依赖下载错误。即使本机`java -version`显示21，旧Eclipse编译器仍会产生该报错。

## 命令行与 Maven 面板

先检查编译器和Maven使用的JDK：

```text
java -version
javac -version
mvn -version
```

`java`和`javac`均应为21，`mvn -version`报告的Java home也应指向该完整JDK。修改`JAVA_HOME`和`PATH`后，重新启动终端和IDE，避免旧进程保留原设置。

从仓库根目录使用已安装的Maven：

```text
mvn -f server/pom.xml clean verify
```

Linux/macOS也可以使用仓库的Bash启动器：

```bash
./mvnw -f server/pom.xml clean verify
```

Windows可直接在IDE的Maven面板依次执行`clean`和`verify`，或在终端使用已安装的`mvn`。

## 仅有JRE的云环境

只在没有`javac`、但具备JRE21的云环境显式启用：

```bash
./mvnw -Pcloud-ecj -f server/pom.xml clean verify
```

该profile使用固定ECJ3.42.0和Plexus Eclipse编译适配器，仅改变本次Maven构建的编译器，不要求IDE采用Eclipse编译器。它没有自动激活条件，普通本地构建和CI仍使用`javac`。只启用profile不会升级IDE内置的旧Eclipse编译器。

# Java 21 本地构建与 IDE 配置

仓库根目录已有Maven聚合`pom.xml`，IDE可直接打开它并导入server模块；Windows提供`mvnw.cmd`。

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

自动化测试应隔离真实运行配置。使用`-DAPP_CONFIG_FILE=`显式禁用自动发现根`.env`，避免测试读取实际商户、短信或生产数据库配置。从仓库根目录使用已安装的Maven：

```text
mvn -DAPP_CONFIG_FILE= -f server/pom.xml clean verify
```

Linux/macOS也可以使用仓库的Bash启动器：

```bash
./mvnw -DAPP_CONFIG_FILE= -f server/pom.xml clean verify
```

Windows可执行`.\mvnw.cmd -DAPP_CONFIG_FILE= clean verify`，也可在IDE测试运行配置中设置VM参数`-DAPP_CONFIG_FILE=`。统一生产构建入口`python scripts/manage.py build`会自动隔离测试子进程的运行配置和凭据。

## 仅有JRE的云环境

只在没有`javac`、但具备JRE21的云环境显式启用：

```bash
./mvnw -DAPP_CONFIG_FILE= -Pcloud-ecj -f server/pom.xml clean verify
```

该profile使用固定ECJ3.42.0和Plexus Eclipse编译适配器，仅改变本次Maven构建的编译器，不要求IDE采用Eclipse编译器。它没有自动激活条件，普通本地构建和CI仍使用`javac`。只启用profile不会升级IDE内置的旧Eclipse编译器。

## 本地运行与首次登录

直接运行`com.sujian.mall.Application`或执行`mvn -f server/pom.xml spring-boot:run`。没有发现运行配置、也没有显式生产环境变量时，默认local profile使用H2和演示商品，不需要微信商户号。首次管理员口令未设置时写入`data/bootstrap-admin.txt`；请以日志输出的绝对路径为准。日志不输出口令，已有数据库不会被重置。

IDE、Maven和可执行JAR均会在Spring决定profile和数据源前读取配置。自动发现顺序为当前工作目录`.env`，以及工作目录为仓库`server`模块时的父级仓库`.env`；也可用`APP_CONFIG_FILE`显式指定文件。命令行/进程变量优先于文件；配置中的相对文件路径基于选中配置文件的目录。敏感值可使用对应的`变量名_FILE`读取文件，RSA密钥使用文档列出的`*_PATH`。读取过程解析数据，不执行shell命令。`APP_CONFIG_FILE`显式为空时禁用自动发现。

网页执行`npm ci --prefix web`后运行`npm run dev --prefix web`，跟踪的.env.development提供本机API默认配置。网页自定义地址在web/.env.local中设置。只做本地演示时无需复制生产模板；根`.env`一旦配置为prod，IDE直接运行也会按生产要求校验。

正式部署使用根`.env.example`准备配置与密钥文件，再执行`python scripts/manage.py check`及`python scripts/manage.py up`。同一份配置派生网页、小程序、回调和容器参数，无需再编辑源码；生产配置及HTTPS模式详见[配置清单](configuration.md)与[部署说明](operations.md)。

# 素见好物 · Simply Seen

微信小程序、品牌商城、PC运营后台与 Java 21/MyBatis-Plus 服务端。界面采用暖白、低饱和绿、系统字体和留白；商品素材是项目自有 SVG/PNG。

[观看实际操作演示](docs/demo/README.md)：约92秒录像，展示商城下单、二级佣金、后台发货、售后退款、佣金冲回和对账，全部连接本地实际后端，支付及退款为开发测试。

## 配好变量后部署

直接使用[逐项中文注释的配置文件](.env.example)，按[填写步骤](docs/configuration-start.md)补齐必填项即可。

需要完整 **JDK 21**、**Node 22.12+**、**Python 3.11+**、**Docker 与 Compose 2.24+**。先将根目录`.env.example`复制为`.env`，按[配置清单](docs/configuration.md)填写站点、数据库、管理员、微信、短信、实名和数据加密参数，并放好商户密钥文件。官方接口已有默认值，站点API、素材、回调、小程序AppID及容器挂载由统一配置派生。

```bash
python3 scripts/manage.py check
python3 scripts/manage.py up
```

Windows使用`py -3`替代`python3`。`up`会执行测试、构建JAR/网页/小程序并等待容器健康。默认Caddy申请和续签HTTPS，需先设置域名DNS并开放80/443；已有HTTPS代理或证书可切换配置模式。启动后录入实际商品与库存即可经营，无需修改源码。小程序产物导入微信开发者工具，合法域名、审核和发布按微信平台要求办理。

```bash
python3 scripts/manage.py status
python3 scripts/manage.py down
```

进程环境优先于文件，所有敏感值支持`变量名_FILE`；相对路径以配置文件目录为准。错误或缺失配置会阻止生产启动，不会返回虚假渠道成功。详见[配置说明](docs/configuration.md)与[部署和恢复](docs/operations.md)。

## 本地启动

安装完整 **JDK 21**（`java -version`和`javac -version`都应为21）及 **Node 22.12+**。本地开发无需先配置微信商户号、MySQL或Redis。

IntelliJ IDEA直接打开仓库根目录的`pom.xml`，会自动导入`server`模块。Project/Module/Maven SDK均选JDK21，Java Compiler选`Javac`。运行`server`模块的`com.sujian.mall.Application`即可；也可以使用下面的终端命令。

Linux/macOS：

```bash
./mvnw -DAPP_CONFIG_FILE= clean verify
./mvnw -f server/pom.xml spring-boot:run
```

Windows PowerShell或命令提示符：

```text
.\mvnw.cmd -DAPP_CONFIG_FILE= clean verify
.\mvnw.cmd -f server/pom.xml spring-boot:run
```

启动器优先使用已安装的Maven；没有时下载固定3.9.11并校验SHA-512。若下载受网络限制，可安装Maven3.9+后使用`mvn`执行相同命令。仅有JRE21的受管云环境显式增加`-Pcloud-ecj`；本地IDE不要选择旧Eclipse编译器。

没有配置文件时，后端默认`local` profile，监听8080，使用工作目录下`data/`中的持久H2数据库，并仅在本地插入演示商品。首次启动没有提供管理员口令时，会生成强随机初始密码并保存到`data/bootstrap-admin.txt`；**日志只提示该文件的绝对路径，不打印密码**。管理员默认为`admin`，以文件中的实际账号为准；数据库已有管理员后不会重置。

另开终端启动网页：

```bash
npm ci --prefix web
npm run dev --prefix web
```

网页默认5173端口，首页为商城，`/admin`为运营后台。已提供不含密钥的`web/.env.development`，开发API代理默认指向本机8080。无需先手动复制环境模板。自定义地址写入`web/.env.local`；本项目的Vite配置明确以该文件覆盖模式默认，进程环境变量优先级最高。

**本地演示不要直接复制生产模板**：模板启用`prod`及真实渠道校验。Java会在Spring启动前自动读取根`.env`，从`server`启动也会发现父项目配置；IDE、JAR及管理脚本遵守相同规则。需要临时忽略已有文件时，在进程环境设置显式空值`APP_CONFIG_FILE=`。存在配置文件时，本地数据和密钥默认保存在该配置目录的`data/`，避免IDE与命令行使用不同数据库。

开发环境有明确标记的测试登录、付款及出款入口，不发生真实资金交易；生产模式禁用这些入口。演示身份不会被标记为真实微信实名用户。

## 目录和职责

```text
pom.xml                         Maven聚合入口，导入server模块
server/src/main/java/com/sujian/mall/
  Application.java              应用入口
  controller/                   按商品、会员、订单、财务等拆分HTTP入口
  dto/                          请求校验及入参转换
  service/                      业务接口
  service/impl/                 事务、状态机及业务实现
  entity/                       MyBatis-Plus表实体
  mapper/                       BaseMapper及领域SQL
  integration/                  微信、物流等外部适配
  common/                       异常、JSON、ID、统一基础能力
  config/                       本地/生产配置与启动校验
  job/                          Outbox及维护任务
web/src/
  router/                       显式路由和权限守卫
  store/                        Pinia会话、配置、购物袋、界面状态
  views/                        独立商城及后台页面
  layouts/                      商城/管理布局
  components/                   复用展示与业务组件
  composables/                  页面相关组合逻辑
  api/                          领域接口，使用request.post/get等
  utils/                        Axios封装、格式化、会话和导出工具
  styles/                       按领域拆分的公共及页面样式
mini/src/
  pages/                        独立uni-app原生页面
  store/                        Pinia状态
  components/                   复用组件
  api/                          领域API
  utils/                        Axios原生适配器及路由封装
```

Controller不承担SQL或完整业务流程，持久化查询放在领域Mapper；事务和业务规则放在ServiceImpl。普通CRUD使用MyBatis-Plus实体及BaseMapper，涉及锁、预算、批次和对账的SQL明确放在Mapper。网页视图通过领域API调用；`utils/request`集中处理Axios实例、令牌、幂等头、响应解包、错误及401。小程序使用Axios的`uni.request`适配器，不依赖浏览器XHR。

## 微信小程序

```bash
npm ci --prefix mini
cp mini/.env.example mini/.env.local
# 在mini/.env.local填写实际AppID、API及素材域名
npm run typecheck --prefix mini
npm run test --prefix mini
npm run build:mp-weixin --prefix mini
```

微信开发者工具导入`mini/dist/build/mp-weixin`。小程序用`pages.json`定义原生路由，`utils/router`统一登录返回和鉴权；详情见[mini运行说明](mini/README.md)。开发模式支持显式测试身份；生产微信授权、支付、收款确认和真机仍需要有效AppID、合法HTTPS域名及商户权限。

## 业务能力

- 网页/小程序手机验证码登录、首次自动注册、微信与手机号初次绑定，账户冲突明确拒绝。
- 姓名/身份证二要素实名认证、敏感信息加密与脱敏展示、提现实名和微信绑定门禁；阿里云服务通过环境变量接入。
- 商品分类、搜索、上下架、SKU、成本、库存、商品级积分和佣金上限。
- 持久购物袋、地址、服务端报价、价格与库存复核、订单快照、幂等创建和超时关闭。
- 微信JSAPI、网页Native扫码和移动H5支付，v3签名、平台验签、AES-GCM回调解密、支付渠道与应用快照、金额/币种/付款人校验、原单查单及关单。
- 发货、运单、可配置快递100轨迹、收货、分项多次退款、累计限额与权益冲回。
- 积分和佣金分账、冻结、追偿、只追加账本、唯一事件、账本重建与渠道对账。
- 两级成交分销、商品毛利及预算约束、售后期结算、退款重算和已出款追偿。
- 提现冻结、财务核税审核、另一管理员执行、商家转账、查单和失败释放。
- 月度候选、预算、双人复核、退款失效或工单；独立生产开关。
- 管理员/运营/财务独立账号、BCrypt、哈希会话、限流、停用撤销、审计与CSV导出。

初始分销规则为一级5%、二级2%，受商品7%上限与可分配毛利30%约束；确认收货后7天结算。规则有版本和双人审批，不追溯修改已下单快照；它替代旧文档80%消费解锁模型。详见[分销规则](docs/distribution.md)。

商品、会员记录和后台管理列表使用服务端分页、搜索和筛选，显示总量、当前页和重试入口；两级团队人数与待结佣金按完整账户汇总。CSV导出当前页并防止表格公式注入。SKU停用保留订单、库存及退款历史。

## 验证与生产配置

```bash
./mvnw -DAPP_CONFIG_FILE= clean verify
npm run test --prefix web
npm run build --prefix web
npm run test --prefix mini
npm run typecheck --prefix mini
npm run build:mp-weixin --prefix mini
python3 -m unittest discover -s scripts/tests -v
python3 scripts/smoke-local.py
python3 scripts/smoke-configured.py
```

- [本地IDE与Java21配置](docs/development.md)
- [全部环境变量清单](docs/configuration.md)
- [架构与事务边界](docs/architecture.md)
- [部署、备份及恢复](docs/operations.md)
- [验证记录与外部验收边界](docs/verification.md)
- [OpenAPI3.1](docs/openapi.json)

生产使用`python3 scripts/manage.py up`，不要绕过配置生成直接启动基础Compose。入口强制关闭开发模式，生成私有运行配置并只读挂载密钥；密钥不会进入前端、镜像或Git。配置变化后再次执行同一入口即可更新服务。

生产不会自动生成管理员口令或插入演示数据。真实微信/短信/实名/物流的开通及小额联调、真机、生产负载和实际税务申报需使用正式账户验收；受控协议测试结果详见[验证记录](docs/verification.md)。

手机登录与实名配置见[账户认证说明](docs/account-authentication.md)。生产还需开通短信签名、验证码模板和实名认证产品并填入服务端凭据；开发短信及实名模拟不代表已经通过真实渠道验收。

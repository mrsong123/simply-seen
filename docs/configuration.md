# 配置清单与自动读取

项目使用一份根配置文件。复制`.env.example`为`.env`，填写真实参数并放好密钥文件，然后执行`python3 scripts/manage.py check`及`python3 scripts/manage.py up`。Windows可用`py -3`替代`python3`。构建、前端API、小程序AppID、回调地址、Docker挂载由同一入口处理，不需要修改Java、Vue、Nginx或Compose源码。

## 读取规则

1. `manage.py --config 路径`或进程/启动参数`APP_CONFIG_FILE`显式选择文件；未指定时，管理脚本读取仓库根`.env`，Java读取工作目录`.env`，在`server`目录启动时也会发现父级项目根`.env`。
2. 命令行和进程环境覆盖文件值。变量及密钥的相对路径相对于实际配置文件目录；没有配置文件时，Java使用工作目录，管理脚本使用仓库根。`.env`按UTF-8数据读取，支持BOM、CRLF、中文、引号、多行和JSON，不执行shell、反引号、`$()`或变量插值。
3. 配置项可使用`变量名_FILE`读取UTF-8文件，例如`SMS_ACCESS_KEY_SECRET_FILE=./secrets/sms-secret.txt`。单个文件不超过64KiB，允许末尾一个换行；其他空格保留。同一来源的非空直接值优先，较高来源的显式空值不会读取较低来源的密钥文件。
4. `APP_CONFIG_FILE=`作为**进程环境中的显式空值**会禁用自动文件发现。构建/测试入口自动设置它并隔离真实商户、数据库和短信凭据。它不是`.env`文件内的递归导入指令。`RESTORE_FILE`是备份路径，不按密钥间接引用处理。
5. Java在决定Spring profile、建立数据源前读取配置；直接IDE/Maven/JAR启动同样适用。无`.env`时仍为local演示环境，无需商户参数。生产使用`prod`且`APP_DEV_MODE=false`，错误配置启动拒绝，开发短信/实名/付款不能成为生产凭证。

不要复制真实配置或密钥到Git、前端`VITE_*`、镜像或聊天中。管理脚本把所需微信文件复制到权限受控的`.tools/deploy`并只读挂载；运行Compose文件只保存变量引用，不写入实际密钥。应用数据、原配置、备份和`.tools`均不进入构建上下文。

## 首次生产部署需要填写的内容

| 组 | 手动填写/提供 |
|---|---|
| 站点 | `PUBLIC_STORE_URL`、`SUPPORT_CONTACT`、`PRIVACY_CONTACT`；自动HTTPS填写`ACME_EMAIL` |
| 内置数据库 | `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`；库名/账号已有非秘密默认值 |
| 管理员 | `ADMIN_BOOTSTRAP_PASSWORD`至少12位独立随机口令，账号默认admin |
| 微信 | `WX_APP_ID`、`WX_APP_SECRET`、`WX_MCH_ID`、`WX_MERCHANT_SERIAL`、`WX_PLATFORM_SERIAL`、32字节`WX_API_V3_KEY`及商户私钥、平台公钥/证书文件 |
| 短信 | `SMS_ACCESS_KEY_ID`、`SMS_ACCESS_KEY_SECRET`、已审核`SMS_SIGN_NAME`、验证码`SMS_TEMPLATE_CODE`（模板变量名code） |
| 实名 | `IDENTITY_ACCESS_KEY_ID`、`IDENTITY_ACCESS_KEY_SECRET`；开通Id2MetaVerify二要素核验产品 |
| 数据保护 | `ACCOUNT_DATA_KEY`：安全随机32字节的Base64编码，长期稳定并独立备份 |

外部服务的产品权限、短信签名/模板及域名备案/合法域名属于服务商配置。正确填写变量不能替代服务商开通、微信审核或真实小额联调。网站初始没有真实商品，需要在后台录入商品与库存；这属于日常经营数据，不需要改代码。

首次生成ACCOUNT_DATA_KEY可执行`python3 -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"`，把结果仅存入受控配置或密钥文件，不要在重启或恢复时重新生成。

## 自动派生与部署方式

| 变量 | 默认与作用 |
|---|---|
| `PUBLIC_API_URL` | 留空为`PUBLIC_STORE_URL/api/v1` |
| `PUBLIC_ASSET_URL` | 留空为`PUBLIC_STORE_URL`；小程序素材与远程商品图片域名加入微信白名单 |
| `WX_PAY_NOTIFY_URL` / `WX_REFUND_NOTIFY_URL` / `WX_TRANSFER_NOTIFY_URL` | 留空分别派生为`PUBLIC_API_URL/callbacks/wechat/pay`、`refund`、`transfer`；也可显式覆盖 |
| `DATABASE_MODE` | embedded使用项目MySQL8.4；external接已有MySQL |
| `MYSQL_DATABASE` / `MYSQL_USER` | 内置库与应用账号默认sujian |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | embedded留空由MYSQL变量派生；external必须填写实际JDBC URL及账号。URL不内嵌账号密码 |
| `DATABASE_CA_PATH` | 可选私有数据库CA PEM/证书链；仅MySQL数据源加载为独立信任库，不影响微信/短信HTTPS信任。设置后sslMode需VERIFY_CA或VERIFY_IDENTITY，推荐后者 |
| `DATABASE_CLIENT_NETWORK` | 外部数据库备份客户端使用的Docker网络，默认本项目网络；通常留空 |
| `HTTPS_MODE` | caddy自动申请/续签HTTPS；certificate读取已有证书；external使用已有HTTPS代理 |
| `ACME_EMAIL` | 自动证书续签通知邮箱；域名DNS应先指向服务器并开放80/443 |
| `TLS_CERT_PATH` / `TLS_KEY_PATH` | certificate模式的有效站点证书链与私钥，只读挂载给HTTPS入口 |
| `PORT` | Java监听端口默认8080；镜像、健康检查和默认代理自动使用该值 |
| `API_UPSTREAM_URL` | 留空为`http://server:PORT`；不含路径、查询或凭据，不能以/结尾 |
| `WEB_BIND` | 网页内部代理绑定，默认127.0.0.1:8088；external代理转发到此处 |
| `HTTP_BIND` / `HTTPS_BIND` | 内置HTTPS入口主机绑定，默认0.0.0.0:80和0.0.0.0:443 |
| `APP_NETWORK_CIDR` | 专用Docker私有IPv4网段，默认172.30.10.0/24；冲突时改这一项，无需编辑Compose |
| `TRUSTED_PROXY_CIDR` | 留空信任上述专用网段；external模式按实际代理来源设置。禁止0.0.0.0/0及::/0，避免伪造来源绕过短信IP限制 |
| `COMPOSE_PROJECT_NAME` | 默认simply-seen，决定容器/数据卷归属；已有生产数据后不要随意改名 |
| `CORS_ORIGINS` | 同域留空；跨域填逗号分隔的可信网页Origin，不使用通配符 |

`manage.py build/up`从这些值构建网页及小程序：网页同域`/api/v1`，小程序使用`PUBLIC_API_URL`、`PUBLIC_ASSET_URL`、`WX_APP_ID`。旧的`web/.env.local`或`mini/.env.local`不会覆盖本次管理入口传入的构建参数。直接单独运行npm仍可按各客户端README配置本地开发文件。

## 微信支付、转账与物流

| 变量 | 配置内容 |
|---|---|
| `WX_LOGIN_URL` | 已预填官方code换身份端点；AppSecret仅服务端使用 |
| `WX_PRIVATE_KEY_PATH` | 默认`./secrets/wechat/merchant-private.pem`；2048位以上RSA PKCS#8/PKCS#1私钥 |
| `WX_PLATFORM_PUBLIC_KEY_PATH` | 默认`./secrets/wechat/platform-public.pem`；对应平台RSA公钥或有效X509证书 |
| `WX_MERCHANT_SERIAL` / `WX_PLATFORM_SERIAL` | 分别对应商户签名凭据及平台公钥/证书ID，不可混用；平台轮换时同步配置和文件 |
| `WX_PAY_BASE_URL` / `WX_TRANSFER_BASE_URL` | 已预填官方HTTPS基址；无尾斜线 |
| `WX_WEB_PAYMENT_MODE` | native默认网页扫码；h5在移动网页使用微信H5，桌面仍可扫码。商户需开通所选产品 |
| `WX_NATIVE_APP_ID` / `WX_H5_APP_ID` | 可选网页支付产品AppID，空值回退WX_APP_ID；历史订单保存其支付AppID快照 |
| `WX_H5_APP_NAME` / `WX_H5_ALLOWED_HOSTS` | H5网站名称默认素见好物；允许跳转域名默认wx.tenpay.com |
| `WX_TRANSFER_SCENE_ID` / `WX_TRANSFER_PERCEPTION` | 实际开通的转账场景及允许的收款感知文案 |
| `WX_TRANSFER_REPORT_INFOS` | 该场景实际要求的非空JSON数组，每项含info_type和info_content；支持带空格和中文，无需shell转义 |
| `LOGISTICS_QUERY_URL` / `LOGISTICS_CUSTOMER` / `LOGISTICS_KEY` | 配置快递100完整HTTPS端点、账户与签名密钥 |
| `LOGISTICS_CARRIER_CODES` | 后台物流名称到服务商编码的非空JSON对象；未配置时仅展示真实录入运单，不伪造轨迹 |

网页Native/H5不要求先绑定小程序微信。小程序JSAPI及真实提现需要绑定真实微信；提现同时检查真实二要素认证。转账真实姓名只在服务端读取并按平台公钥加密，创建/查询/通知按各自契约核验，确认收款包不会被不含该字段的查询覆盖。

## 手机、实名与业务开关

| 变量 | 内容 |
|---|---|
| `SMS_PROVIDER` / `IDENTITY_PROVIDER` | 生产为aliyun；local未配置默认development且界面明确标记，生产拒绝开发凭证 |
| `SMS_API_URL` / `IDENTITY_API_URL` | 已预填官方RPC HTTPS端点，不能含查询参数；区域默认模板cn-hangzhou |
| `SMS_REGION_ID` / `IDENTITY_REGION_ID` | API区域参数，模板已填 |
| `SMS_DAILY_LIMIT` | 系统每日短信预算，默认1000，范围1至1000000 |
| `IDENTITY_MAX_ATTEMPTS_PER_DAY` | 每会员每天核验上限，默认5，最多10 |
| `IDENTITY_PENDING_TTL_SECONDS` | 处理中租约默认120秒，限制60至600秒 |
| `IDENTITY_RETRY_AFTER_SECONDS` | 失败冷却默认60秒，限制30至300秒 |
| `IDENTITY_REQUIRED_FOR_WITHDRAWAL` | 默认true；生产不可通过false绕过 |
| `DISTRIBUTION_ENABLED` / `MONTHLY_ENABLED` / `TRANSFER_ENABLED` | 分销、月度活动、提现/真实转账开关。启用前配置预算、场景和相应后台审批 |
| `WITHDRAW_MIN_FEN` / `WITHDRAW_MAX_FEN` | 税前提现范围，默认1000至200000分，最大支持2000元并受实际商户权限限制 |

验证码有效300秒，冷却60秒，5次错误锁定，单次使用；手机每小时5条/每天10条、IP每分钟3条/每小时20条，并受系统预算限制。认证结果仅表示姓名与身份证信息匹配，不包含人脸活体；错误、未知或超时不会通过。`ACCOUNT_DATA_KEY`更换需要受控迁移和重建指纹，不能通过简单改变量轮换。

## 其它运行与维护变量

`SESSION_HOURS`默认168；`ORDER_EXPIRE_MINUTES`默认15；`JOB_INTERVAL_MS`默认10000；`MAINTENANCE_INTERVAL_MS`默认60000；`JOB_INITIAL_DELAY_MS`默认10000；`API_DOCS_ENABLED`生产默认false。本地初始管理员文件可用`APP_BOOTSTRAP_CREDENTIALS_PATH`配置；本地账户密钥文件可用`ACCOUNT_DATA_KEY_PATH`配置，均默认在配置目录data下，仅开发时自动生成。

`JAVA_HOME`优先选择Java；构建要求完整JDK21、Node22.12+、Python3.11+和Docker Compose2.24+。`SUJIAN_TOOLS_DIR`、`MAVEN_SETTINGS`为可选构建缓存/设置；Maven不存在时下载固定版本并校验SHA-512。`JAVA_OPTS_JSON`用于单独运行JAR的非敏感JVM参数数组，`JDK_JAVA_OPTIONS`用于容器非敏感参数，不能放业务密钥。`BACKUP_DIR`为备份路径，恢复使用`--file`/`RESTORE_FILE`并显式`--confirm`。

网络需允许实际包管理源、微信登录/支付域名、短信/实名域名、物流端点及自有API/素材；Caddy自动HTTPS还需访问ACME服务。配置保存或语法检查不等于这些网络目标已经可达。真实渠道验收、证书签发、微信发布及税务申报需按实际平台流程完成。

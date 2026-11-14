# 环境变量清单

外部服务基址、回调地址、前端API入口和素材域名均从环境变量读取；内部业务路由在OpenAPI中定义。密钥只配置在服务端环境或只读文件，不能以`VITE_`前缀传入前端。仓库的`.env.example`只列名称与非秘密示例，不包含真实凭据。

## 服务端（部署环境变量或显式加载根目录.env）

IDE直接运行使用Spring的local默认配置和Run Configuration环境变量；不会自动执行根.env。`scripts/run-server.sh`会显式加载根.env。生产模板的prod配置不能当作本地开箱配置。

| 变量 | 用途与要求 |
|---|---|
| SPRING_PROFILES_ACTIVE | 默认local供本地开发；生产明确设prod。IDE直接启动不自动读取根.env |
| APP_DEV_MODE | local默认true；生产必须false。prod profile强制禁止模拟能力，并校验真实数据库/渠道配置 |
| PORT | API监听端口，默认8080 |
| DATABASE_URL | JDBC连接。生产必须jdbc:mysql；建议sslMode=VERIFY_IDENTITY并配置受信CA，Compose自签名内网可先使用REQUIRED并在生产替换为校验身份的证书配置 |
| DATABASE_USER / DATABASE_PASSWORD | 数据库最小权限账号与凭据。迁移账号需DDL，运行权限可按运维策略分离 |
| ADMIN_BOOTSTRAP_USER / ADMIN_BOOTSTRAP_PASSWORD | 首次启动创建管理员。local未配置时生成强随机私有文件；生产首启必须提供至少12位口令。已有管理员后不重置密码 |
| APP_BOOTSTRAP_CREDENTIALS_PATH | local初始凭据文件路径，默认启动工作目录data/bootstrap-admin.txt；日志提示绝对路径，不打印口令，不能把该文件提交Git |
| SESSION_HOURS | 登录会话有效小时，默认168 |
| ORDER_EXPIRE_MINUTES | 未支付订单有效分钟，默认15；超时不能跳过渠道确认 |
| JOB_INTERVAL_MS | Outbox轮询间隔，默认10000毫秒 |
| MAINTENANCE_INTERVAL_MS | 订单超时及佣金结算轮询，默认60000毫秒 |
| JOB_INITIAL_DELAY_MS | 启动后任务初次延迟，默认10000毫秒 |
| API_DOCS_ENABLED | 开发可true，生产建议false，控制动态OpenAPI和Swagger入口 |
| CORS_ORIGINS | 逗号分隔的可信网页Origin；同域反向代理无需填写，不使用通配符 |
| SUPPORT_CONTACT | 真实客服联系方式，生产必填 |
| PRIVACY_CONTACT | 个人信息权利请求联系方式，生产必填 |
| PUBLIC_STORE_URL | 公开商城HTTPS地址，生成分享链接，生产必填 |
| DISTRIBUTION_ENABLED | 环境层允许发布分销规则，默认false。已有订单快照与承诺不会因该开关自动作废 |
| MONTHLY_ENABLED | 月度候选及复核开关，默认false |
| TRANSFER_ENABLED | 提现和真实转账开关，默认false |
| WITHDRAW_MIN_FEN / WITHDRAW_MAX_FEN | 单次税前提现范围，默认1000至200000分。最大额必须符合实际开通的转账场景条件 |
| JAVA_OPTS | 本地运行脚本的JVM参数，例如内存、受信证书库；不能将密钥值放到命令行参数 |

## 手机验证码登录与姓名/身份证核验

网页与小程序均支持中国大陆手机号验证码登录，首次验证成功自动注册。已有微信会员应先通过微信登录并绑定手机号，随后可使用该号码登录原账户；系统不会合并两个已有账户的订单、资金和邀请关系。

| 变量 | 用途与要求 |
|---|---|
| SMS_PROVIDER | 生产为aliyun；local未配置时为development，返回明确标记的本次随机测试码，生产不能使用该模式 |
| SMS_API_URL | 阿里云短信RPC HTTPS端点，示例`https://dysmsapi.aliyuncs.com/`，不带查询参数或密钥 |
| SMS_ACCESS_KEY_ID / SMS_ACCESS_KEY_SECRET | 有SendSms权限的阿里云RAM凭据，仅服务端使用 |
| SMS_REGION_ID | API区域参数，示例cn-hangzhou，显式填写 |
| SMS_SIGN_NAME | 已审核的短信签名 |
| SMS_TEMPLATE_CODE | 已审核的验证码模板，模板变量使用code |
| SMS_DAILY_LIMIT | 系统每日短信预算上限，默认1000条，必须为正整数；手机及IP限制另行生效 |
| IDENTITY_PROVIDER | 生产为aliyun；local默认development，仅模拟信息核验，不能作为生产实名结果 |
| IDENTITY_API_URL | 阿里云实名认证RPC HTTPS端点，示例`https://cloudauth.aliyuncs.com/` |
| IDENTITY_ACCESS_KEY_ID / IDENTITY_ACCESS_KEY_SECRET | 已开通Id2MetaVerify产品及其权限的RAM凭据 |
| IDENTITY_REGION_ID | API区域参数，示例cn-hangzhou，显式填写 |
| IDENTITY_MAX_ATTEMPTS_PER_DAY | 每会员每天最大核验次数，默认5，上限10，重复查询同一次处理中请求不重复调用渠道 |
| IDENTITY_PENDING_TTL_SECONDS | 处理中请求租约，默认120秒，范围60至600秒；超时保留未知事实，再显式重试 |
| IDENTITY_RETRY_AFTER_SECONDS | 失败后再次核验的冷却，默认60秒，范围30至300秒 |
| ACCOUNT_DATA_KEY | Base64编码的32字节安全随机密钥，用于AES-256-GCM加密及独立上下文HMAC指纹；生产必填，不能下发前端 |
| ACCOUNT_DATA_KEY_PATH | local未配置密钥时生成并复用的私有文件，默认data/account-data.key；生产不自动生成密钥 |
| IDENTITY_REQUIRED_FOR_WITHDRAWAL | 默认true；生产始终强制实名，不能通过false绕过。false仅供隔离开发及历史财务回归测试 |

短信验证码有效期300秒，重发冷却60秒，最多5次错误；消费后不能再次使用。手机每小时最多5条、每日10条；IP每分钟最多3条、每小时20条，并受系统日预算限制。验证码只保存带密钥的摘要，不记录到日志；本地开发码仅通过development响应下发并明确显示，生产拒绝消费开发模式生成的验证码。

真实姓名及身份证号在服务端加密保存，客户端只读取脱敏信息和状态。实名认证使用姓名和18位大陆居民身份证二要素匹配，需要用户明确同意；不包含人脸活体核验，也不能由此证明微信收款人与填写的身份证是同一个人。只有渠道明确匹配才通过，超时或异常不能作为通过依据。开发模拟结果不能用于生产提现；生产提现还要求绑定真实微信身份。

`ACCOUNT_DATA_KEY`必须在重启、扩容和恢复时保持一致，并与数据库备份分开安全保存。丢失或直接更换它会导致历史密文无法解密、手机号及证件指纹无法匹配；更换需先制定受控数据迁移方案，不能每次部署生成新值。不要在聊天、截图、日志或Git中提供凭据及实际姓名/证件资料。

## 微信登录、支付与商家转账

| 变量 | 配置内容 |
|---|---|
| WX_APP_ID / WX_APP_SECRET | 小程序身份及服务端密钥，生产必填。AppSecret不下发客户端 |
| WX_LOGIN_URL | 微信code换身份完整HTTPS端点；示例为`https://api.weixin.qq.com/sns/jscode2session` |
| WX_MCH_ID | 商户号，与小程序关联并已开通所用产品 |
| WX_MERCHANT_SERIAL | 商户API证书序列号，用于请求签名 |
| WX_PRIVATE_KEY_PATH | PKCS#8 PEM商户RSA私钥绝对路径，只读挂载 |
| WX_PLATFORM_SERIAL | 微信平台公钥ID/序列号，必须与响应头完全一致；部署方维护轮换 |
| WX_PLATFORM_PUBLIC_KEY_PATH | X.509 SubjectPublicKeyInfo PEM平台RSA公钥路径，不是商户公钥，不是私钥 |
| WX_API_V3_KEY | 32字节APIv3密钥，仅服务端解密回调 |
| WX_PAY_BASE_URL | 支付v3 HTTPS基址，示例`https://api.mch.weixin.qq.com`，不带尾斜杠 |
| WX_PAY_NOTIFY_URL | 公网HTTPS完整地址，路径`/api/v1/callbacks/wechat/pay` |
| WX_REFUND_NOTIFY_URL | 公网HTTPS完整地址，路径`/api/v1/callbacks/wechat/refund` |
| WX_TRANSFER_BASE_URL | 商家转账v3 HTTPS基址，与实际产品匹配 |
| WX_TRANSFER_NOTIFY_URL | 公网HTTPS完整地址，路径`/api/v1/callbacks/wechat/transfer` |
| WX_TRANSFER_SCENE_ID | 已开通的真实转账场景ID，按商户实际权限填写，不能猜测 |
| WX_TRANSFER_PERCEPTION | 对应场景允许的收款感知文案 |
| WX_TRANSFER_REPORT_INFOS | 该场景要求的JSON数组，如键名`info_type`、`info_content`。实际内容按商户场景要求填写；不是任意通用示例 |

转账使用微信`/v3/fund-app/mch-transfer/transfer-bills`产品接口；支持WAIT_USER_CONFIRM时原生确认收款。当前实现按无需加密收款人姓名的额度/场景使用，默认单笔不超过2000元；若商户产品要求额外实名字段，必须先按实际渠道契约扩展并联调，不应通过抬高最大额绕过该要求。税务申报由商户实际税务流程完成，本系统保留经财务核实的税款及审计事实。

## 网页（web/.env.local，构建时读取）

| 变量 | 内容 |
|---|---|
| VITE_API_BASE_URL | 必填。API完整基址或同域`/api/v1`。不得填密钥 |
| API_PROXY_TARGET | Vite本地代理的服务端地址，例如`http://127.0.0.1:8080`；仅本地开发使用 |
| WEB_PORT | 本地Vite端口，默认5173 |

生产网页支持公开浏览及手机验证码登录。当前支付渠道为微信小程序支付，手机账户应在小程序以同一手机号登录并绑定微信，再完成支付/提现；网页不会伪造可用的微信JSAPI能力。已有微信用户先在原微信账户绑定手机号，避免重复创建账户。管理后台继续使用独立账号和密码。

## 小程序（mini/.env.local，构建时读取）

| 变量 | 内容 |
|---|---|
| VITE_API_BASE_URL | 必填，真实HTTPS API基址，含`/api/v1`，加入微信request合法域名 |
| VITE_ASSET_BASE_URL | 必填，素材HTTPS基址，承载`/art/*.png`，加入downloadFile合法域名；远程商品图片也需合法域名 |
| WX_APP_ID | 必填，构建时写入产物project.config.json；源manifest在构建结束恢复 |

小程序微信隐私后台需要声明微信身份标识、收货信息等实际处理范围。地址导入为用户主动操作，不要求定位权限。生产构建需换成真实AppID，`.env.example`中的格式示例不代表可提审账号。

## Docker与运维

| 变量 | 内容 |
|---|---|
| MYSQL_ROOT_PASSWORD | MySQL初始化root凭据，强随机，仅数据库管理员持有 |
| MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD | 初始化库及应用账号，需与JDBC配置一致 |
| WEB_BIND | 反向代理绑定，例如`127.0.0.1:8088`，避免绕过外层HTTPS入口 |
| API_UPSTREAM_URL | Nginx到服务端的内部基址，例如`http://server:8080` |
| TRUSTED_PROXY_CIDR | Nginx可采信客户端IP的外层代理来源CIDR，默认127.0.0.1/32；按实际边缘代理地址填写，不可用0.0.0.0/0。Nginx重写X-Forwarded-For并清除客户端Forwarded，避免伪造来源绕过短信限流 |
| BACKUP_DIR | 备份脚本输出目录，应为受控加密挂载 |
| RESTORE_FILE / RESTORE_CONFIRMED | 恢复文件及人工确认标记YES；仅恢复脚本读取 |
| SUJIAN_TOOLS_DIR | Maven wrapper缓存目录，可选，默认项目.tools |
| E2E_ADMIN_CREDENTIALS_PATH | 浏览器验收读取本地初始凭据文件的路径，仅测试进程使用，不下发前端 |
| E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD | 浏览器验收可显式提供管理账号，优先于本地文件；不要写入测试报告或Git |
| TEST_DATABASE_URL | 集成测试目标；只允许专用可清空测试库，测试会删除业务表内数据 |

网络目标：包管理源、微信登录域名、商户支付域名、短信与实名认证端点，以及自有API与素材域名。已有环境只放行包管理源时，应在环境设置添加`api.weixin.qq.com`、`api.mch.weixin.qq.com`、`dysmsapi.aliyuncs.com`、`cloudauth.aliyuncs.com`及实际自有域名。配置文件保存不会证明这些域名已被当前运行实例放行。

## 物流轨迹（快递100实时查询协议）

`LOGISTICS_QUERY_URL`为完整HTTPS查询端点，官方协议示例为`https://poll.kuaidi100.com/poll/query.do`；`LOGISTICS_CUSTOMER`、`LOGISTICS_KEY`为服务商账户与签名密钥。`LOGISTICS_CARRIER_CODES`为后台发货名称到服务商编码的JSON映射，例如`{"顺丰速运":"shunfeng","顺丰":"shunfeng","中通快递":"zhongtong"}`。实际编码和权限按服务商账号确认。服务端按该协议计算参数签名，收货电话仅发送给实际履约轨迹服务，响应只下发时间与轨迹文本。未配置时仅展示已录入运单，不伪造轨迹；配置后小程序和商城订单详情提供实际查询。网络需放行实际物流端点域名。

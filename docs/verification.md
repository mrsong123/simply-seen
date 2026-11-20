# 验证与交付记录

验证日期：2026-11-19（America/Los_Angeles）。本记录针对统一配置部署、手机号/实名、网页支付、二级分销、服务端分页和前后端动作收尾；所有生产外部账户仍由经营方配置。

## 构建和回归证据

| 检查 | 已执行结果 | 证据 |
|---|---|---|
| 完整JDK21默认构建 | 官方Temurin21镜像，根聚合POM、默认javac、release21，clean verify与可运行JAR构建成功 | `docs/test-reports/java21-compilers.txt` |
| H2回归 | 150项，0失败、0错误；仅真实MySQL专用协议用例跳过1项 | `docs/test-reports/h2/` |
| MySQL8.4回归 | 150项全部通过，无跳过，六个Flyway迁移实际执行 | `docs/test-reports/mysql/` |
| 网页 | 55项单元测试、vue-tsc及Vite生产构建通过 | `docs/test-reports/web.txt` |
| 小程序 | 66项单元测试、静态类型和微信生产构建通过，16页产物存在 | `docs/test-reports/mini.txt` |
| 配置与运维入口 | Python配置测试21项，覆盖解析、优先级、密钥隔离、部署生成、备份完整性回归通过；三种HTTPS和外部数据库CA的Compose静默校验通过 | `docs/test-reports/configuration-cli.txt` |
| 无配置最终JAR | 独立临时目录真实HTTP交易、鉴权/门禁、分页、手机/实名、重启持久化和缺配置prod拒绝通过 | `docs/test-reports/local-startup.txt` |
| 有配置最终JAR | 根.env及server父目录自动发现、BOM/CRLF/中文/JSON、相对_FILE、进程覆盖、跨重启同一账户/数据库/密钥及纯进程_FILE/PORT_FILE通过 | `docs/test-reports/configured-startup.txt` |
| 容器 | Java与Nginx镜像、非root、默认/自定义端口健康探针、页面/history路由、真实API代理、手机/脱敏开发实名和密钥600权限通过 | `docs/test-reports/release-containers.txt` |
| 最终配置生产栈 | 实际manage.py up --skip-build启动MySQL/Java/Nginx/Caddy；严格CA/SAN/SNI的HTTPS、关闭开发入口、管理员_FILE登录、商品/SKU保存及公开回读通过；最终JAR哈希与编译产物一致 | `docs/test-reports/configured-production-stack.txt` |
| 运维恢复 | 外部MySQL真实备份、摘要和gzip验证、独立测试库恢复、全表及账本比对、坏备份拒绝通过 | `docs/test-reports/release-backup-restore.txt` |
| API契约 | 逐项核对77个应用操作与3个微信回调，OpenAPI3.1静态文件和源生成结果一致 | `docs/openapi.json` |

完整生产栈实测还修复了三个部署阻塞：健康探针继承应用Xms与小堆上限冲突、网页loopback探针误走环境代理、HTTPS入口向内部web反代误走外部代理。最终探针显式使用小堆参数，仅对内部通信追加代理例外，保留原有外部代理及TLS验证。隔离栈down后只清除其测试卷和网络，已有容器未被修改。

H2专用跳过不代替MySQL证据；MySQL测试使用可清空的独立测试数据库，未连接生产库。普通IDE使用完整JDK21及Javac；只有JRE的云环境可显式启用cloud-ecj。Windows启动器与配置测试已纳入GitHub Actions，本Linux会话没有宣称执行用户本机或Windows验收。

## 浏览器与动作审查

Chromium验收共10项：4项使用真实本地Java API，覆盖手机注册/登录/初绑/实名持久化、完整交易退款、移动商城和后台角色协作；另外6项明确控制API响应，覆盖首页加载/空/错误恢复、Native二维码、H5跳转、过期付款门禁、会员账本筛选和奖励路由。受控界面响应不作为真实资金或身份事实。最终结果见`docs/test-reports/browser.txt`。

真实后台流程包括：分类和双SKU创建、规格停用与历史回读、导出、三角色建号、运营API403、规则异人审批、停用后会话失效、重新启用、密码重置与审计检索。下拉框提供明确可访问标签；测试等待真实分页搜索响应，避免把旧页面数据误当查询完成。

逐页/逐按钮的数据落点、动作、权限与失败恢复分别归档：

- [网页动作矩阵](web-action-audit.md)
- [小程序16页动作矩阵](mini-action-audit.md)
- [后台业务与状态机矩阵](backend-action-audit.md)

商品、会员及后台使用服务端分页，不再依赖固定前100/200/500条。会员不能读取他人记录，角色由服务器再次校验；会员交易投影不返回商品成本、私有佣金预算/受益快照或支付人openid。页面错误可重试，旧路由与旧会话请求不能覆盖当前账户。业务源码检查无TODO、未实现异常或伪成功入口；表单提示和明确空数据状态不属于占位功能。

## 关闭开发模式后的协议与业务闭环

`ProductionProtocolTest`运行prod、APP_DEV_MODE=false、真实MySQL以及正式业务服务。仅供应商由测试目录内的受控HTTPS服务器提供，使用每轮独立的CA、商户与平台RSA密钥；没有关闭TLS或生产门禁。详见[协议验收](production-acceptance.md)。

实际核对阿里云短信/身份RPC签名、微信code交换、JSAPI/Native/H5请求签名、平台响应验签、AES-GCM支付/退款/转账通知、金额/币种/商户/应用/原支付交易号、JSAPI绑定付款人，以及Native/H5独立付款人。退款通知按官方refund_status解析，缺少币种时查原单补证；已有冲突信息不能被查询洗掉。商家转账实名按RSA-OAEP加密，创建/查询/通知各自缺少的字段不造成错误拒绝或清空确认包。

通过真实API/服务创建手机账户和两级归因链，完成下单、预付、可信支付、二级佣金、发货/收货、观察期结算、实名/微信提现门禁、财务与执行人分离、待确认包保留、最终到账、退款佣金冲回和已出款追偿、后续佣金抵债、月度异人复核与账本/渠道对账。

佣金预算归属来源订单的上海支付自然月，查询包含起止边界；延迟生成不能挤占邻月，退款不释放原始占用，预算检查在COMMISSION锁内执行。SKU停用保留历史；并发管理员停用保留有效管理账号；同一异常再次出现可重开；未知外部任务保留原单并恢复查询，可信NOT_FOUND才沿同号重试。

## 配置和交付边界

按[配置清单](configuration.md)填写一份.env和密钥文件后，执行`python3 scripts/manage.py check`、`python3 scripts/manage.py up`即可检查、测试、构建并启动；无需修改Java、Vue、Nginx或Compose源码。正式库没有演示商品，需要后台录入真实经营商品和库存。密钥未进入源码、前端或镜像；现有私有配置未被重写。

尚无真实商户和证件服务凭据，因而没有宣称短信真实到达、真实证件匹配、商户小额支付/退款/转账到账、微信真机或审核通过。自动HTTPS配置校验没有向虚构域名申请证书；公开证书签发须真实DNS与网络条件。税额为财务核定记录，实际扣缴申报由经营主体完成。

库存、权限、状态机和预算回归不等于生产负载、弱网真机、可用性SLA或生产RPO/RTO验收。源代码实现与配置部署已完成；正式开通、平台发布与真实渠道联调须使用已授权的实际账号。历史录像见`docs/demo/README.md`，展示较早开发版本，不冒充本轮新增功能录像。

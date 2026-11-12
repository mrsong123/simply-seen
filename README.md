# 素见好物 · Simply Seen

微信小程序、品牌商城网页、PC运营后台与 Java 交易服务。界面采用暖白、低饱和绿、系统字体、细线与留白；素材为项目自有 SVG/PNG，无第三方图片授权依赖。

## 运行

要求 Java 21、Node 22.12+、Python 3、curl。Maven wrapper 固定3.9.11并校验SHA-512；Java编译使用固定版本Eclipse编译器，在仅有JRE21的环境也能构建。

```bash
python3 scripts/dev-env.py
./mvnw -f server/pom.xml verify
npm ci --prefix web
cp web/.env.example web/.env.local
npm run build --prefix web
./scripts/run-server.sh
# 另一个终端
npm run dev --prefix web
```

本地服务端8080、网页5173。首页是商城，`/admin`是管理后台。私有`.env`保存自动生成的初始管理员密码，不在代码或日志中提供默认通用口令。使用`.env`中的账号登录，再创建财务及第二管理员，以验证双人审批。H2只用于本地开发，数据保存在`server/data`，重启保留。

开发模式明确标记测试身份、测试付款与测试出款；生产启动禁用开发入口并检查MySQL、微信配置及密钥文件。生产不会插入演示商品、会员或账本。

```bash
npm ci --prefix mini
cp mini/.env.example mini/.env.local
# 填写真实WX_APP_ID、API及素材HTTPS域名后构建
npm run build:mp-weixin --prefix mini
```

微信开发者工具导入`mini/dist/build/mp-weixin`。小程序原生微信登录、`requestPayment`、商家转账确认收款和分享路径均已接线；真机与商户渠道验收需要真实配置，编译不代表微信审核或资金联调通过。

## 功能

- 商品分类、搜索、上下架、SKU、成本、库存、商品级积分与佣金上限；后台创建/编辑商品和分类。
- 持久购物袋、多地址、微信地址导入、服务端报价、价格/库存复核、订单快照、幂等创建、超时取消。
- 微信支付v3：商户RSA签名、平台公钥验签、AES-GCM回调解密、金额/币种/付款人校验、原单查单与关单。
- 发货、运单展示、可配置快递100轨迹查询、确认收货、商品数量级部分退款、多次退款限额、退款审核、原路退款及回调补偿。
- 可用积分/冻结积分、可用佣金/提现冻结/追偿负债分账；只追加账本，唯一事件去重，账本重建核验。
- 两级成交分销：注册固定归因、可选推广资格、商品毛利护栏、预算、收货后售后期结算、退款重算、已出款追偿。
- 提现冻结、财务审核实际税额、另一管理员执行、商家转账、原单查单、用户确认收款、失败释放与成功核销。
- 月度优惠：上海自然月、合格用户前10%候选、稳定排序、预算及单人500元上限、草稿、双人复核、退款失效或异常工单。
- 管理员/运营/财务独立账号、BCrypt、哈希会话、数据库限流、账号停用撤销登录、强制审计、CSV导出。
- 持久Outbox、租约、指数退避、异常工单、交易/退款/出款渠道查询核对、健康检查、数据库备份恢复脚本。

前端列表会明确显示当前加载条数；会员订单上限100条，管理列表200条，账本及审计500条。CSV导出的是当前加载并筛选的记录，而非全库导出。

## 规则与技术决策

本实现按用户的新要求将文档的一级邀请扩展为两级成交归因，并替换80%消费解锁奖励。规则详见[分销规则](docs/distribution.md)。不含多级代理、入门费、拉人头报酬或强制囤货。

初始规则：一级5%、二级2%；每商品佣金上限7%；毛利最多分配30%；确认收货后7天结算；每满100元合格商品现金消费赠10积分；商品默认最多20%积分抵扣。以上是本实现明确采用的初始设计，不声称已经获得客户、财务或法务签收；可经双人审批发布新版本，旧订单保持旧规则。

后端为Spring Boot 3.5/Java21/JdbcTemplate/Flyway。相较原文档方案，本实现使用SQL会话、限流与任务租约，不依赖Redis；资金正确性由数据库事务和唯一键保证。MySQL8.4用于生产，H2用于快速本地运行；没有微服务或外部消息队列前置依赖。

## 配置、部署与交付

- [完整环境变量清单](docs/configuration.md)
- [架构、状态与事务边界](docs/architecture.md)
- [两级分销与月度规则](docs/distribution.md)
- [部署与运维手册](docs/operations.md)
- [验收与验证结果](docs/verification.md)
- [OpenAPI 3.1](docs/openapi.json)

真实微信支付/转账、生产HTTPS域名、微信隐私声明、运营主体信息及实际税务申报需由商户完成配置和验收。税额由财务审核录入并保存在提现单和审计中；系统不虚构税务机关申报API，也不将内部税额记录标为已完成税务申报。

```bash
# 生产构建及启动，先完整填写根目录.env与web/.env.local
./mvnw -f server/pom.xml verify
npm ci --prefix web && npm run build --prefix web
docker compose up -d --build
```

Compose强制`APP_DEV_MODE=false`。HTTPS由外层可信反向代理或负载均衡终止，Web默认只绑定127.0.0.1。微信私钥和平台公钥挂载到`secrets/wechat`，切勿提交该目录。

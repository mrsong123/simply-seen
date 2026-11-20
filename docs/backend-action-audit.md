# 后台业务动作与数据验收

本表核对实际Controller → Service → Mapper → 数据库动作，不以页面存在或按钮文案作为完成依据。新增回归的最终结果见 `docs/verification.md` 和测试报告。真实支付/转账产品权限仍由对应商户配置与渠道验收决定。

## 权限和持久化矩阵

| 业务动作 | HTTP / 后端入口 | 数据落点及状态 | 服务端门禁 |
|---|---|---|---|
| 商品与SKU新建/编辑/上下架 | POST `/admin/products` | product、sku，事务保存，SKU价格成本库存复核 | ADMIN/OPERATOR，分类必须存在，佣金上限10%，规格编码/ID不可重复 |
| SKU移除 | 同商品保存，省略对应现有SKU | `sku.active=false`保留历史、库存和退款引用；`retired_skus`只读回显 | 已预占held>0不得移除；已停用规格不能新增购物袋、报价或下单 |
| 分类新建/编辑 | POST `/admin/categories` | category及操作审计 | ADMIN/OPERATOR，名称与排序校验 |
| 订单/详情查询与发货 | GET/POST `/admin/orders/...` | 订单快照、order_item、发货状态、物流单及审计 | 运营权限；仅已支付且无进行中售后可发货 |
| 会员停用/恢复 | POST `/admin/members/{id}/block` | member.blocked；停用时删除会话 | 仅ADMIN；目标必须存在；旧会话不随恢复自动复活 |
| 佣金/账本查询、CSV数据来源 | GET `/admin/commissions`、`/admin/ledger` | commission、只追加ledger及账户余额 | ADMIN/FINANCE，会员不可读取他人账本 |
| 退款审核 | POST `/admin/refunds/{id}/review` | refund REVIEWED/CLOSED、Outbox事件、审核审计 | ADMIN/FINANCE，原单退款范围、数量/金额累积、单笔进行中售后保护 |
| 退款渠道处理 | Outbox及微信验签解密回调 | PROCESSING/SUCCESS/ABNORMAL/CLOSED、积分/佣金逆向、库存恢复 | 可信原单号、退款编号、金额、币种、原支付金额，成功交易号不可替换 |
| 分销规则草稿/审批 | POST `/admin/rules`、`/{id}/approve` | rule_version DRAFT→ACTIVE，旧版ARCHIVED | 草稿ADMIN/OPERATOR；审批ADMIN且不同经办人，比例/预算/结算天数护栏，环境开关 |
| 成交佣金结算 | 维护任务 | PENDING→SETTLED；available、debt及唯一事件ledger | 收货后规则观察期结束、无售后、账户未停用；退款追偿不写负可用余额 |
| 提现申请 | POST `/me/withdrawals` | available→reserved，withdrawal REVIEWING | 当前会员、实名、额度/余额、未停用、无追偿债务/未完成积分回收；幂等键 |
| 提现审核/驳回 | POST `/admin/withdrawals/{id}/review` | APPROVED或REJECTED，gross/tax/net及审计 | ADMIN/FINANCE，仅REVIEWING，核定税额须小于税前金额 |
| 提现执行 | POST `/admin/withdrawals/{id}/execute` | SUBMITTED、执行人、持久TRANSFER事件 | 仅ADMIN，执行人不同于审核人；再次实名/微信/债务/停用检查 |
| 转账创建/查单/确认 | Outbox与`/{id}/query`、微信通知 | CONFIRMING/UNKNOWN/SUCCESS/FAILED，确认package持久保存 | 未审核对象不能因查询触发出款；真实姓名加密提交微信；商户/收款openid/金额/原单/交易编号核对 |
| 月度草稿与复核 | POST `/admin/settlements`、`/{id}/approve` | settlement、settlement_award，复核后唯一ledger入账 | 已结束月份、预算、经办与财务复核分离；退款和生成/审批共用事务锁，避免漏失效 |
| 对账 | POST `/admin/reconciliation` | reconcile_batch、issue及审计 | ADMIN/FINANCE，重建账本并核对原渠道状态；无法查询计差异 |
| 异常工单关闭 | POST `/admin/issues/{id}/resolve` | issue OPEN→RESOLVED，处理依据写audit | ADMIN/FINANCE，目标和OPEN状态必须存在；同一问题复发重开原工单 |
| 账号创建/停用/重置密码 | `/admin/accounts/...` | BCrypt口令、角色、active、会话撤销、审计 | 仅ADMIN；禁止自停用/自身代重置；管理员并发停用也保留有效管理员 |
| 个人改密与审计查询 | POST `/admin/password`、GET `/admin/audit` | 原密码验证、会话撤销、无口令的审计事件 | 已登录管理身份；审计查询ADMIN/FINANCE |

列表的跨页读取由记录分页域提供；原列表接口保留兼容。导出由前端根据当前实际加载筛选的数据生成，不会虚构数据库全量导出。

## 本轮修复的实质缺口

1. 商品保存只upsert旧规格，省略的SKU仍会销售。现以V5迁移增加active生命周期，退货仍保留原SKU和库存，预占中的规格不可移除，退役记录有管理侧回读。
2. 两个管理员并发互相停用时，原“最后一位管理员”计数会被竞态绕过。现串行化敏感账号变更并重新检查执行账号有效性。
3. 对不存在的会员/工单操作会记录成功审计。现先锁定并验证目标；已处理工单重复关闭拒绝，同一故障复发能重新开启。
4. 原任务初次外调失败后只查单，明确NOT_FOUND仍可能永久冻结。现仅对可信渠道明确不存在，沿用同一商户单号重提；未知状态不换号、未授权对象查询不会变成创建出款。
5. 商家转账创建响应没有金额/appid/openid字段，查询又没有创建响应的收款package。现区分创建回执和完整查单/通知证据，终态创建结果经完整原单查询后才核销，后续查单不清空已有确认包。
6. 待用户确认和正常退款处理中被当成异常，手动查询返回503。现正常持久状态返回成功，Outbox另行安排后续查询，不误标DONE、不生成伪异常。
7. 退款通知使用`refund_status`，原先只读`status`。现明确归一化并拒绝冲突；通知缺币种等字段时查原退款单取得完整可信证据，不自行补造。
8. 渠道明确关闭的退款原先永久挂ABNORMAL。现记录CLOSED且不计到账，允许会员按剩余实际可退金额重新发起售后；真实ABNORMAL仍保留原单核对。
9. 月度草稿生成/审批与退款并发时，退款可能漏看尚未提交草稿或覆盖已审批状态。现以统一锁序串行化规则/月结/退款判定，已入账事实保留，后续退款生成复核工单。
10. 真实转账使用已核验姓名，经微信平台RSA-OAEP加密和序列号头提交，覆盖2000元阈值要求，允许微信核对收款openid姓名；接口和日志不返回姓名原文。
11. 退款通知中已提供的商户号和原支付交易号先与本地事实核对，再进行缺币种等字段的原单补证；查询不能用另一退款交易编号替换通知中的编号。

## 新增回归

- `BackendCatalogPersistenceTest`：SKU停用、历史订单/退款库存、预占保护、重复规格拒绝。
- `BackendAdministrationTest`：不存在对象、工单复发、会话撤销、并发最后管理员保护、实际HTTP角色与会员门禁。
- `BackendMonthlyConsistencyTest`：月结未提交窗口中的退款最终使草稿失效。
- `ChannelEvidenceTest`：官方退款通知字段、原单查询补证、商户/原支付/退款交易编号冲突拒绝、创建/完整转账回执区别、收款人与商户和金额核对。
- `OutboxRecoveryTest`：同原单恢复、未审核查询不可出款、终态完整证据、确认包保留。

受控渠道协议验收另见 `docs/production-acceptance.md`；该测试不能替代实际商户服务权限、到账、真机或平台审核证据。

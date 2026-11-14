# 分层架构与一致性

后端为Spring Boot3 / Java21 / MyBatis-Plus模块化单体；网页为Vue3 / Pinia / Vue Router / Axios；小程序为uni-app原生页面、Pinia及Axios原生适配器。前后端继续使用同一份OpenAPI契约，金额单位及历史响应字段保持兼容。

## 后端依赖方向

```text
HTTP → controller → service接口 → service.impl → mapper → entity/数据库
                                  ↓
                              integration
job → service接口                 微信 / 物流
```

- `controller`按商品、账号、会员、订单、营销、财务拆分，只负责HTTP入参、DTO校验、身份检查和响应，不直接依赖Mapper或外部集成实现。
- `dto`定义实际校验的请求结构；查询投影保留原API的snake_case字段，避免重构时暗中改变客户端契约。
- `service`定义业务接口；`service.impl`实现事务、状态机、归属校验、价格/预算/退款计算。订单创建、支付确认、资产、履约、退款申请/入账、佣金、提现、月结、对账、Outbox分别有独立服务。
- `entity`为真实表实体，声明MyBatis-Plus表名、字段和主键；普通分类CRUD、商品实体读取、地址归属删除等调用继承的BaseMapper方法。
- `mapper`集中数据库查询与写入。库存锁、账户锁、支付幂等、预算与对账等SQL明确写在领域Mapper/Provider；服务层没有SQL字符串或通用raw-SQL网关。
- `cart_item`有`(member_id, sku_id)`复合主键，使用明确同时校验两列的Mapper操作，避免伪装成MyBatis-Plus不支持的单列`xxById`操作。
- `integration`隔离微信签名、请求、回调解密及物流协议；由业务服务调用。`job`只触发对应Service，不承担资金规则。
- `common`集中JSON、异常、Row投影、ID号段等基础能力。ID号段通过独立MyBatis会话提交，避免占用业务连接池或因事务回滚复用号段。
- `config`集中Mapper扫描、事务、HTTP、安全配置、启动校验、local演示种子和私有初始凭据策略。Flyway是数据库版本化入口。

## 网页依赖方向

```text
router/guard → layouts + views → components / composables / store
                                          ↓
                                         api
                                          ↓
                                   utils/request（Axios）
```

领域`api`模块只以`request.post/get/put/delete()`调用HTTP。Axios封装处理基址、超时、会员/管理员独立会话、Idempotency-Key、响应解包、401与错误；传输层依赖纯会话工具，`bootstrap/http`注入Pinia/UI回调，避免`request → store → api → request`循环依赖。

页面只组织本页交互，布局、登录框、地址/退款表单、商品卡、资产/列表等组件各自独立。Pinia保存会话、配置、购物袋和界面状态；格式化、CSV导出和文件凭据发现等工具各自独立。样式按公共、商城、后台和响应式域拆分。

下单成功后的订单ID是已确认结果：立即保证导航到订单，购物袋刷新独立处理；补刷失败不能把用户留在已消费报价上重新提交。

## 小程序

小程序用`pages.json`和`utils/router`管理真实原生页面、登录返回与路由鉴权，不嵌入浏览器Vue Router。领域API沿用`request.post()`接口，Axios通过自定义`uni.request`适配器运行，不依赖XHR。会话、配置、购物袋分别由Pinia管理。

公开目录不能依赖可选会话恢复成功；过期Token的401会清理会话，但不阻止浏览商品。下单/立即购买成功后，购物袋刷新失败也不能阻止成功页面跳转；过期请求和退出登录后的旧响应不能恢复旧购物袋。

## 事务、金额与状态

MySQL是真实来源，local使用H2。货币始终为人民币整数分，积分为整数点；前端数字只用于展示及表达选择，不能覆盖权威价格、库存或应付金额。

创建订单在事务内复核报价、预占库存和积分、写入订单行快照并消费报价。幂等范围为会员+操作+请求键，同键同内容返回原响应，不同内容409。支付/退款/出款各有独立状态，渠道未知不当成成功或失败；重复通知用业务锁和唯一事件去重。

外部HTTP不属于数据库事务。先持久化支付意图或Outbox，再请求微信；未知提交按原业务编号查单，不换号盲重试。Outbox使用租约与指数退避。回调验签、密文认证、商户、AppID、币种、金额与付款人交叉校验保持不变。

余额只通过带来源的追加账本变更，可重建核对；后台没有任意改余额入口。佣金仍按订单规则快照、真实净现金、毛利及预算计算，退款按原分摊逆向；已出款不足冲回记追偿，不写负可用余额。经办人与财务/执行人分离的约束由服务端强制执行。

## local 与 prod

默认local直接启动，使用H2和明确标记的演示数据；未提供管理员口令且没有已有管理员时，生成私有随机凭据文件，日志只给绝对路径。已有账号不重置。

prod或显式APP_DEV_MODE=false不生成演示身份和随机管理员口令，必须通过真实数据库、支付配置及密钥文件校验。prod不能通过误设APP_DEV_MODE=true开启模拟能力。营销与真实转账仍是独立环境开关。

# 素见好物微信小程序

本目录为uni-app微信小程序，不以一个首页中的条件分支模拟多页。13个页面在`src/pages.json`中注册，可独立进入、返回、刷新和分享。

## 启动与构建

```bash
npm ci
cp .env.example .env.local
npm run typecheck
npm run test
npm run build:mp-weixin
```

微信开发者工具导入`dist/build/mp-weixin`。开发监听使用`npm run dev:mp-weixin`。

`.env.local`需填写真实`WX_APP_ID`、`VITE_API_BASE_URL`和`VITE_ASSET_BASE_URL`。API地址包含`/api/v1`；素材地址提供`/art/*.png`。生产必须使用已配置为微信合法域名的HTTPS地址，并完成实际微信隐私设置和商户关联。示例AppID仅用于编译格式检查，不能代表已有微信账号。

开发环境的账号页在后端`devMode=true`时显示明确标记的测试登录；生产仅提供原生微信授权登录。测试身份、测试付款不会被描述为真实微信交易。支付结果始终以服务端查单/回调为准。

## 分层

- `pages`：商品、积分、购物袋、结算、订单及详情、账户、地址、推广、提现、规则等真实页面。
- `components`：页面框架、导航、商品卡、数量选择、订单行、退款弹窗及目录展示等复用组件。
- `store`：Pinia管理应用配置、当前会话和购物袋；401统一撤销会话并通知状态仓库。
- `api`：按商品、会员、地址、交易拆分，调用`request.post()`、`request.get()`等封装方法，页面不直接调用HTTP传输。
- `utils/request.ts`：Axios实例、请求/响应拦截器及`uni.request`原生适配器，注入Bearer与幂等键、处理错误和超时，不依赖浏览器XHR。
- `utils/router.ts`：统一命名路由、鉴权跳转及登录后返回；采用uni-app原生页面栈，不将Web Vue Router强行用于小程序。
- `utils/wechat.ts`：微信隐私授权、登录code、地址导入、原生支付、商家转账确认。
- `composables/useCatalog.ts`：公开商品目录的局部加载逻辑；可选会话刷新失败不阻断商品展示。
- `types`：前后端请求与响应类型。
- `styles`：按基础、商品、交易、会员拆分的全局风格。

页面和业务组件各自管理局部表单，不把全部页面状态搬到另一个巨型composable。接口金额仍是人民币整数分；小程序不会把前端价格作为服务端权威值。

## 验证范围

`npm run test`对Axios原生适配器的请求序列化、鉴权/幂等头、GET参数、401、409、网络失败，以及原生页面鉴权跳转进行自动化检查。还覆盖过期会话不阻断公开商品、成功创建订单后补刷购物袋失败仍能进入订单页、已接受的购物袋写入不会被补刷失败覆盖，以及迟到响应与退出登录时的状态隔离。`typecheck`和微信生产构建检查全部页面及组件。

真实微信登录、支付、确认收款与物流查询需要实际商户/服务商凭据和微信开发者工具或真机联调；没有将编译通过说成已通过真实资金验收。

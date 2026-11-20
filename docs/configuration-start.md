# 直接填写生产配置

使用仓库根目录的[`.env.example`](../.env.example)。92个参数均已逐项写明中文名称、用途、填写条件和默认值。英文参数名保持原样，只修改等号右侧。

## 1. 保存为项目根目录的 `.env`

没有配置文件时，将`.env.example`复制为`.env`。使用UTF-8编码。若已有`.env`，先备份，再补齐对应参数，不要用空模板覆盖已有真实配置。

只配置根目录这一份文件；使用`manage.py`构建部署时，网页、小程序、后端和Docker会读取或派生所需参数，无须另改Java、Vue或Compose文件。

标记含义：**必填/必需文件**必须提供；**条件必填**按使用方式填写；**自动**留空；**默认/固定**通常保持不变。

## 2. 默认方案先填这些值

模板默认使用内置MySQL、自动HTTPS证书、网页扫码支付。需填写20个空值，并准备两份微信文件：

| 用途 | 参数名 | 填写来源 |
|---|---|---|
| 站点域名 | `PUBLIC_STORE_URL` | 自己的HTTPS域名，例如`https://mall.example.com`，不带子目录 |
| 客服联系方式 | `SUPPORT_CONTACT` | 实际客服电话、邮箱或客服微信 |
| 隐私联系方式 | `PRIVACY_CONTACT` | 接受个人信息申请的实际邮箱或电话 |
| 自动证书通知邮箱 | `ACME_EMAIL` | 实际邮箱；域名DNS先指向部署服务器并开放80/443 |
| 数据库应用密码 | `MYSQL_PASSWORD` | 自设独立随机密码 |
| 数据库root密码 | `MYSQL_ROOT_PASSWORD` | 自设另一份独立随机密码 |
| 管理员密码 | `ADMIN_BOOTSTRAP_PASSWORD` | 至少12位，建议24至48位随机口令；配置内保留该值，已有账号不会被重置 |
| 小程序AppID | `WX_APP_ID` | 小程序后台的AppID，wx开头共18位 |
| 小程序AppSecret | `WX_APP_SECRET` | 与该AppID对应的小程序密钥 |
| 微信商户号 | `WX_MCH_ID` | 微信支付商户平台的商户号，须与AppID关联 |
| 商户API证书序列号 | `WX_MERCHANT_SERIAL` | 对应商户API证书，不是平台公钥ID |
| 平台公钥ID或平台证书序列号 | `WX_PLATFORM_SERIAL` | 与平台公钥/证书文件配对；公钥ID通常以PUB_KEY_ID_开头 |
| 微信APIv3密钥 | `WX_API_V3_KEY` | 微信商户平台设置的32字节密钥，不是AppSecret |
| 短信AccessKey ID | `SMS_ACCESS_KEY_ID` | 有短信发送权限的阿里云/RAM密钥ID |
| 短信AccessKey Secret | `SMS_ACCESS_KEY_SECRET` | 与上述短信ID配对的Secret |
| 短信签名 | `SMS_SIGN_NAME` | 审核通过的签名原文 |
| 验证码模板编码 | `SMS_TEMPLATE_CODE` | 审核通过的SMS_开头模板编码，验证码变量必须为`code` |
| 实名AccessKey ID | `IDENTITY_ACCESS_KEY_ID` | 已开通且有权调用Id2MetaVerify的阿里云/RAM密钥ID |
| 实名AccessKey Secret | `IDENTITY_ACCESS_KEY_SECRET` | 与上述实名ID配对的Secret |
| 本系统账户加密密钥 | `ACCOUNT_DATA_KEY` | 独立随机32字节的Base64编码，生成方法见下方 |

短信与实名可使用同一对实际AccessKey，但该密钥必须同时具有两项服务权限，两组配置仍分别填写。生产启动始终要求短信、实名及账户加密配置，即使暂未启用提现。

首次生成`ACCOUNT_DATA_KEY`，在自己的电脑执行以下命令，将输出填入该项：

```bash
python3 -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

Windows将`python3`替换成`py -3`。该密钥只生成一次，重启、扩容和恢复均须保持相同，另行安全备份。它与`WX_API_V3_KEY`是两个不同的密钥。

## 3. 放好微信文件

模板默认读取：

```text
simply-seen/
  .env
  secrets/
    wechat/
      merchant-private.pem
      platform-public.pem
```

- `merchant-private.pem`：商户API证书申请时生成的RSA私钥，原文件通常名为`apiclient_key.pem`，可以复制后改名。不要放成商户证书`apiclient_cert.pem`。
- `platform-public.pem`：微信支付平台公钥或平台证书，必须与`WX_PLATFORM_SERIAL`对应。不能用商户证书替代。

也可以不改文件名，直接把`.env`中的`WX_PRIVATE_KEY_PATH`和`WX_PLATFORM_PUBLIC_KEY_PATH`改成实际文件路径。路径相对于`.env`所在目录。

## 4. 按需调整模式和业务开关

| 需求 | 要调整的配置 |
|---|---|
| 使用已有MySQL | `DATABASE_MODE=external`，填写`DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`；不必填写内置MySQL两个密码 |
| 使用已有站点证书 | `HTTPS_MODE=certificate`，填写`TLS_CERT_PATH/TLS_KEY_PATH`；不必填写ACME邮箱 |
| 使用已有HTTPS代理 | `HTTPS_MODE=external`，让现有代理转发至`WEB_BIND`，填写实际`TRUSTED_PROXY_CIDR` |
| 移动网页用微信H5支付 | `WX_WEB_PAYMENT_MODE=h5`，商户开通H5产品并配置实际H5支付域名；不同AppID填`WX_H5_APP_ID` |
| 开启二级分销 | `DISTRIBUTION_ENABLED=true`并重新部署，随后后台创建启用分销的规则，由另一管理员审批；已有规则不会随环境开关自动改写 |
| 开启月度奖励 | `MONTHLY_ENABLED=true`，后台设置规则预算，生成草稿并按权限异人复核 |
| 开启提现/真实转账 | `TRANSFER_ENABLED=true`，填写实际`WX_TRANSFER_SCENE_ID`、`WX_TRANSFER_PERCEPTION`、`WX_TRANSFER_REPORT_INFOS`并开通对应商户场景；仍有实名、微信绑定及独立审批门禁 |
| 查询快递轨迹 | 填写`LOGISTICS_QUERY_URL`、`LOGISTICS_CUSTOMER`、`LOGISTICS_KEY`、`LOGISTICS_CARRIER_CODES`；不填也可发货、录入运单 |

分销、月度奖励、提现/转账三个开关初始均为false，需要使用时明确开启。接口地址、端口、地区、回调地址等按模板标记保留默认或留空自动派生即可。

## 5. 检查和启动

安装完整JDK21、Node22.12+、Python3.11+、Docker与Compose2.24+后，在项目根目录执行：

```bash
python3 scripts/manage.py check
python3 scripts/manage.py up
python3 scripts/manage.py status
```

Windows使用`py -3`。`check`会指出缺少或格式错误的参数名，不输出密钥；它不会验证服务商实际权限，也不会发送短信或发起付款。`up`会测试、构建并启动服务。

管理员入口为`PUBLIC_STORE_URL`加`/admin`，账号默认admin，口令为配置的管理员密码。正式库初始不含演示商品，在后台录入实际商品与库存。小程序产物在`mini/dist/build/mp-weixin`，微信后台仍需配置实际合法域名并按平台流程审核发布。

真实配置与密钥不提交Git。若采用文件传值，取消模板末尾对应`XXX_FILE`的注释，保持同名`XXX`直接值为空；文本文件内只写值，不写`XXX=`。其他细节见[完整配置说明](configuration.md)。

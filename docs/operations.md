# 部署、运行与恢复

## 从配置到启动

构建机准备Python 3.11+、完整JDK 21、Node.js 22.12+及Docker Compose 2.24+。Maven由仓库工具下载并验证摘要，也可使用已有安装。部署机须能访问依赖仓库、镜像仓库及配置中的正式渠道。配置项见[配置清单](configuration.md)；配置文件与密钥不进入Git或镜像。

首次部署时将`.env.example`复制为仓库根`.env`，填写清单中的必填值，将微信RSA文件放到配置指定位置；已有`.env`时直接补齐该文件。不要再维护web、mini或server的第二份环境文件。以下命令在仓库根运行；Windows将`python3`替换为`py -3`。

```bash
python3 scripts/manage.py check
python3 scripts/manage.py up
python3 scripts/manage.py status
```

`up`自动读取配置，检查变量及RSA格式，隔离真实密钥执行Java/网页/小程序测试和构建，生成私有运行配置，构建容器并等待服务就绪。构建子进程强制`APP_CONFIG_FILE=`，也不继承真实数据库及渠道密钥；生产启动才注入配置。首次部署不使用`--skip-tests`。若在可信CI已完成构建，可将JAR、web/dist及仓库部署文件放到部署机，执行`up --skip-build`。此选项仍检查配置并等待健康状态。

配置文件放在其他目录时，每次命令提供`--config /absolute/path/app.env`。相对密钥路径、备份目录按该配置文件目录解析，进程环境覆盖文件。脚本不使用shell加载配置，含`&`的JDBC、带空格的JSON及中文值可直接使用；完整规则见配置清单。`check`仅检查本地配置，不发送短信、不付款，也不能验证渠道是否已向商户开通产品。

`HTTPS_MODE=caddy`时，将站点域名指向服务器、放通80/443并填写ACME_EMAIL，内置Caddy会申请并续签证书；`certificate`模式读取TLS_CERT_PATH/TLS_KEY_PATH；`external`模式由既有HTTPS代理转发至WEB_BIND。内置模式的公开站点地址使用443。已有代理时填写其实际源IP或CIDR到TRUSTED_PROXY_CIDR；禁止信任全网。Nginx只解析可信代理传来的客户端IP，并覆盖发给Java的转发头，避免伪造IP绕过短信限流。应用端口和数据库端口不向公网发布。

`DATABASE_MODE=embedded`自动初始化MySQL 8.4库并派生连接；`external`必须填写DATABASE_URL/USER/PASSWORD。自建CA填写DATABASE_CA_PATH并使用VERIFY_IDENTITY或VERIFY_CA，应用仅为数据库连接创建独立信任库，不改变微信、阿里云等HTTPS连接的信任配置。数据库密码更改不等于对已有MySQL卷完成密码轮换，应先在数据库侧轮换账号，再同步配置。

私钥及可选CA复制到`.tools/deploy`中的私有目录，再以只读挂载提供容器；目录不属于构建上下文。该目录仍需作为敏感运行文件保护，不可上传。`status`查看状态，`down`停止服务且保留数据库和证书卷。不要随意改变COMPOSE_PROJECT_NAME，它决定持久卷及网络名称。Flyway自动验证并执行迁移；迁移失败应排查实际原因，不修改历史校验和绕过。

如需直接运行JAR或用IDE启动，Java会在启动配置与数据源前自动读取根`.env`；IDE工作目录可为仓库根或server。完整JDK 21为编译要求，IDE项目SDK和编译器均应设置为21。命令行启动为：

```bash
python3 scripts/manage.py run
```

该命令只启动Java，前端与HTTPS仍需独立进程或容器。无配置文件时的local演示环境不用于生产。不要使用生产数据库运行测试。

## 上线验收

首个管理员首次登录后更新随机口令，并建立独立财务、运营和第二管理员账号。短信签名/模板、实名认证产品、微信AppID关联、支付及转账产品权限应在对应平台完成开通；在微信后台配置域名与回调，核对隐私声明及客服信息。`mini/dist/build/mp-weixin`为构建产物，仍须导入微信开发者工具并按平台流程提交审核和发布。

使用真实账号和小额金额验证手机号登录、身份匹配、Native或H5支付、查询、关单、退款、提现确认及回调重试。导入真实商品与库存，核对成本和佣金上限，创建规则草稿并由另一管理员审批；分销、月度活动与转账分别按实际业务启用。项目自动化测试使用受控服务协议与测试库；正式商户权限、真机弱网、生产容量和真实恢复能力需要部署后的证据。

## 健康、任务与对账

容器使用`/actuator/health/readiness`检查服务及数据库就绪；`/actuator/health`可用于内部监控。UP不代表商户产品权限、预算或所有业务状态正常。持久Outbox定期领取任务，租约到期可恢复，失败按退避重试，业务唯一键防止重复记账；相关周期与租约可按配置调整。

管理后台对账页查看未完成任务、渠道错误码和差异工单。超时未知时保留资金冻结并查询原单，不改商户单号重新出款。修复配置后由任务继续；渠道拒绝需依据真实商户权限和渠道记录处理。

建议外部监控覆盖：就绪状态、5xx、连接池、最老未完成Outbox、UNKNOWN/CONFIRMING持续时间、待审退款/提现、账本差异、预算占用、磁盘及备份新鲜度。告警接收人及通知路由在部署方监控平台配置。后台对账会核对账本、余额及渠道状态；大数据量环境按实际耗时设置代理超时，避免重复点击。银行到账、手续费和税务申报仍需与真实账单核对。

提现分别记录gross/tax/net，按net实际出款并按gross核销冻结。tax表示财务核定预留税款，不是税务机关收款证明；实际扣缴、申报和凭证由经营主体完成并归档。

## 备份与恢复

将BACKUP_DIR设置为受控备份目录，按业务RPO定时执行以下命令，并将产物加密转存异地。脚本生成单事务mysqldump、gzip及SHA-256校验文件，备份文件不会覆盖同秒产生的另一份备份。数据库密码通过环境传递，不出现在客户端参数中。

```bash
python3 scripts/manage.py backup
python3 scripts/manage.py backup --output /secure/backups
```

内置数据库通过运行中的MySQL容器执行。外部数据库通过MySQL 8.4临时客户端容器连接，默认使用项目网络；若需既有Docker网络可填写DATABASE_CLIENT_NETWORK。普通外部JDBC地址、端口及sslMode会自动转为客户端参数，DATABASE_CA_PATH同样只读挂载；特殊多主机或代理JDBC形式请使用数据库平台自身备份能力。备份账号须具备mysqldump所需权限。

ACCOUNT_DATA_KEY必须长期稳定并与数据库备份分开安全保存；恢复时需要原密钥解密敏感资料。商户私钥、配置文件和HTTPS证书备份也应独立保护；不要把密钥上传至源码仓库。

恢复会覆盖目标数据库。先暂停用户写入、支付回调及任务处理，保存目标库的当前备份，核对配置指向与备份时间；将`.sql.gz`及同名`.sha256`放在一起，再显式确认：

```bash
python3 scripts/manage.py restore --file /secure/backups/sujian-....sql.gz --confirm
```

该命令先验证摘要及压缩完整性，再恢复到当前配置的数据库；恢复不会撤销微信已经发生的支付、退款或转账。恢复后验证Flyway迁移、账本重建、未完成任务、渠道原单状态与余额，再逐步开放流量。脚本不自动判断停写条件，`--confirm`表示操作者已完成这些步骤。RPO/RTO需通过实际演练测量。

旧入口`./scripts/backup.sh`、`./scripts/restore.sh`调用同一Python配置读取器；restore也支持RESTORE_FILE及RESTORE_CONFIRMED=YES。回滚优先切回已验证应用产物，数据库迁移只前滚；真实资金事实不得因回滚删除，未知渠道结果仍查原单。

物流提供发货、运单展示和配置后的快递100查询。电子面单、发票平台和经营主体税务申报需使用相应外部服务；客服采用配置的联系方式和订单售后记录。上线前的验收清单见[生产验收](production-acceptance.md)。

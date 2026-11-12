#!/usr/bin/env python3
"""Create a private local development configuration without replacing user files."""
import secrets
from pathlib import Path
root=Path(__file__).resolve().parents[1]
path=root/'.env'
if path.exists():
    raise SystemExit('.env 已存在；保留现有配置。')
path.write_text('APP_DEV_MODE=true\nADMIN_BOOTSTRAP_USER=admin\nADMIN_BOOTSTRAP_PASSWORD='+secrets.token_urlsafe(24)+'\nPORT=8080\nDATABASE_URL="jdbc:h2:file:./data/mall;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE"\nDATABASE_USER=sa\nDATABASE_PASSWORD=\nDISTRIBUTION_ENABLED=true\nMONTHLY_ENABLED=true\nTRANSFER_ENABLED=false\nSUPPORT_CONTACT="素见好物运营团队"\nPRIVACY_CONTACT="通过运营团队提交个人信息请求"\nPUBLIC_STORE_URL="http://localhost:5173"\nAPI_DOCS_ENABLED=true\n')
path.chmod(0o600)
print('已生成私有 .env；管理员密码仅保存在该文件中，不会写入源码。')

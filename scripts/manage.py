#!/usr/bin/env python3
"""One cross-platform configuration reader and operational entry point."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import sys
from configuration import database, deployment
from configuration.environment import ROOT, load
from configuration.processes import build, java_command, run
from configuration.validation import validate


def parser():
    result = argparse.ArgumentParser(description="素见好物：读取配置、检查、构建和部署，无需修改源码")
    result.add_argument("action", choices=["check", "build", "up", "down", "status", "run", "render", "backup", "restore"])
    result.add_argument("--config", help="配置文件；默认仓库根.env，进程环境覆盖文件")
    result.add_argument("--skip-tests", action="store_true", help="构建时显式跳过自动化测试")
    result.add_argument("--skip-mini", action="store_true", help="本次不打包微信小程序")
    result.add_argument("--skip-build", action="store_true", help="使用已构建的JAR和web产物")
    result.add_argument("--cloud-ecj", action="store_true", help="仅JRE21云环境使用ECJ构建；本地完整JDK不需要")
    result.add_argument("--jar", default=str(ROOT / "server/target/mall-1.0.0.jar"))
    result.add_argument("--output", help="备份输出目录")
    result.add_argument("--file", help="需恢复的.sql.gz备份")
    result.add_argument("--confirm", action="store_true", help="已停写并备份当前库，确认恢复")
    result.add_argument("--check-compose", action="store_true", help="render后调用Docker Compose静默校验")
    return result


def main():
    arguments = parser().parse_args()
    secret_names = None
    if arguments.action in {"down", "status"}:
        secret_names = set()
    elif arguments.action in {"backup", "restore"}:
        secret_names = {"MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "DATABASE_PASSWORD"}
    values, base, selected = load(arguments.config, secret_names=secret_names)
    action = arguments.action
    if action in {"check", "build", "up", "render"}:
        validate(values, compose=action in {"check", "up", "render"}, mini=not arguments.skip_mini)
        print("生产配置检查通过；未输出密钥，也未向真实短信/支付渠道发起请求。")
    if action == "check":
        print("配置来源：" + (str(selected) if selected else "进程环境"))
        return
    if action in {"build", "up"} and not arguments.skip_build:
        build(values, tests=not arguments.skip_tests, mini=not arguments.skip_mini, cloud_ecj=arguments.cloud_ecj)
        print("JAR、网页" + ("及小程序" if not arguments.skip_mini else "") + "构建完成。")
    if action == "build":
        return
    if action in {"up", "render"}:
        if action == "up":
            for file in [ROOT / "server/target/mall-1.0.0.jar", ROOT / "web/dist/index.html"]:
                if not file.is_file():
                    raise ValueError("缺少构建产物，请先运行manage.py build或移除--skip-build")
        runtime = deployment.prepare(values)
        command = deployment.command(values, runtime)
        if arguments.check_compose or action == "up":
            run(command + ["config", "--quiet"], values, capture=True)
        if action == "render":
            print("已生成私有运行配置：" + str(runtime))
            return
        run(command + ["up", "-d", "--build", "--wait", "--wait-timeout", "240"], values)
        deployment.clean_old()
        print("服务已就绪：" + values["PUBLIC_STORE_URL"])
        print("小程序产物：mini/dist/build/mp-weixin（导入微信开发者工具并按平台流程发布）")
    elif action == "down":
        run(deployment.command(values) + ["down"], values)
        deployment.clean_old(all_files=True)
        print("服务已停止；数据库及HTTPS证书卷保留。")
    elif action == "status":
        run(deployment.command(values) + ["ps"], values)
    elif action == "run":
        production = values.get("SPRING_PROFILES_ACTIVE") == "prod" or values.get("APP_DEV_MODE", "true").lower() == "false"
        if production:
            validate(values)
        jar = Path(arguments.jar).expanduser().resolve()
        if not jar.is_file():
            raise ValueError("尚未构建JAR，请先执行mvnw clean verify或manage.py build")
        run(java_command(values, jar), values, cwd=base)
    elif action == "backup":
        print("备份及校验完成：" + str(database.backup(values, arguments.output)))
    elif action == "restore":
        file = arguments.file or values.get("RESTORE_FILE")
        if not file:
            raise ValueError("请通过--file或RESTORE_FILE指定恢复备份")
        database.restore(values, file, arguments.confirm or values.get("RESTORE_CONFIRMED") == "YES")
        print("恢复完成；开放流量前请验证迁移、账本及渠道状态。")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)

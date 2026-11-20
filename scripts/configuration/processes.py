from pathlib import Path
import json
import os
import shlex
import subprocess
from .environment import ROOT
from .dotenv import read
from .tooling import executable, java_executable, check_build_versions, maven, npm


def run(arguments: list[str], values: dict[str, str], *, cwd: Path = ROOT, capture: bool = False):
    result = subprocess.run(arguments, cwd=cwd, env=values, check=False, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None)
    if result.returncode:
        if capture:
            raise RuntimeError("命令执行失败（为防止配置值进入日志，不输出捕获的配置内容）")
        raise RuntimeError("命令执行失败，退出码" + str(result.returncode))
    return result


def build(values: dict[str, str], *, tests: bool, mini: bool, cloud_ecj: bool):
    safe = build_environment(values)
    check_build_versions(safe, cloud_ecj=cloud_ecj)
    node_npm = npm()
    arguments = [*maven(values), "-B"] + (["-Pcloud-ecj"] if cloud_ecj else []) + ["clean", "verify" if tests else "package"]
    if not tests:
        arguments.append("-DskipTests")
    run(arguments, safe)
    # Build input comes from the selected root config, never stale frontend .env.local files.
    web_values = {**safe, "VITE_API_BASE_URL": "/api/v1"}
    run([*node_npm, "ci", "--prefix", "web"], web_values)
    if tests:
        run([*node_npm, "run", "test", "--prefix", "web"], web_values)
    run([*node_npm, "run", "build", "--prefix", "web"], web_values)
    if mini:
        mini_values = {**safe, "VITE_API_BASE_URL": values["PUBLIC_API_URL"], "VITE_ASSET_BASE_URL": values["PUBLIC_ASSET_URL"], "WX_APP_ID": values["WX_APP_ID"]}
        run([*node_npm, "ci", "--prefix", "mini"], mini_values)
        if tests:
            run([*node_npm, "run", "test", "--prefix", "mini"], mini_values)
        run([*node_npm, "run", "typecheck", "--prefix", "mini"], mini_values)
        run([*node_npm, "run", "build:mp-weixin", "--prefix", "mini"], mini_values)


def java_command(values: dict[str, str], jar: Path) -> list[str]:
    options = values.get("JAVA_OPTS_JSON")
    if options:
        decoded = json.loads(options)
        if not isinstance(decoded, list) or any(not isinstance(item, str) for item in decoded):
            raise ValueError("JAVA_OPTS_JSON必须为字符串JSON数组")
    else:
        decoded = shlex.split(values.get("JAVA_OPTS", ""))
    return [java_executable(values), *decoded, "-jar", str(jar)]


def build_environment(values: dict[str, str]) -> dict[str, str]:
    declared = set(read(ROOT / ".env.example"))
    declared.update({name + "_FILE" for name in declared})
    prefixes = ("WX_", "SMS_", "IDENTITY_", "DATABASE_", "MYSQL_", "APP_", "ACCOUNT_", "LOGISTICS_", "PUBLIC_", "ADMIN_BOOTSTRAP_", "TEST_DATABASE_")
    safe = {key: value for key, value in values.items()
            if key not in declared and not key.startswith(prefixes) and key != "TEST_DATABASE_URL"}
    safe["APP_CONFIG_FILE"] = ""
    return safe

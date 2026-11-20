#!/usr/bin/env python3
"""Verify packaged-JAR dotenv and secret-file loading without changing application source."""
from __future__ import annotations

import argparse
import base64
import importlib.util
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def smoke_helpers():
    spec = importlib.util.spec_from_file_location("sujian_local_smoke", ROOT / "scripts/smoke-local.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("Local smoke helpers are unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def clean_environment() -> dict[str, str]:
    """Keep proxy/CA/managed guards; remove only application settings and their file aliases."""
    environment = os.environ.copy()
    application_prefix = re.compile(
        r"^(?:APP|ACCOUNT|WX|SMS|IDENTITY|DATABASE|MYSQL|ADMIN|PUBLIC|SESSION|CORS|ORDER|JOB|"
        r"MAINTENANCE|API|WITHDRAW|TRANSFER|MONTHLY|DISTRIBUTION|LOGISTICS|SPRING|VITE|WEB|TLS|"
        r"HTTPS_MODE|ACME|STORE|RESTORE|BACKUP)_"
    )
    names = set()
    for line in (ROOT / ".env.example").read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=", line)
        if match:
            names.add(match.group(1))
    for name in list(environment):
        if name in names or name.removesuffix("_FILE") in names or application_prefix.match(name):
            environment.pop(name, None)
    # Absence enables the actual discovery path under test; an empty value would disable it.
    environment.pop("APP_CONFIG_FILE", None)
    return environment


def assert_no_execution(project: Path) -> None:
    for directory in (project, project / "server"):
        for marker in ("SHELL_EXPANSION_MARKER", "BACKTICK_MARKER", "JSON_MARKER"):
            assert not (directory / marker).exists(), "Configuration text was executed as a command"


def verify_process_files(helpers, java: str, jar: Path, base: Path, environment: dict[str, str],
                         password: str, account_key: str, privacy: str) -> Path:
    """Exercise only process *_FILE aliases, including the actual listening port."""
    working = base / "pure process 文件"
    (working / "secrets").mkdir(parents=True)
    port = helpers.free_port()
    support = "纯环境文件客服 $(touch PURE_ENV_MARKER) & 中文"
    values = {"admin password.txt": password, "account-data.key": account_key,
              "support.txt": support, "privacy.txt": privacy, "port.txt": str(port)}
    for name, value in values.items():
        (working / "secrets" / name).write_bytes((value + "\r\n").encode("utf-8"))
    child_environment = {**environment,
        "APP_CONFIG_FILE": "", "SPRING_PROFILES_ACTIVE": "local", "APP_DEV_MODE": "true",
        "DATABASE_MODE": "embedded", "DATABASE_USER": "sa", "DATABASE_PASSWORD": "",
        "ADMIN_BOOTSTRAP_USER": "process_only_admin",
        "ADMIN_BOOTSTRAP_PASSWORD_FILE": "secrets/admin password.txt",
        "ACCOUNT_DATA_KEY_FILE": "secrets/account-data.key",
        "SUPPORT_CONTACT_FILE": "secrets/support.txt", "PRIVACY_CONTACT_FILE": "secrets/privacy.txt",
        "PORT_FILE": "secrets/port.txt", "SMS_PROVIDER": "development",
        "IDENTITY_PROVIDER": "development", "JOB_INITIAL_DELAY_MS": "3600000",
        "API_DOCS_ENABLED": "false"}
    for direct in ("PORT", "ADMIN_BOOTSTRAP_PASSWORD", "ACCOUNT_DATA_KEY", "SUPPORT_CONTACT", "PRIVACY_CONTACT"):
        assert direct not in child_environment, "Direct process value would hide a file-alias failure"
    assert not (working / ".env").exists()
    log_dir = ROOT / "server/target/local-smoke-logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / ("configured-start-4-" + secrets.token_hex(4) + ".log")
    stream = log.open("x")
    # Unlike helpers.start(), do not add PORT: this startup must obtain it from PORT_FILE.
    process = subprocess.Popen([java, "-jar", str(jar)], cwd=working, env=child_environment,
                               stdout=stream, stderr=subprocess.STDOUT)
    try:
        address = helpers.ready(process, port, log)
        settings = helpers.call(address, "/config")
        assert settings["support"] == support and settings["privacy"] == privacy
        admin = helpers.call(address, "/auth/admin", "POST", {
            "username": "process_only_admin", "password": password})
        assert admin["profile"]["username"] == "process_only_admin"
        challenge = helpers.call(address, "/auth/phone/code", "POST", {"phone": "13900000003"})
        session = helpers.call(address, "/auth/phone", "POST", {
            "phone": "13900000003", "code": challenge["devCode"],
            "challengeId": challenge["challengeId"], "accepted": True})
        assert session["profile"]["phoneBound"]
        assert (working / "data/mall.mv.db").is_file()
        assert not (working / "data/account-data.key").exists(), "Process ACCOUNT_DATA_KEY_FILE was ignored"
        assert not (working / "PURE_ENV_MARKER").exists(), "Secret-file text was executed"
        assert not (working / ".env").exists()
    finally:
        helpers.stop(process, stream)
    return log


def verify(java: str, jar: Path, result_file: Path | None) -> None:
    helpers = smoke_helpers()
    environment = clean_environment()
    support = "配置客服 $(touch SHELL_EXPANSION_MARKER) `touch BACKTICK_MARKER` # 中文 & 值"
    report_json = '[{"info_type":"岗位类型","info_content":"测试内容 & $(touch JSON_MARKER)"}]'
    secret_password = secrets.token_urlsafe(24) + "$ &:"
    account_key = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
    log_files: list[Path] = []

    with tempfile.TemporaryDirectory(prefix="sujian-configured-") as temporary:
        project = Path(temporary) / "配置 project"
        project.mkdir()
        (project / "server").mkdir()
        (project / "secrets").mkdir()
        # This marker matches the normal repository layout used by server-directory discovery.
        (project / "pom.xml").write_text("<project><modelVersion>4.0.0</modelVersion></project>\n")
        (project / "secrets/admin password.txt").write_bytes((secret_password + "\r\n").encode())
        (project / "secrets/account-data.key").write_bytes((account_key + "\r\n").encode())
        lines = [
            "SPRING_PROFILES_ACTIVE=local",
            "APP_DEV_MODE=true",
            "DATABASE_MODE=embedded",
            "DATABASE_USER=sa",
            "DATABASE_PASSWORD=",
            "ADMIN_BOOTSTRAP_USER=configured_admin",
            'ADMIN_BOOTSTRAP_PASSWORD_FILE="./secrets/admin password.txt"',
            "ACCOUNT_DATA_KEY_FILE='./secrets/account-data.key'",
            "SMS_PROVIDER=development",
            "IDENTITY_PROVIDER=development",
            "JOB_INITIAL_DELAY_MS=3600000",
            "API_DOCS_ENABLED=false",
            f'SUPPORT_CONTACT="{support}"',
            f"PRIVACY_CONTACT='{report_json}'",
            f"WX_TRANSFER_REPORT_INFOS='{report_json}'",
        ]
        config = project / ".env"
        original_config = b"\xef\xbb\xbf" + ("\r\n".join(lines) + "\r\n").encode("utf-8")
        config.write_bytes(original_config)
        credentials = {"username": "configured_admin", "password": secret_password}
        token = None
        profile_id = None
        pending = None

        for index, working in enumerate((project, project / "server", project / "server")):
            child_environment = environment.copy()
            if index == 2:
                child_environment["SUPPORT_CONTACT"] = "进程环境优先于文件配置"
            port = helpers.free_port()
            process, stream, log = helpers.start(
                java, jar, working, child_environment, port, f"configured-start-{index + 1}"
            )
            log_files.append(log)
            try:
                base = helpers.ready(process, port, log)
                settings = helpers.call(base, "/config")
                expected_support = support if index < 2 else child_environment["SUPPORT_CONTACT"]
                assert settings["support"] == expected_support, "Dotenv literal text or environment precedence changed"
                assert settings["privacy"] == report_json, "Quoted JSON or Chinese configuration text changed"
                admin = helpers.call(base, "/auth/admin", "POST", credentials)
                assert admin["profile"]["username"] == credentials["username"]
                assert (project / "data/mall.mv.db").is_file(), "Configured datasource was not rooted beside .env"
                assert not (project / "server/data").exists(), "Server-directory restart created a second data location"
                if index == 0:
                    challenge = helpers.call(base, "/auth/phone/code", "POST", {"phone": "13900000001"})
                    assert challenge["development"] and "devCode" in challenge
                    session = helpers.call(base, "/auth/phone", "POST", {
                        "phone": "13900000001", "code": challenge["devCode"],
                        "challengeId": challenge["challengeId"], "accepted": True,
                    })
                    token, profile_id = session["token"], session["profile"]["id"]
                    pending = helpers.call(base, "/auth/phone/code", "POST", {"phone": "13900000002"})
                else:
                    profile = helpers.call(base, "/me", token=token)
                    assert profile["id"] == profile_id and profile["phoneBound"], "Restart did not preserve the original account"
                    if index == 1:
                        # A challenge created before restart proves the relative key file was reused.
                        recovered = helpers.call(base, "/auth/phone", "POST", {
                            "phone": "13900000002", "code": pending["devCode"],
                            "challengeId": pending["challengeId"], "accepted": True,
                        })
                        assert recovered["profile"]["phoneBound"]
                assert not (project / "data/account-data.key").exists(), "Configured key file was ignored in favour of a generated key"
                assert_no_execution(project)
            finally:
                helpers.stop(process, stream)
        # Kept outside individual process lifetimes: the source file must never be rewritten.
        assert config.read_bytes() == original_config
        log_files.append(verify_process_files(helpers, java, jar, Path(temporary), environment,
                                             secret_password, account_key, report_json))
        for log in log_files:
            contents = log.read_text(encoding="utf-8")
            assert secret_password not in contents and account_key not in contents, "A secret value leaked to application logs"

    summary = (
        "Packaged JAR: automatic root .env discovery and server-subdirectory parent discovery: passed\n"
        "UTF-8 BOM/CRLF, Chinese values, ampersands and quoted JSON preserved: passed\n"
        "Dollar command substitutions and backticks treated as data, never executed: passed\n"
        "Relative ADMIN_BOOTSTRAP_PASSWORD_FILE and ACCOUNT_DATA_KEY_FILE (CRLF-terminated): passed\n"
        "One datasource/account location across root and server-directory restarts: passed\n"
        "Pre-restart OTP consumed after restart using the configured stable encryption key: passed\n"
        "Process environment overrides file values without changing the source .env: passed\n"
        "No dotenv: pure process ACCOUNT/ADMIN/SUPPORT/PRIVACY file aliases and PORT_FILE listening: passed\n"
        "Configured passwords and encryption keys absent from application logs: passed\n"
        "No application source changes, real SMS, real identity queries or money movement were required.\n"
    )
    if result_file:
        result_file.parent.mkdir(parents=True, exist_ok=True)
        result_file.write_text(summary)
    print(summary, end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "server/target/mall-1.0.0.jar")
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--result", type=Path, default=ROOT / "docs/test-reports/configured-startup.txt")
    arguments = parser.parse_args()
    if not arguments.java:
        parser.error("Java 21 runtime was not found")
    if not arguments.jar.is_file():
        parser.error("Build the packaged backend JAR first")
    verify(arguments.java, arguments.jar.resolve(), arguments.result)

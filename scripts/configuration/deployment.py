from pathlib import Path
import json
import os
import shutil
import uuid
from .environment import ROOT
from .dotenv import read
from .processes import executable, run

RUNTIME = ROOT / ".tools/deploy"


def prepare(values: dict[str, str], directory: Path = RUNTIME) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    sensitive = directory / ("files-" + uuid.uuid4().hex)
    sensitive.mkdir(mode=0o700)
    environment = {}
    declared = set(read(ROOT / ".env.example"))
    declared.update({"PUBLIC_API_URL", "PUBLIC_ASSET_URL", "WX_PAY_NOTIFY_URL", "WX_REFUND_NOTIFY_URL", "WX_TRANSFER_NOTIFY_URL", "JDK_JAVA_OPTIONS"})
    for name in sorted(declared):
        if (name in values and values[name] and not name.endswith("_FILE")
                and not name.startswith(("MYSQL_", "TLS_", "COMPOSE_"))
                and name not in {"DATABASE_MODE", "HTTPS_MODE", "HTTP_BIND", "HTTPS_BIND", "ACME_EMAIL", "APP_NETWORK_CIDR", "WEB_BIND", "TRUSTED_PROXY_CIDR", "API_UPSTREAM_URL", "BACKUP_DIR", "RESTORE_CONFIRMED", "SUJIAN_TOOLS_DIR", "MAVEN_SETTINGS", "MAVEN_OPTS", "MAVEN_HOME", "M2_HOME", "JAVA_OPTS", "JAVA_OPTS_JSON"}):
            environment[name] = "${" + name + "}"
    # Root config is parsed only by this runner, not copied/mounted into the image.
    environment.update({"APP_CONFIG_FILE": "", "APP_CONFIG_BASE_DIR": "/app", "APP_DEV_MODE": "false", "SPRING_PROFILES_ACTIVE": "prod"})
    for name in ("APP_BOOTSTRAP_CREDENTIALS_PATH", "ACCOUNT_DATA_KEY_PATH"):
        environment.pop(name, None)
    mounts = []
    for name, filename in [("WX_PRIVATE_KEY_PATH", "merchant-private.pem"), ("WX_PLATFORM_PUBLIC_KEY_PATH", "platform-public.pem")]:
        copied = sensitive / filename
        shutil.copyfile(values[name], copied)
        copied.chmod(0o444)
        destination = "/run/secrets/wechat/" + filename
        environment[name] = destination
        mounts.append({"type": "bind", "source": str(copied.resolve()), "target": destination, "read_only": True})
    if values.get("DATABASE_CA_PATH"):
        copied = sensitive / "database-ca.pem"
        shutil.copyfile(values["DATABASE_CA_PATH"], copied)
        copied.chmod(0o444)
        environment["DATABASE_CA_PATH"] = "/run/secrets/database/ca.pem"
        mounts.append({"type": "bind", "source": str(copied.resolve()), "target": environment["DATABASE_CA_PATH"], "read_only": True})
    override = {"services": {"server": {"environment": environment, "volumes": mounts},
                             "web": {"environment": {"TRUSTED_PROXY_CIDR": values.get("TRUSTED_PROXY_CIDR") or values["APP_NETWORK_CIDR"]}}}}
    if values.get("HTTPS_MODE", "caddy") == "certificate":
        for name, filename in [("TLS_CERT_PATH", "site.crt"), ("TLS_KEY_PATH", "site.key")]:
            copied = sensitive / filename
            shutil.copyfile(values[name], copied)
            copied.chmod(0o444)
            mounts_target = "/run/secrets/tls/" + filename
            override["services"].setdefault("edge", {}).setdefault("volumes", []).append({"type": "bind", "source": str(copied.resolve()), "target": mounts_target, "read_only": True})
        caddy = sensitive / "Caddyfile"
        # Address is supplied via the container environment, never interpolated into shell text.
        caddy.write_text("{$PUBLIC_STORE_URL} {\n tls /run/secrets/tls/site.crt /run/secrets/tls/site.key\n encode zstd gzip\n reverse_proxy web:80 {\n  header_up X-Forwarded-For {remote_host}\n  header_up -Forwarded\n }\n}\n", encoding="utf-8")
        caddy.chmod(0o444)
        override["services"]["edge"]["volumes"].append({"type": "bind", "source": str(caddy.resolve()), "target": "/etc/caddy/Caddyfile", "read_only": True})
    target = directory / "compose.runtime.json"
    temporary = target.with_suffix(".next")
    temporary.write_text(json.dumps(override, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.chmod(0o600)
    temporary.replace(target)
    return target


def command(values: dict[str, str], runtime: Path = RUNTIME / "compose.runtime.json") -> list[str]:
    project = values.get("COMPOSE_PROJECT_NAME", "simply-seen")
    if not project or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in project):
        raise ValueError("COMPOSE_PROJECT_NAME只能包含小写字母、数字、下划线和连字符")
    result = [executable("docker"), "compose", "--env-file", os.devnull, "--project-directory", str(ROOT), "--project-name", project, "-f", str(ROOT / "compose.yml")]
    if runtime.is_file():
        result += ["-f", str(runtime)]
    if values.get("DATABASE_MODE", "embedded") == "embedded":
        result += ["--profile", "database"]
    if values.get("HTTPS_MODE", "caddy") != "external":
        result += ["--profile", "https"]
    return result


def clean_old(directory: Path = RUNTIME, *, all_files: bool = False) -> None:
    active = set()
    current = directory / "compose.runtime.json"
    if current.is_file() and not all_files:
        data = json.loads(current.read_text())
        for service in data.get("services", {}).values():
            for mount in service.get("volumes", []):
                if isinstance(mount, dict) and mount.get("source"):
                    active.add(Path(mount["source"]).parent)
    for old in directory.glob("files-*"):
        if old.is_dir() and old not in active:
            shutil.rmtree(old)
    if all_files:
        current.unlink(missing_ok=True)

from pathlib import Path
import os
from .dotenv import read

ROOT = Path(__file__).resolve().parents[2]


def load(config: str | None = None, inherited: dict[str, str] | None = None, secret_names: set[str] | None = None) -> tuple[dict[str, str], Path, Path | None]:
    inherited = dict(os.environ if inherited is None else inherited)
    configured = config if config is not None else inherited.get("APP_CONFIG_FILE")
    path = None if configured == "" else Path(configured or ROOT / ".env").expanduser().resolve()
    if path is not None and not path.exists():
        if configured is not None:
            raise ValueError("APP_CONFIG_FILE指定的配置文件不存在")
        path = None
    base = path.parent if path is not None else ROOT
    stored = read(path) if path is not None else {}
    merged = {**stored, **inherited}
    # Each higher-priority source chooses either its direct value or its _FILE value.
    prefixes = ("APP_", "ACCOUNT_", "WX_", "SMS_", "IDENTITY_", "DATABASE_", "MYSQL_", "LOGISTICS_", "ADMIN_", "PUBLIC_", "SUPPORT_", "PRIVACY_", "SESSION_", "CORS_", "ORDER_", "JOB_", "MAINTENANCE_", "API_", "WITHDRAW_", "TRANSFER_", "MONTHLY_", "DISTRIBUTION_")
    declared = set(read(ROOT / ".env.example"))
    names = {key[:-5] for source in (stored, inherited) for key in source
             if key.endswith("_FILE") and key not in {"APP_CONFIG_FILE", "RESTORE_FILE"}
             and (key in stored or key.startswith(prefixes) or key[:-5] in declared)}
    for name in names:
        if secret_names is not None and name not in secret_names:
            continue
        for source in (inherited, stored):
            direct, indirect = source.get(name, ""), source.get(name + "_FILE", "")
            if direct:
                merged[name] = direct
                break
            if indirect:
                file = resolve(base, indirect)
                if not file.is_file() or file.stat().st_size > 65536:
                    raise ValueError(f"{name}_FILE不可读取或超过64KiB")
                try:
                    with file.open(encoding="utf-8", newline="") as stream:
                        contents = stream.read()
                except (OSError, UnicodeError):
                    raise ValueError(f"{name}_FILE不可读取") from None
                if contents.endswith("\r\n"):
                    contents = contents[:-2]
                elif contents.endswith("\n"):
                    contents = contents[:-1]
                if not contents or "\0" in contents:
                    raise ValueError(f"{name}_FILE内容为空或无效")
                merged[name] = contents
                break
            if name in source:
                merged[name] = direct
                break
    merged["APP_CONFIG_BASE_DIR"] = str(base)
    if path is not None:
        merged["APP_CONFIG_FILE"] = str(path)
    else:
        merged["APP_CONFIG_FILE"] = ""
    for key in list(merged):
        if (key.endswith("_PATH") or key in {"BACKUP_DIR", "RESTORE_FILE", "MAVEN_SETTINGS", "SUJIAN_TOOLS_DIR"}) and merged[key]:
            merged[key] = str(resolve(base, merged[key]))
    for name in list(merged):
        if name.endswith("_BASE_URL") or name in {"PUBLIC_STORE_URL", "PUBLIC_API_URL", "PUBLIC_ASSET_URL"}:
            merged[name] = merged[name].rstrip("/")
    defaults(merged)
    return merged, base, path


def resolve(base: Path, value: str) -> Path:
    path = Path(value).expanduser()
    return (path if path.is_absolute() else base / path).resolve()


def defaults(values: dict[str, str]) -> None:
    def fill(name: str, value: str) -> None:
        if not values.get(name):
            values[name] = value
    for name in ("DATABASE_MODE", "HTTPS_MODE", "WX_WEB_PAYMENT_MODE"):
        if values.get(name):
            values[name] = values[name].lower()
    database = values.get("MYSQL_DATABASE", "")
    import re
    embedded = values.get("DATABASE_MODE", "embedded") == "embedded"
    if embedded and re.fullmatch(r"[A-Za-z0-9_]{1,64}", database):
        fill("DATABASE_URL", "jdbc:mysql://mysql:3306/" + database + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=REQUIRED")
    if embedded:
        fill("DATABASE_USER", values.get("MYSQL_USER", ""))
        fill("DATABASE_PASSWORD", values.get("MYSQL_PASSWORD", ""))
    store = values.get("PUBLIC_STORE_URL", "").rstrip("/")
    if store:
        fill("PUBLIC_API_URL", store + "/api/v1")
        fill("PUBLIC_ASSET_URL", store)
        api = values["PUBLIC_API_URL"].rstrip("/")
        for kind in ("PAY", "REFUND", "TRANSFER"):
            fill(f"WX_{kind}_NOTIFY_URL", api + "/callbacks/wechat/" + kind.lower())
    fill("WX_WEB_PAYMENT_MODE", "native")
    fill("HTTPS_MODE", "caddy")
    fill("APP_NETWORK_CIDR", "172.30.10.0/24")
    fill("API_UPSTREAM_URL", "http://server:" + values.get("PORT", "8080"))
    fill("WEB_BIND", "127.0.0.1:8088")

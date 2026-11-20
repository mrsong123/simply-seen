from pathlib import Path
import base64
import ipaddress
import json
import re
from urllib.parse import parse_qs, urlsplit
from .keys import validate as validate_key


class ConfigurationError(ValueError):
    pass


def validate(values: dict[str, str], *, compose: bool = False, mini: bool = False) -> None:
    errors: list[str] = []

    def required(name: str) -> str:
        value = values.get(name, "")
        if not value.strip():
            errors.append(f"缺少{name}（或{name}_FILE）")
        return value

    def https(name: str, *, query: bool = True) -> None:
        value = required(name)
        if not value:
            return
        try:
            address = urlsplit(value)
            if address.scheme != "https" or not address.hostname or any(character.isspace() or ord(character) < 32 for character in value) or address.username or address.password or address.fragment or (not query and address.query):
                raise ValueError()
            address.port
        except ValueError:
            errors.append(f"{name}必须为有效HTTPS地址且不能含凭据或片段")

    if values.get("APP_DEV_MODE", "false").lower() != "false":
        errors.append("生产构建/部署要求APP_DEV_MODE=false")
    if values.get("SPRING_PROFILES_ACTIVE", "prod") != "prod":
        errors.append("生产构建/部署要求SPRING_PROFILES_ACTIVE=prod")
    database_url = required("DATABASE_URL")
    if not database_url.startswith("jdbc:mysql:"):
        errors.append("DATABASE_URL生产必须使用jdbc:mysql连接")
    if re.search(r"[?&;](?:user|password)=", database_url, re.IGNORECASE):
        errors.append("DATABASE_URL不能内嵌账号或密码，请使用DATABASE_USER/PASSWORD或对应_FILE")
    if values.get("DATABASE_CA_PATH"):
        ca = Path(values["DATABASE_CA_PATH"])
        if not ca.is_file() or ca.stat().st_size > 65536:
            errors.append("DATABASE_CA_PATH必须为可读取且不超过64KiB的数据库CA证书文件")
        try:
            mode = parse_qs(urlsplit(database_url[5:]).query).get("sslMode", ["VERIFY_IDENTITY"])[-1].upper()
            if mode not in {"VERIFY_CA", "VERIFY_IDENTITY"}:
                errors.append("DATABASE_CA_PATH要求数据库sslMode为VERIFY_CA或VERIFY_IDENTITY")
        except ValueError:
            errors.append("DATABASE_URL格式无效")
    for name in ["DATABASE_USER", "DATABASE_PASSWORD", "ADMIN_BOOTSTRAP_USER", "ADMIN_BOOTSTRAP_PASSWORD", "WX_APP_ID", "WX_APP_SECRET", "WX_MCH_ID", "WX_MERCHANT_SERIAL", "WX_PLATFORM_SERIAL", "SUPPORT_CONTACT", "PRIVACY_CONTACT"]:
        required(name)
    if values.get("ADMIN_BOOTSTRAP_PASSWORD") and len(values["ADMIN_BOOTSTRAP_PASSWORD"]) < 12:
        errors.append("ADMIN_BOOTSTRAP_PASSWORD至少12位")
    for name in ["PUBLIC_STORE_URL", "PUBLIC_API_URL", "PUBLIC_ASSET_URL", "WX_LOGIN_URL", "WX_PAY_BASE_URL", "WX_PAY_NOTIFY_URL", "WX_REFUND_NOTIFY_URL"]:
        https(name)
    try:
        store = urlsplit(values.get("PUBLIC_STORE_URL", ""))
    except ValueError:
        store = urlsplit("")
    if store.path not in ("", "/") or store.query:
        errors.append("PUBLIC_STORE_URL应为站点根地址，不能包含子路径或查询参数")
    if len(required("WX_API_V3_KEY").encode()) != 32:
        errors.append("WX_API_V3_KEY必须恰为32字节")
    try:
        if len(base64.b64decode(required("ACCOUNT_DATA_KEY"), validate=True)) != 32:
            raise ValueError()
    except (ValueError, TypeError):
        errors.append("ACCOUNT_DATA_KEY必须为32字节随机值的Base64编码")
    for name, private in [("WX_PRIVATE_KEY_PATH", True), ("WX_PLATFORM_PUBLIC_KEY_PATH", False)]:
        file = required(name)
        if file:
            try:
                validate_key(Path(file), private)
            except ValueError as error:
                errors.append(f"{name}：{error}")
    for prefix in ("SMS", "IDENTITY"):
        if values.get(prefix + "_PROVIDER", "aliyun").lower() != "aliyun":
            errors.append(prefix + "_PROVIDER生产必须为aliyun")
        for suffix in ("ACCESS_KEY_ID", "ACCESS_KEY_SECRET", "REGION_ID"):
            required(prefix + "_" + suffix)
        https(prefix + "_API_URL", query=False)
    required("SMS_SIGN_NAME")
    required("SMS_TEMPLATE_CODE")
    if values.get("WX_WEB_PAYMENT_MODE", "native").lower() not in {"native", "h5"}:
        errors.append("WX_WEB_PAYMENT_MODE只允许native或h5")
    _integer(values, errors, "SMS_DAILY_LIMIT", 1000, 1, 1000000)
    _integer(values, errors, "SESSION_HOURS", 168, 1, 720)
    _integer(values, errors, "ORDER_EXPIRE_MINUTES", 15, 1, 120)
    _integer(values, errors, "PORT", 8080, 1, 65535)
    if values.get("TRANSFER_ENABLED", "false").lower() == "true":
        for name in ("WX_TRANSFER_BASE_URL", "WX_TRANSFER_NOTIFY_URL"):
            https(name)
        required("WX_TRANSFER_SCENE_ID")
        required("WX_TRANSFER_PERCEPTION")
        try:
            entries = json.loads(required("WX_TRANSFER_REPORT_INFOS"))
            if not isinstance(entries, list) or not entries or any(not isinstance(row, dict) or any(not isinstance(row.get(k), str) or not row[k].strip() for k in ("info_type", "info_content")) for row in entries):
                raise ValueError()
        except (ValueError, TypeError):
            errors.append("WX_TRANSFER_REPORT_INFOS须为非空JSON数组，包含info_type及info_content")
    if values.get("LOGISTICS_QUERY_URL"):
        https("LOGISTICS_QUERY_URL")
        required("LOGISTICS_CUSTOMER")
        required("LOGISTICS_KEY")
        try:
            carriers = json.loads(required("LOGISTICS_CARRIER_CODES"))
            if not isinstance(carriers, dict) or not carriers or any(not isinstance(v, str) or not v for v in carriers.values()):
                raise ValueError()
        except (ValueError, TypeError):
            errors.append("LOGISTICS_CARRIER_CODES须为非空服务商编码JSON对象")
    if mini and not re.fullmatch(r"wx[a-zA-Z0-9]{16}", values.get("WX_APP_ID", "")):
        errors.append("小程序WX_APP_ID应为wx开头的18位真实AppID")
    if compose:
        try:
            upstream = urlsplit(values.get("API_UPSTREAM_URL", ""))
            if upstream.scheme not in {"http", "https"} or not upstream.hostname or upstream.username or upstream.password or upstream.path not in {"", "/"} or upstream.query or upstream.fragment:
                raise ValueError()
            if upstream.path == "/":
                errors.append("API_UPSTREAM_URL不能以/结尾，否则Nginx会移除/api路由前缀")
            if upstream.hostname == "server" and (upstream.port or (443 if upstream.scheme == "https" else 80)) != int(values.get("PORT", "8080")):
                errors.append("API_UPSTREAM_URL端口应与PORT一致，或留空由PORT自动派生")
        except ValueError:
            errors.append("API_UPSTREAM_URL必须为不带路径、查询或凭据的有效HTTP(S)基址")
        database_mode = values.get("DATABASE_MODE", "embedded")
        if database_mode not in {"embedded", "external"}:
            errors.append("DATABASE_MODE只允许embedded或external")
        if database_mode == "embedded":
            for name in ("MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD"):
                required(name)
            if values.get("DATABASE_PASSWORD") != values.get("MYSQL_PASSWORD") or values.get("DATABASE_USER") != values.get("MYSQL_USER"):
                errors.append("内置数据库模式DATABASE_USER/PASSWORD应与MYSQL_USER/PASSWORD一致，或留空由MYSQL变量派生")
        mode = values.get("HTTPS_MODE", "caddy").lower()
        if mode not in {"caddy", "certificate", "external"}:
            errors.append("HTTPS_MODE只允许caddy、certificate或external")
        if mode in {"caddy", "certificate"}:
            try:
                if store.port not in {None, 443}:
                    errors.append("内置HTTPS模式PUBLIC_STORE_URL须使用443端口；自定义公网端口请用external模式")
            except ValueError:
                pass
        if mode == "caddy":
            if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", required("ACME_EMAIL")):
                errors.append("ACME_EMAIL应为证书续签通知邮箱")
            try:
                ipaddress.ip_address(store.hostname or "")
                errors.append("自动HTTPS请使用已指向服务器的域名，或选择certificate/external模式")
            except ValueError:
                pass
        if mode == "certificate":
            for name in ("TLS_CERT_PATH", "TLS_KEY_PATH"):
                file = required(name)
                if file and not Path(file).is_file():
                    errors.append(name + "文件不可读取")
        try:
            network = ipaddress.ip_network(values.get("APP_NETWORK_CIDR", "172.30.10.0/24"))
            if (not network.is_private or network.version != 4 or not 16 <= network.prefixlen <= 28
                    or network.network_address.is_loopback or network.network_address.is_link_local):
                raise ValueError()
        except ValueError:
            errors.append("APP_NETWORK_CIDR应为独立私有IPv4网段（/16至/28）")
        if values.get("TRUSTED_PROXY_CIDR"):
            try:
                proxy = ipaddress.ip_network(values["TRUSTED_PROXY_CIDR"], strict=False)
                if proxy.prefixlen == 0:
                    raise ValueError()
            except ValueError:
                errors.append("TRUSTED_PROXY_CIDR须为实际代理的IP或CIDR且禁止信任全网")
    if errors:
        raise ConfigurationError("配置检查未通过：\n- " + "\n- ".join(dict.fromkeys(errors)))


def _integer(values, errors, name, default, minimum, maximum):
    try:
        if not minimum <= int(values.get(name, str(default))) <= maximum:
            raise ValueError()
    except ValueError:
        errors.append(f"{name}须为{minimum}..{maximum}的整数")

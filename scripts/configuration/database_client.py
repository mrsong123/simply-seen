"""Build database client arguments without exposing credentials in process arguments."""
from pathlib import Path
import os
import re
from urllib.parse import parse_qs, unquote, urlsplit
from . import deployment
from .tooling import executable


def command(values: dict[str, str], *, dump: bool) -> tuple[list[str], dict[str, str]]:
    if values.get("DATABASE_MODE", "embedded") == "embedded":
        operation = ('mysqldump -u "$MYSQL_USER" --single-transaction --routines --triggers '
                     '--no-tablespaces "$MYSQL_DATABASE"') if dump else 'mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
        return deployment.command(values) + ["exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_PASSWORD" ' + operation], values
    url = values.get("DATABASE_URL", "")
    try:
        if not url.startswith("jdbc:mysql://"):
            raise ValueError()
        parsed = urlsplit(url[5:])
        database = unquote(parsed.path.lstrip("/"))
        if not parsed.hostname or parsed.username or parsed.password or not re.fullmatch(r"[A-Za-z0-9_]{1,64}", database):
            raise ValueError()
        port = parsed.port or 3306
    except ValueError:
        raise ValueError("外部数据库备份要求标准jdbc:mysql://host:port/database连接，账号密码单独配置") from None
    if not values.get("DATABASE_USER") or not values.get("DATABASE_PASSWORD"):
        raise ValueError("外部数据库备份缺少DATABASE_USER或DATABASE_PASSWORD")
    project = values.get("COMPOSE_PROJECT_NAME", "simply-seen")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", project):
        raise ValueError("COMPOSE_PROJECT_NAME格式不正确")
    network = values.get("DATABASE_CLIENT_NETWORK") or project + "_default"
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", network):
        raise ValueError("DATABASE_CLIENT_NETWORK格式不正确")
    arguments = [executable("docker"), "run", "--rm", "--network", network,
                 "--add-host", "host.docker.internal:host-gateway", "-e", "MYSQL_PWD"]
    if not dump:
        # Docker must keep stdin open so the verified SQL stream reaches mysql.
        arguments.append("-i")
    options = parse_qs(parsed.query)
    mode = options.get("sslMode", ["VERIFY_IDENTITY"])[-1].upper()
    if mode not in {"DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"}:
        raise ValueError("DATABASE_URL的sslMode不受MySQL客户端支持")
    ca = values.get("DATABASE_CA_PATH")
    if ca:
        file = Path(ca)
        if not file.is_file():
            raise ValueError("DATABASE_CA_PATH文件不可读取")
        arguments += ["-v", str(file.resolve()) + ":/run/secrets/database/ca.pem:ro"]
    arguments += ["--entrypoint", "mysqldump" if dump else "mysql", "mysql:8.4",
                  "--host=" + parsed.hostname, "--port=" + str(port), "--user=" + values["DATABASE_USER"], "--ssl-mode=" + mode]
    if ca:
        arguments.append("--ssl-ca=/run/secrets/database/ca.pem")
    if dump:
        arguments += ["--single-transaction", "--routines", "--triggers", "--no-tablespaces"]
    arguments.append(database)
    return arguments, {**values, "MYSQL_PWD": values["DATABASE_PASSWORD"]}

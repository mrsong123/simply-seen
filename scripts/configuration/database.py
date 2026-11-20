from pathlib import Path
import gzip
import hashlib
import hmac
import os
import shutil
import subprocess
import tempfile
import uuid
from datetime import datetime, timezone
from .environment import ROOT
from .database_client import command as client_command


def _digest(file: Path) -> str:
    checksum = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def _verify(file: Path) -> None:
    checksum = file.with_name(file.name + ".sha256")
    if not checksum.is_file():
        raise ValueError("缺少备份SHA-256校验文件")
    parts = checksum.read_text().split()
    if not parts or len(parts[0]) != 64 or any(character not in "0123456789abcdefABCDEF" for character in parts[0]):
        raise ValueError("备份SHA-256校验文件格式不正确")
    expected = parts[0].lower()
    actual = _digest(file)
    if not hmac.compare_digest(expected, actual):
        raise ValueError("备份SHA-256校验失败")
    try:
        with gzip.open(file, "rb") as stream:
            while stream.read(1024 * 1024):
                pass
    except (OSError, EOFError):
        raise ValueError("备份压缩文件不完整或已损坏") from None


def backup(values: dict[str, str], output: str | None = None) -> Path:
    selected = output or values.get("BACKUP_DIR")
    if not selected:
        raise ValueError("请设置BACKUP_DIR或--output为受控备份目录")
    directory = Path(selected).expanduser().resolve()
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    file = directory / ("sujian-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + uuid.uuid4().hex[:8] + ".sql.gz")
    command, environment = client_command(values, dump=True)
    process = None
    created = False
    try:
        with os.fdopen(os.open(file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as raw, tempfile.TemporaryFile() as errors:
            created = True
            process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE, stderr=errors)
            assert process.stdout is not None
            with gzip.GzipFile(fileobj=raw, mode="wb") as compressed:
                shutil.copyfileobj(process.stdout, compressed)
            process.stdout.close()
            process.wait()
        if process.returncode:
            raise RuntimeError("数据库备份失败，请检查数据库服务、账号权限和磁盘空间")
        file.chmod(0o600)
        checksum = file.with_name(file.name + ".sha256")
        checksum.write_text(_digest(file) + "  " + file.name + "\n")
        checksum.chmod(0o600)
        _verify(file)
        return file
    except BaseException:
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
        if created:
            file.unlink(missing_ok=True)
            file.with_name(file.name + ".sha256").unlink(missing_ok=True)
        raise


def restore(values: dict[str, str], file: str, confirmed: bool) -> None:
    if not confirmed:
        raise ValueError("恢复会覆盖目标数据库；暂停写入并保存当前备份后，显式提供--confirm")
    source = Path(file).expanduser().resolve()
    _verify(source)
    command, environment = client_command(values, dump=False)
    with tempfile.TemporaryFile() as errors:
        process = subprocess.Popen(command, cwd=ROOT, env=environment, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=errors)
        try:
            assert process.stdin is not None
            with gzip.open(source, "rb") as stream:
                shutil.copyfileobj(stream, process.stdin)
            process.stdin.close()
            process.stdin = None
            process.wait()
            if process.returncode:
                raise RuntimeError("数据库恢复失败，请检查目标数据库权限和备份版本")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()

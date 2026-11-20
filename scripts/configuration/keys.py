"""Minimal ASN.1 structural RSA checks; the Java startup parser performs its own key validation."""
from pathlib import Path
import base64
import re

RSA_OID = bytes.fromhex("2a864886f70d010101")


def _item(data: bytes, at: int = 0) -> tuple[int, bytes, int]:
    if at + 2 > len(data):
        raise ValueError("Invalid DER")
    tag, size = data[at], data[at + 1]
    cursor = at + 2
    if size & 128:
        count = size & 127
        if count == 0 or count > 4 or cursor + count > len(data):
            raise ValueError("Invalid DER")
        size = int.from_bytes(data[cursor : cursor + count], "big")
        cursor += count
    if cursor + size > len(data):
        raise ValueError("Invalid DER")
    return tag, data[cursor : cursor + size], cursor + size


def _sequence(data: bytes) -> list[tuple[int, bytes]]:
    tag, contents, end = _item(data)
    if tag != 48 or end != len(data):
        raise ValueError("Invalid DER sequence")
    items, cursor = [], 0
    while cursor < len(contents):
        child, value, cursor = _item(contents, cursor)
        items.append((child, value))
    return items


def _encoded(tag: int, value: bytes) -> bytes:
    size = len(value)
    length = bytes([size]) if size < 128 else bytes([128 + (size.bit_length() + 7) // 8]) + size.to_bytes((size.bit_length() + 7) // 8, "big")
    return bytes([tag]) + length + value


def validate(path: Path, private: bool) -> None:
    try:
        if not path.is_file() or path.stat().st_size > 65536:
            raise ValueError()
        text = path.read_text(encoding="ascii")
        match = re.fullmatch(r"\s*-----BEGIN ([A-Z ]+)-----\s*([A-Za-z0-9+/=\s]+)\s*-----END \1-----\s*", text)
        if not match:
            raise ValueError()
        kind = match[1]
        data = base64.b64decode(re.sub(r"\s", "", match[2]), validate=True)
        items = _sequence(data)
        if private:
            if kind == "PRIVATE KEY":
                if items[1][0] != 48 or items[2][0] != 4 or RSA_OID not in items[1][1]:
                    raise ValueError()
                items = _sequence(items[2][1])
            elif kind != "RSA PRIVATE KEY":
                raise ValueError()
            if len(items) < 9 or items[1][0] != 2:
                raise ValueError()
            modulus = items[1][1]
        else:
            if kind == "CERTIFICATE":
                if not items or items[0][0] != 48:
                    raise ValueError()
                certificate = _sequence(_encoded(*items[0]))
                offset = 1 if certificate[0][0] == 160 else 0
                data = _encoded(*certificate[offset + 5])
                items = _sequence(data)
            elif kind not in {"PUBLIC KEY", "RSA PUBLIC KEY"}:
                raise ValueError()
            if kind == "RSA PUBLIC KEY":
                public = items
            else:
                if len(items) != 2 or items[0][0] != 48 or RSA_OID not in items[0][1] or items[1][0] != 3 or items[1][1][0] != 0:
                    raise ValueError()
                public = _sequence(items[1][1][1:])
            if len(public) != 2 or public[0][0] != 2:
                raise ValueError()
            modulus = public[0][1]
        if int.from_bytes(modulus, "big").bit_length() < 2048:
            raise ValueError()
    except (ValueError, OSError, UnicodeError, IndexError):
        raise ValueError("密钥文件必须为可解析的2048位或以上RSA PEM（商户私钥/平台公钥或证书）") from None

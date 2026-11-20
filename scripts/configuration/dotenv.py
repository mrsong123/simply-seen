"""Parse dotenv as UTF-8 data. Never execute shell syntax or expand variables."""
from pathlib import Path
import re

NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_]*\Z")


def parse(text: str) -> dict[str, str]:
    text = text.removeprefix("\ufeff")
    if "\0" in text:
        raise ValueError("配置文件包含非法字符，内容不会输出")
    lines = text.splitlines()
    result: dict[str, str] = {}
    index = 0
    while index < len(lines):
        number = index + 1
        entry = lines[index].strip()
        index += 1
        if not entry or entry.startswith("#"):
            continue
        if entry.startswith(("export ", "export\t")):
            entry = entry[7:].lstrip()
        name, equal, value = entry.partition("=")
        name = name.strip()
        if not equal or not NAME.fullmatch(name):
            raise ValueError(f"配置文件第{number}行格式错误，内容不会输出")
        value = value.lstrip()
        if value.startswith(('"', "'")):
            quote, remaining = value[0], value[1:]
            closing = _closing(remaining, quote)
            while closing < 0 and index < len(lines):
                remaining += "\n" + lines[index]
                index += 1
                closing = _closing(remaining, quote)
            if closing < 0:
                raise ValueError(f"配置文件第{number}行引号未闭合")
            tail = remaining[closing + 1 :].strip()
            if tail and not tail.startswith("#"):
                raise ValueError(f"配置文件第{number}行引号后有非注释内容")
            contents = remaining[:closing]
            value = contents if quote == "'" else _decode(contents)
        else:
            inner, escaped = None, False
            for at, character in enumerate(value):
                if inner is None and character in ('"', "'"):
                    inner = character
                elif inner is not None and character == inner and not escaped:
                    inner = None
                elif inner is None and character == "#" and (at == 0 or value[at - 1].isspace()):
                    value = value[:at]
                    break
                escaped = inner == '"' and character == "\\" and not escaped
            value = value.rstrip()
        result[name] = value
    return result


def _closing(value: str, quote: str) -> int:
    escaped = False
    for index, character in enumerate(value):
        if character == quote and not escaped:
            return index
        escaped = quote == '"' and character == "\\" and not escaped
    return -1


def _decode(value: str) -> str:
    decoded: list[str] = []
    index = 0
    replacements = {"n": "\n", "r": "\r", "t": "\t", '"': '"', "\\": "\\"}
    while index < len(value):
        if value[index] == "\\" and index + 1 < len(value):
            following = value[index + 1]
            decoded.append(replacements.get(following, "\\" + following))
            index += 2
        else:
            decoded.append(value[index])
            index += 1
    return "".join(decoded)


def read(path: Path) -> dict[str, str]:
    if not path.is_file() or path.stat().st_size > 1048576:
        raise ValueError("配置文件不存在、不可读取或超过1MiB")
    try:
        return parse(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError):
        raise ValueError("配置文件不可读取") from None

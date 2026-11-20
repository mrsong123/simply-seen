"""Launch Maven/npm without a shell, including Windows installations with spaces."""
from pathlib import Path
import hashlib
import os
import re
import shlex
import shutil
import tempfile
import subprocess
import urllib.request
import zipfile
from urllib.parse import urlsplit, unquote
from xml.sax.saxutils import escape
from .environment import ROOT

MAVEN_VERSION = "3.9.11"
MAVEN_SHA512 = "03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076"


def executable(name: str) -> str:
    located = shutil.which(name)
    if located:
        return located
    raise RuntimeError(f"未安装{name}，请先安装文档列出的运行工具")


def java_executable(values: dict[str, str], name: str = "java") -> str:
    home = values.get("JAVA_HOME")
    if home:
        suffix = ".exe" if os.name == "nt" else ""
        candidate = Path(home).expanduser() / "bin" / (name + suffix)
        if not candidate.is_file():
            raise RuntimeError("JAVA_HOME未提供" + name + "，请检查完整JDK21安装")
        return str(candidate.resolve())
    return executable(name)


def check_build_versions(values: dict[str, str], *, cloud_ecj: bool) -> None:
    commands = [(java_executable(values), "-version", "Java"), (executable("node"), "--version", "Node")]
    if not cloud_ecj:
        commands.append((java_executable(values,"javac"), "-version", "Javac"))
    for program, argument, kind in commands:
        try:
            result = subprocess.run([program, argument], env=values, capture_output=True, text=True, timeout=15)
        except (OSError, subprocess.TimeoutExpired):
            raise RuntimeError("无法检查" + kind + "版本") from None
        text = result.stdout + "\n" + result.stderr
        pattern = r"(?m)^v(\d+)\.(\d+)" if kind=="Node" else r'(?m)^(?:openjdk|java|javac)(?:\s+version)?\s+"?(\d+)(?:\.(\d+))?'
        match = re.search(pattern,text)
        if result.returncode or not match:
            raise RuntimeError("无法识别" + kind + "版本，请使用文档指定的安装版本")
        major = int(match[1]); minor = int(match[2] or 0)
        if kind=="Node" and (major<22 or major==22 and minor<12):
            raise RuntimeError("构建要求Node22.12或以上")
        if kind!="Node" and major!=21:
            raise RuntimeError("构建要求Java/Javac21，请设置JAVA_HOME指向完整JDK21")


def maven(values: dict[str, str]) -> list[str]:
    candidates = [Path(values[key]) for key in ("MAVEN_HOME", "M2_HOME") if values.get(key)]
    installed = shutil.which("mvn")
    if installed:
        candidates.append(Path(installed).resolve().parent.parent)
    tools = Path(values.get("SUJIAN_TOOLS_DIR") or ROOT / ".tools").expanduser().resolve()
    candidates.append(tools / ("apache-maven-" + MAVEN_VERSION))
    home = next((directory for directory in candidates if (directory / "bin/m2.conf").is_file()), None)
    if home is None:
        tools.mkdir(parents=True, exist_ok=True)
        home = tools / ("apache-maven-" + MAVEN_VERSION)
        with tempfile.NamedTemporaryFile(prefix="maven-", suffix=".zip", dir=tools, delete=False) as stream:
            archive = Path(stream.name)
        try:
            url = f"https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/{MAVEN_VERSION}/apache-maven-{MAVEN_VERSION}-bin.zip"
            with urllib.request.urlopen(url, timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
            if hashlib.sha512(archive.read_bytes()).hexdigest() != MAVEN_SHA512:
                raise RuntimeError("Maven下载包SHA-512不匹配")
            with zipfile.ZipFile(archive) as zipped:
                for item in zipped.infolist():
                    destination = (tools / item.filename).resolve()
                    if not destination.is_relative_to(tools.resolve()):
                        raise RuntimeError("Maven压缩包路径无效")
                zipped.extractall(tools)
        finally:
            archive.unlink(missing_ok=True)
    boot = list((home / "boot").glob("plexus-classworlds-*.jar"))
    if len(boot) != 1:
        raise RuntimeError("Maven安装不完整，请删除不完整缓存后重试")
    command = [java_executable(values), *shlex.split(values.get("MAVEN_OPTS", "")), "-classpath", str(boot[0]),
               "-Dclassworlds.conf=" + str(home / "bin/m2.conf"), "-Dmaven.home=" + str(home),
               "-Dmaven.multiModuleProjectDirectory=" + str(ROOT), "org.codehaus.plexus.classworlds.launcher.Launcher"]
    selected = values.get("MAVEN_SETTINGS")
    if selected:
        return command + ["--settings", selected]
    proxy = values.get("HTTPS_PROXY") or values.get("https_proxy")
    if proxy:
        target = urlsplit(proxy)
        if not target.hostname:
            raise RuntimeError("HTTPS_PROXY地址无效")
        credentials = ""
        if target.username:
            credentials = "<username>" + escape(unquote(target.username)) + "</username><password>" + escape(unquote(target.password or "")) + "</password>"
        entries = "".join("<proxy><id>manage-" + protocol + "</id><active>true</active><protocol>" + protocol + "</protocol><host>" + escape(target.hostname) + "</host><port>" + str(target.port or 8080) + "</port>" + credentials + "<nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy>" for protocol in ("http", "https"))
        tools.mkdir(parents=True, exist_ok=True)
        settings = tools / "manage-maven-settings.xml"
        settings.write_text("<settings><localRepository>" + escape(str(tools / "m2")) + "</localRepository><proxies>" + entries + "</proxies></settings>", encoding="utf-8")
        settings.chmod(0o600)
        command += ["--settings", str(settings)]
    return command


def npm() -> list[str]:
    entry = shutil.which("npm") or shutil.which("npm.cmd")
    if not entry:
        raise RuntimeError("未安装Node.js/npm，请安装Node22.12或以上")
    located = Path(entry).resolve()
    candidates = [located if located.name == "npm-cli.js" else located.parent / "node_modules/npm/bin/npm-cli.js",
                  located.parent / "../lib/node_modules/npm/bin/npm-cli.js",
                  located.parent / "../node_modules/npm/bin/npm-cli.js"]
    script = next((path.resolve() for path in candidates if path.is_file()), None)
    if script is None:
        raise RuntimeError("找不到npm-cli.js，请修复Node.js/npm安装")
    return [executable("node"), str(script)]

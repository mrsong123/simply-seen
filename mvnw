#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
TOOLS="${SUJIAN_TOOLS_DIR:-$ROOT/.tools}"
MAVEN="$TOOLS/apache-maven-3.9.11/bin/mvn"
if [[ ! -x "$MAVEN" ]]; then
 mkdir -p "$TOOLS"
 archive="$(mktemp "$TOOLS/maven-XXXXXX.tar.gz")"
 trap 'rm -f "$archive"' EXIT
 curl --fail --location --proto '=https' --tlsv1.2 https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz --output "$archive"
 printf '%s  %s\n' 'bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978' "$archive" | sha512sum -c -
 tar -xzf "$archive" -C "$TOOLS"
fi
if [[ -n "${HTTPS_PROXY:-${https_proxy:-}}" ]] && [[ " $* " != *" -s "* ]] && [[ " $* " != *" --settings "* ]]; then
 export SUJIAN_TOOLS_DIR="$TOOLS"
 python3 - <<'PYSETTINGS'
import os
from pathlib import Path
from urllib.parse import urlparse
from xml.sax.saxutils import escape
proxy=urlparse(os.environ.get('HTTPS_PROXY') or os.environ['https_proxy'])
root=Path(os.environ['SUJIAN_TOOLS_DIR'])
entries=''.join('<proxy><id>session-'+protocol+'</id><active>true</active><protocol>'+protocol+'</protocol><host>'+escape(proxy.hostname or '')+'</host><port>'+str(proxy.port or 8080)+'</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy>' for protocol in ['http','https'])
path=root/'settings.xml'
path.write_text('<settings><localRepository>'+escape(str(root/'m2'))+'</localRepository><proxies>'+entries+'</proxies></settings>')
path.chmod(0o600)
PYSETTINGS
 exec "$MAVEN" -s "$TOOLS/settings.xml" "$@"
fi
exec "$MAVEN" "$@"

#!/usr/bin/env bash
# 从根 pom.xml 读取项目版本，并把两个子模块 <parent><version> 同步成同一版本。
# 成功时只在 stdout 输出版本号，日志全部走 stderr，方便 CI 用 $(...) 捕获。
set -euo pipefail

ROOT_POM="${1:-pom.xml}"
API_POM="${2:-lengbanlist-api/pom.xml}"
MAIN_POM="${3:-Lengbanlist - main/pom.xml}"

if [ ! -f "$ROOT_POM" ]; then
  echo "::error::找不到根 pom：$ROOT_POM" >&2
  exit 1
fi

VERSION=$(
  python3 - "$ROOT_POM" <<'PY'
import sys
import xml.etree.ElementTree as ET

pom = sys.argv[1]
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(pom).getroot()
version = root.find("m:version", namespace)
if version is None or not (version.text or "").strip():
    raise SystemExit(1)
print(version.text.strip())
PY
)

if [ -z "${VERSION:-}" ]; then
  echo "::error::无法从 $ROOT_POM 读取 <version>" >&2
  exit 1
fi

for pom in "$API_POM" "$MAIN_POM"; do
  if [ ! -f "$pom" ]; then
    echo "::error::找不到子模块 pom：$pom" >&2
    exit 1
  fi

  # 只改 <parent> 块里的 <version>，不碰依赖和插件版本；按字节写回，保留原行尾
  python3 - "$pom" "$VERSION" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
version = sys.argv[2]
text = path.read_bytes().decode("utf-8")
pattern = re.compile(r"(<parent>.*?<version>)[^<]+(</version>.*?</parent>)", re.S)
new_text, count = pattern.subn(rf"\g<1>{version}\g<2>", text, count=1)
if count != 1:
    raise SystemExit(f"::error::未找到 {path} 的 <parent><version>")

match = re.search(r"<parent>.*?<version>([^<]+)</version>.*?</parent>", new_text, re.S)
if not match or match.group(1) != version:
    raise SystemExit(f"::error::同步 {path} 失败")

path.write_bytes(new_text.encode("utf-8"))
print(f"已同步 {path} -> {version}", file=sys.stderr)
PY
done

printf '%s\n' "$VERSION"

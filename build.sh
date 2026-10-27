#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  PixelmonTXZ（原 PokePass）构建脚本：javac + jar，无 Maven/Gradle 依赖
#
#  与其他五个插件（PokeTraining / PokeTools / PokeMarket / PokeRanked /
#  PokeUtils）保持同一套约定：
#    · 版本号只在 plugin/src/main/resources/plugin.yml 写一次，jar 名由
#      `name + version` 推导（PixelmonTXZ-1.2.0.jar），不带 -SNAPSHOT、不带日期；
#    · 依赖统一取工作区根的 ../_libs/；
#    · 部署规则走 _tools/deploy.sh（服在跑就不覆盖）。
#
#  历史上这里是 build.ps1 —— 全套六个插件里唯一的 PowerShell 脚本，参数风格
#  （-Deploy 开关）与其余五份 bash 不一致，`build-all` 没法统一调。改写成 bash
#  之后六个插件可以用同一条命令、同一组参数构建。
#
#  用法:
#    ./build.sh            构建；服务端没在跑就顺带部署
#    ./build.sh deploy     构建并部署（服在跑同样跳过，只提示）
#    ./build.sh no-deploy  只构建
# ---------------------------------------------------------------------------
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SRC="plugin/src/main/java"
RES="plugin/src/main/resources"
STUB_SRC="stub"
CLASSES="build/classes"
STUBS="build/stubs"
DIST="dist"
# 版本号与插件名只在 plugin.yml 里写一次，jar 名与服务端报告的版本都从它推导
PLUGIN_NAME="$(sed -n 's/^name:[[:space:]]*//p' "$RES/plugin.yml" | head -1 | tr -d '\r')"
BASE_VERSION="$(sed -n 's/^version:[[:space:]]*//p' "$RES/plugin.yml" | head -1 | tr -d '\r')"
[ -n "$PLUGIN_NAME" ] || PLUGIN_NAME="PixelmonTXZ"
[ -n "$BASE_VERSION" ] || BASE_VERSION="1.0.0"
JAR_NAME="${PLUGIN_NAME}-${BASE_VERSION}.jar"

JDK_HOME="${JDK_HOME:-/c/Program Files/Java/jdk1.8.0_191}"
JAVAC="$JDK_HOME/bin/javac"
JAR="$JDK_HOME/bin/jar"
[ -x "$JAVAC" ] || { echo "找不到 javac: $JAVAC （用 JDK_HOME 指定 JDK 8）" >&2; exit 1; }

# 依赖统一放在工作区根的 _libs，六个项目共用一份
CS="../_libs/CatServer-1.12.2-universal.jar"
PM="../_libs/Pixelmon-1.12.2-8.4.3-universal.jar"
PAPI="../_libs/PlaceholderAPI-2.11.7.jar"
for f in "$CS" "$PM" "$PAPI"; do
  if [ ! -f "$f" ]; then
    echo "缺少依赖：$f" >&2
    exit 1
  fi
done

echo "==> 清理"
rm -rf build "dist/$JAR_NAME"
mkdir -p "$CLASSES" "$STUBS" "$DIST"
# dist/ 只留当前版本，旧产物移进 _legacy/（规则见 _tools/build-lib.sh）
. "$ROOT/../_tools/build-lib.sh"
sweep_dist "$DIST" "$JAR_NAME"

CSW="$(cygpath -m "$ROOT/$CS" 2>/dev/null || echo "$ROOT/$CS")"
PMW="$(cygpath -m "$ROOT/$PM" 2>/dev/null || echo "$ROOT/$PM")"
PAPIW="$(cygpath -m "$ROOT/$PAPI" 2>/dev/null || echo "$ROOT/$PAPI")"
STUBW="$(cygpath -m "$ROOT/$STUBS" 2>/dev/null || echo "$ROOT/$STUBS")"

if [ -n "$(find "$STUB_SRC" -name '*.java' 2>/dev/null)" ]; then
  echo "==> 编译 NMS 桩（仅编译期用，不进产物）"
  find "$STUB_SRC" -name '*.java' > build/stub-sources.txt
  "$JAVAC" -J-Duser.language=en -J-Duser.country=US -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
    -d "$STUBS" @build/stub-sources.txt
fi

echo "==> 编译插件"
export CLASSPATH="$CSW;$PMW;$PAPIW;$STUBW"
find "$SRC" -name '*.java' > build/sources.txt
"$JAVAC" -J-Duser.language=en -J-Duser.country=US -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
  -d "$CLASSES" @build/sources.txt

echo "==> 打包资源"
cp -r "$RES/." "$CLASSES/"

echo "==> 生成 jar"
"$JAR" cf "$DIST/$JAR_NAME" -C "$CLASSES" .

SIZE=$(wc -c < "$DIST/$JAR_NAME")
echo "    产物: dist/$JAR_NAME ($SIZE 字节)"
echo "    版本: $BASE_VERSION"
echo "    入口: $(unzip -p "$DIST/$JAR_NAME" plugin.yml | grep '^main:' | tr -d '\r')"

# 部署规则只有一份，见 _tools/deploy.sh（服在跑不覆盖、同名旧版本先删）
TEST_SERVER_DIR="$ROOT/../TestServer/plugins"
. "$ROOT/../_tools/deploy.sh"
deploy_jar "$PLUGIN_NAME" "$ROOT/$DIST/$JAR_NAME" "$@"

#!/bin/bash
# 坚果云第三方应用管理客户端 - APK 手动构建脚本（aapt2 + javac + d8 + apksigner）
set -e

if [ -z "$LOCALAPPDATA" ]; then
    LOCALAPPDATA="${APPDATA/Roaming/Local}"
fi
SDK="$LOCALAPPDATA/Android/Sdk"

# 定位 JDK（javac / jar / keytool）
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
fi
export PATH="$JAVA_HOME/bin:$PATH"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"

ROOT="$(cd "$(dirname "$0")" && pwd -W)"
APP="$ROOT/app"
BUILD="$ROOT/build"
OUT_APK="$ROOT/../坚果云应用管理.apk"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/apk"

echo "[1/7] 生成图标..."
python "$ROOT/gen_icon.py"

echo "[2/7] 编译资源 (aapt2 compile)..."
"$BT/aapt2.exe" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "[3/7] 链接资源 (aapt2 link)..."
"$BT/aapt2.exe" link \
    -o "$BUILD/apk/app.ap_" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --auto-add-overlay

echo "[4/7] 编译 Java (javac)..."
find "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
find "$APP/src" -name "*.java" >> "$BUILD/sources.txt"
javac -source 8 -target 8 \
    -classpath "$PLATFORM" \
    -d "$BUILD/classes" \
    -encoding UTF-8 \
    -nowarn \
    @"$BUILD/sources.txt"

echo "[5/7] 打包 DEX (d8)..."
(cd "$BUILD/classes" && jar cf "$BUILD/classes.jar" .)
"$BT/d8.bat" --release --lib "$PLATFORM" --min-api 24 \
    --output "$BUILD/apk" "$BUILD/classes.jar"

echo "[6/7] 组装 APK..."
cp "$BUILD/apk/app.ap_" "$BUILD/apk/unsigned.apk"
python - "$BUILD/apk/unsigned.apk" "$BUILD/apk/classes.dex" << 'PYEOF'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex')
print("classes.dex added")
PYEOF

"$BT/zipalign.exe" -f 4 "$BUILD/apk/unsigned.apk" "$BUILD/apk/aligned.apk"

echo "[7/7] 生成密钥并签名..."
KS="$BUILD/release.keystore"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias nutstoreasp \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass nutstoreasp123 -keypass nutstoreasp123 \
        -dname "CN=Nutstore ASP Manager, OU=Personal, O=WorkBuddy, C=CN"
fi
"$BT/apksigner.bat" sign \
    --ks "$KS" --ks-pass pass:nutstoreasp123 --key-pass pass:nutstoreasp123 \
    --out "$OUT_APK" \
    "$BUILD/apk/aligned.apk"

echo ""
echo "构建完成: $OUT_APK"
ls -la "$OUT_APK"

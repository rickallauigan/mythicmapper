#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
cd "$(dirname "$0")/.."
cargo build --locked --offline --release --manifest-path engine/Cargo.toml
jdk_dir=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
work=$(mktemp -d /tmp/mythicmapper-jni-test.XXXXXX)
trap 'rm -rf "$work"' EXIT
cc -shared -fPIC -I"$jdk_dir/include" -I"$jdk_dir/include/linux" app/src/main/cpp/mythicmapper_jni.c engine/target/release/libmythicmapper_core.a -ldl -lpthread -lm -o "$work/libmythicmapper_core_jni.so"
mkdir -p "$work/xtr/keymapper/engine" "$work/xtr/keymapper/keymap/element" "$work/xtr/keymapper/mouse"
# Compile the actual original Java math/aim class with minimal Android-independent types.
cat > "$work/xtr/keymapper/keymap/element/AimKey.java" <<'JAVA'
package xtr.keymapper.keymap.element;
public class AimKey { public String code; public boolean stick,invertY; public float x,y,radius,deadZone,xSensitivity,ySensitivity; }
JAVA
cat > "$work/xtr/keymapper/mouse/HeldAimHandler.java" <<'JAVA'
package xtr.keymapper.mouse;
public class HeldAimHandler { public interface TouchSink { void inject(float x,float y,int action,int pointer); } }
JAVA
cp tools/RustMappingMathHost.java "$work/xtr/keymapper/engine/RustMappingMath.java"
javac -d "$work" "$work/xtr/keymapper/engine/RustMappingMath.java" "$work/xtr/keymapper/keymap/element/AimKey.java" "$work/xtr/keymapper/mouse/HeldAimHandler.java" app/src/main/java/xtr/keymapper/controller/ControllerAimHandler.java
java -Djava.library.path="$work" -cp "$work" xtr.keymapper.engine.RustMappingMath

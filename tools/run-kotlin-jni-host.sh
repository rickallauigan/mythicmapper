#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Requires :app:compileDebugKotlin (or assembleDebug) first; tests the actual compiled Kotlin object.
set -euo pipefail
cd "$(dirname "$0")/.."
cargo build --locked --offline --release --manifest-path engine/Cargo.toml
classes=app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes
test -f "$classes/xtr/keymapper/engine/RustMappingMath.class"
stdlib_root="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.4.0"
stdlib=$(rg --files "$stdlib_root" -g '*.jar' | head -n 1)
test -n "$stdlib"
jdk_dir=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
work=$(mktemp -d /tmp/mythicmapper-kotlin-jni.XXXXXX)
trap 'rm -rf "$work"' EXIT
cc -shared -fPIC -I"$jdk_dir/include" -I"$jdk_dir/include/linux" app/src/main/cpp/mythicmapper_jni.c engine/target/release/libmythicmapper_core.a -ldl -lpthread -lm -o "$work/libmythicmapper_core_jni.so"
cat > "$work/KotlinBridgeHost.java" <<'JAVA'
class KotlinBridgeHost {
    public static void main(String[] args) throws Exception {
        Class<?> type=Class.forName("xtr.keymapper.engine.RustMappingMath");
        Object instance=type.getField("INSTANCE").get(null);
        boolean available=(boolean)type.getMethod("getAvailable").invoke(instance);
        if(args.length>0) {
            if(available)throw new AssertionError("Missing library must disable bridge");
            System.out.println("Actual Kotlin missing-library handling PASS");return;
        }
        if(!available)throw new AssertionError("Actual Kotlin JNI library did not load");
        long packed=(long)type.getMethod("vectorPacked",float.class,float.class,float.class,float.class,float.class,boolean.class)
            .invoke(null,1f,0f,0f,1f,1f,false);
        if((float)type.getMethod("x",long.class).invoke(instance,packed)!=1f ||
           (float)type.getMethod("y",long.class).invoke(instance,packed)!=0f)throw new AssertionError("Packed Kotlin JNI result");
        System.out.println("Actual compiled Kotlin -> JNI -> Rust -> Kotlin PASS");
    }
}
JAVA
javac -d "$work" "$work/KotlinBridgeHost.java"
java -Djava.library.path="$work" -cp "$classes:$stdlib:$work" KotlinBridgeHost
java -Djava.library.path="$work/missing" -cp "$classes:$stdlib:$work" KotlinBridgeHost missing

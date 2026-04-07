#!/bin/bash
# Build proot binary for Android (arm64-v8a, armeabi-v7a, x86_64).
#
# Output: android-app/src/main/jniLibs/<abi>/libproot.so
#
# The binaries are ELF executables renamed as .so so the Android APK packager
# includes them under lib/<abi>/ and extracts them at install time.
#
# Requirements: Docker with internet access.
# Build time: ~5 min (downloads NDK + compiles proot from source).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTBASE="$REPO_ROOT/android-app/src/main/jniLibs"
PROOT_VERSION="5.4.0"
NDK_VERSION="r26d"

echo "==> Building proot $PROOT_VERSION for Android ABIs using NDK $NDK_VERSION"

# ── Docker image with NDK ──────────────────────────────────────────────────────
BUILDER_IMAGE="agentshell-proot-builder:latest"

docker build -t "$BUILDER_IMAGE" - <<'DOCKERFILE'
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    wget unzip git make cmake python3 \
    gcc g++ pkg-config \
    libarchive-dev libtalloc-dev \
    && rm -rf /var/lib/apt/lists/*

# Download Android NDK
ARG NDK_VERSION=r26d
RUN wget -q "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" -O /tmp/ndk.zip \
    && unzip -q /tmp/ndk.zip -d /opt \
    && rm /tmp/ndk.zip

ENV NDK_HOME=/opt/android-ndk-r26d

# Clone proot
ARG PROOT_VERSION=5.4.0
RUN git clone --depth 1 --branch "v${PROOT_VERSION}" \
    https://github.com/proot-me/proot.git /proot || \
    git clone --depth 1 https://github.com/proot-me/proot.git /proot

COPY build_proot_abi.sh /usr/local/bin/build_proot_abi.sh
RUN chmod +x /usr/local/bin/build_proot_abi.sh
DOCKERFILE

# ── ABI build script (injected into container) ────────────────────────────────
cat > /tmp/build_proot_abi.sh <<'ABISHELL'
#!/bin/bash
set -euo pipefail

ABI="$1"
NDK_HOME="${NDK_HOME:-/opt/android-ndk-r26d}"
HOST_TAG="linux-x86_64"
MIN_API=26

case "$ABI" in
  arm64-v8a)
    TARGET="aarch64-linux-android"
    CLANG_PREFIX="${TARGET}${MIN_API}"
    ;;
  armeabi-v7a)
    TARGET="armv7a-linux-androideabi"
    CLANG_PREFIX="${TARGET}${MIN_API}"
    ;;
  x86_64)
    TARGET="x86_64-linux-android"
    CLANG_PREFIX="${TARGET}${MIN_API}"
    ;;
  *)
    echo "Unknown ABI: $ABI"; exit 1 ;;
esac

TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
export CC="$TOOLCHAIN/bin/${CLANG_PREFIX}-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"

cd /proot/src
make clean 2>/dev/null || true

# Build as statically-linked executable (no libc.so dependency on device)
make -j"$(nproc)" \
    CC="$CC" \
    AR="$AR" \
    RANLIB="$RANLIB" \
    CFLAGS="-Os -fPIE -DPROOT_NO_SECCOMP" \
    LDFLAGS="-static -pie -fPIE" \
    proot

mkdir -p "/output/$ABI"
cp proot "/output/$ABI/libproot.so"
"$STRIP" --strip-all "/output/$ABI/libproot.so"
echo "[OK] $ABI → /output/$ABI/libproot.so ($(du -sh /output/$ABI/libproot.so | cut -f1))"
ABISHELL

# ── Run builds ────────────────────────────────────────────────────────────────
for ABI in arm64-v8a armeabi-v7a x86_64; do
    echo ""
    echo "==> Building ABI: $ABI"
    mkdir -p "$OUTBASE/$ABI"
    docker run --rm \
        -v "$OUTBASE:/output" \
        -v /tmp/build_proot_abi.sh:/usr/local/bin/build_proot_abi.sh \
        "$BUILDER_IMAGE" \
        bash /usr/local/bin/build_proot_abi.sh "$ABI"
done

echo ""
echo "==> Done. Artifacts:"
find "$OUTBASE" -name "libproot.so" -exec ls -lh {} \;
echo ""
echo "Add the following to android-app/build.gradle.kts if not present:"
echo "  sourceSets[\"main\"].jniLibs.srcDirs(\"src/main/jniLibs\")"

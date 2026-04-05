#!/bin/bash
# Build Alpine rootfs for Android Proot sandbox
# Run this on a Linux host with Docker installed.
# Output: android-app/src/main/assets/rootfs.tar.gz
set -e
OUTDIR="$(dirname "$0")/../android-app/src/main/assets"
mkdir -p "$OUTDIR"
docker run --rm -v "$OUTDIR:/output" alpine:3.19 sh -c \
  'apk add --no-cache busybox git curl jq openssh-client && \
   tar czf /output/rootfs.tar.gz -C / --exclude=./proc --exclude=./sys --exclude=./dev .'
echo "rootfs.tar.gz written to $OUTDIR"

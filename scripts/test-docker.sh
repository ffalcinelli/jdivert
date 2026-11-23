#!/bin/bash
# SPDX-License-Identifier: LGPL-3.0-or-later OR GPL-2.0-or-later
# Run JDivert's Linux build and live capture suite inside a privileged Docker container.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE_NAME="${JDIVERT_TEST_IMAGE:-jdivert-test}"
# Maven's repository lives in a named volume: the container runs as root and must not write into ~/.m2.
M2_VOLUME="${JDIVERT_M2_VOLUME:-jdivert-m2}"

docker build -t "$IMAGE_NAME" -f "$REPO_DIR/Dockerfile.test" "$REPO_DIR"

ENV_ARGS=()
if [ "${JDK_VERSION:-25}" = "8" ]; then
    ENV_ARGS+=(-e "JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64")
fi

exec docker run --rm --privileged --pid=host \
    -v "$REPO_DIR:/src:ro" \
    -v "$M2_VOLUME:/root/.m2" \
    "${ENV_ARGS[@]}" \
    "$IMAGE_NAME" \
    bash -ceu '
        mountpoint -q /sys/fs/bpf || mount -t bpf bpf /sys/fs/bpf
        mountpoint -q /sys/fs/cgroup || mount -t cgroup2 none /sys/fs/cgroup
        mkdir -p /tmp/work
        tar -C /src --exclude=.git --exclude=target -cf - . | tar -C /tmp/work -xf -
        cd /tmp/work
        mvn -B clean verify "$@"
    ' -- "$@"

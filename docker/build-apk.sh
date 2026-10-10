#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
builder_image=instaloaderapp-android-builder:local
gradle_cache_volume=instaloaderapp-gradle-cache
android_home_volume=instaloaderapp-android-home

if [ "$#" -eq 0 ]; then
    set -- assembleRelease
fi

case " $* " in
    *assembleRelease*)
        if [ ! -f "${project_root}/keystore.properties" ]; then
            echo "Release signing is not configured: create keystore.properties first." >&2
            exit 1
        fi
        ;;
esac

docker build \
    --platform linux/amd64 \
    --tag "$builder_image" \
    --file "${script_dir}/android-builder/Dockerfile" \
    "$project_root"

docker volume create "$gradle_cache_volume" >/dev/null
docker volume create "$android_home_volume" >/dev/null

docker run --rm \
    --platform linux/amd64 \
    --user "$(id -u):$(id -g)" \
    --env HOME=/tmp \
    --env JAVA_TOOL_OPTIONS=-Duser.home=/tmp \
    --env GRADLE_USER_HOME=/tmp/gradle-home \
    --volume "${gradle_cache_volume}:/tmp/gradle-home" \
    --volume "${android_home_volume}:/tmp/.android" \
    --volume "${project_root}:/workspace" \
    --workdir /workspace \
    "$builder_image" \
    ./gradlew --no-watch-fs "$@"

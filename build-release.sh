#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

sh "${script_dir}/docker/build-apk.sh" assembleRelease "$@"

apk_path="${script_dir}/app/build/outputs/apk/release/app-release.apk"
printf '\nRelease APK: %s\n' "$apk_path"

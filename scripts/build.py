#!/usr/bin/env python3
"""
构建项目

用法:
  python scripts/build.py          # 构建全部
  python scripts/build.py cas_lib  # 仅构建 cas_lib
  python scripts/build.py cas_cli  # 仅构建 cas_cli
  python scripts/build.py cas_android_lib  # 仅构建 cas_android_lib
  python scripts/build.py clean    # 清理构建产物
"""

import subprocess
import sys


def run(cmd: str):
    print(f"> {cmd}")
    result = subprocess.run(cmd, shell=True)
    if result.returncode != 0:
        sys.exit(result.returncode)


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else ""

    if target == "clean":
        run("./gradlew clean")
    elif target == "cas_lib":
        run("./gradlew :cas_lib:build")
    elif target == "cas_cli":
        run("./gradlew :cas_cli:build")
    elif target == "cas_android_lib":
        run("./gradlew :cas_android_lib:build")
    else:
        run("./gradlew build")


if __name__ == "__main__":
    main()

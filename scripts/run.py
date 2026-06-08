#!/usr/bin/env python3
"""
运行 CLI

用法:
  python scripts/run.py bill -u <学号> -p <密码>
  python scripts/run.py hot-water -u <学号> -p <密码> -c manual
  python scripts/run.py person-account -u <学号> -p <密码>
  python scripts/run.py parse-person-account -i <personaccount.html>
  python scripts/run.py captcha-test
  python scripts/run.py help
"""

import subprocess
import sys


def main():
    # 传递所有命令行参数给 Gradle run 任务
    args = sys.argv[1:]
    cmd = ["./gradlew", ":cas_cli:run", f"--args={' '.join(args)}" if args else ""]
    cmd = [c for c in cmd if c]  # 去掉空字符串
    result = subprocess.run(cmd)
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()

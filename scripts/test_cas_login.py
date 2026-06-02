#!/usr/bin/env python3
"""
CAS 登录流程测试（纯 Python，不依赖 Kotlin）

快速验证 CAS 登录三阶段流程是否正常，无需启动 JVM。

用法:
  SHMTU_USER_ID=x SHMTU_PASSWORD=y python scripts/test_cas_login.py
  SHMTU_USER_ID=x SHMTU_PASSWORD=y python scripts/test_cas_login.py --manual
"""

import os
import re
import sys
import time
import urllib.request
import urllib.error


def test_cas_login(username: str, password: str, manual: bool = False):
    print(f"=== CAS Login Test (manual={manual}) ===")
    print(f"User: {username}")

    # Phase 0: 探测
    print("\n[Phase 0] Probing login status...")

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(),
        NoRedirect(),
    )

    try:
        req = urllib.request.Request("https://ecard.shmtu.edu.cn/epay/consume/query?pageNo=1&tabNo=1")
        opener.open(req, timeout=10)
        print("  Already logged in!")
        return True
    except urllib.error.HTTPError as e:
        if e.code != 302:
            print(f"  FAIL: Unexpected status {e.code}")
            return False
        login_url = e.headers.get("Location", "")
        print(f"  Need login, redirect URL: {login_url[:80]}...")

    # Phase 1: 获取 challenge
    print("\n[Phase 1] Preparing challenge...")
    try:
        req = urllib.request.Request(login_url)
        resp = opener.open(req, timeout=10)
        html = resp.read().decode("utf-8")
        match = re.search(r'name="execution"\s+value="([^"]+)"', html)
        if not match:
            print("  FAIL: Cannot extract execution token")
            return False
        execution = match.group(1)
        print(f"  Execution: {execution[:40]}...")

        req = urllib.request.Request("https://cas.shmtu.edu.cn/cas/captcha")
        resp = opener.open(req, timeout=10)
        captcha_data = resp.read()
        print(f"  Captcha image: {len(captcha_data)} bytes")
    except Exception as e:
        print(f"  FAIL: {e}")
        return False

    if manual:
        filename = f"/tmp/captcha_{int(time.time())}.png"
        with open(filename, "wb") as f:
            f.write(captcha_data)
        print(f"  Saved to: {filename}")
        validate_code = input("  Enter captcha answer: ").strip()
    else:
        print("  (Use --manual to enter captcha, or use Kotlin CLI with OCR)")
        return False

    # Phase 2: 提交
    print(f"\n[Phase 2] Submitting login (code={validate_code})...")
    print("  (Full POST not implemented in this test script, use Kotlin CLI for complete flow)")


def main():
    username = os.environ.get("SHMTU_USER_ID", "")
    password = os.environ.get("SHMTU_PASSWORD", "")
    manual = "--manual" in sys.argv

    if not username or not password:
        print("Usage: SHMTU_USER_ID=x SHMTU_PASSWORD=y python scripts/test_cas_login.py [--manual]")
        sys.exit(1)

    test_cas_login(username, password, manual)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
测试验证码 OCR 连通性

用法:
  python scripts/test_ocr.py                          # 默认 127.0.0.1:21601
  python scripts/test_ocr.py 192.168.1.100 21601      # 自定义地址
  python scripts/test_ocr.py --http http://192.168.1.100:21600
"""

import base64
import json
import os
import socket
import sys
import urllib.request


def test_tcp(host: str, port: int):
    print(f"=== TCP OCR Test: {host}:{port} ===")

    print("Downloading captcha image...")
    try:
        req = urllib.request.Request("https://cas.shmtu.edu.cn/cas/captcha")
        with urllib.request.urlopen(req, timeout=10) as resp:
            image_data = resp.read()
        print(f"  Image size: {len(image_data)} bytes")
    except Exception as e:
        print(f"  FAIL: Cannot download captcha: {e}")
        return False

    print("Sending to OCR server...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        sock.connect((host, port))
        sock.sendall(image_data + b"<END>")
        result = sock.recv(4096).decode("utf-8")
        sock.close()
        print(f"  OCR result: {result}")
        if "=" in result:
            print(f"  Answer: {result.split('=')[-1].strip()}")
        return True
    except Exception as e:
        print(f"  FAIL: Cannot connect to OCR server: {e}")
        return False


def test_http(base_url: str):
    print(f"=== HTTP OCR Test: {base_url} ===")

    print("Health check...")
    try:
        req = urllib.request.Request(f"{base_url}/api/health")
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"  Status: {resp.status}")
    except Exception as e:
        print(f"  FAIL: Health check failed: {e}")
        return False

    print("Downloading captcha image...")
    try:
        img_req = urllib.request.Request("https://cas.shmtu.edu.cn/cas/captcha")
        with urllib.request.urlopen(img_req, timeout=10) as resp:
            image_data = resp.read()
        print(f"  Image size: {len(image_data)} bytes")
    except Exception as e:
        print(f"  FAIL: Cannot download captcha: {e}")
        return False

    print("Sending to OCR server...")
    try:
        b64 = base64.b64encode(image_data).decode("utf-8")
        payload = json.dumps({"imageBase64": b64}).encode("utf-8")
        req = urllib.request.Request(
            f"{base_url}/api/ocr",
            data=payload,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.loads(resp.read().decode("utf-8"))
        print(f"  OCR result: {result}")
        if result.get("success"):
            print(f"  Expression: {result.get('expression')}")
            print(f"  Result: {result.get('result')}")
        return True
    except Exception as e:
        print(f"  FAIL: OCR request failed: {e}")
        return False


def main():
    args = sys.argv[1:]

    if args and args[0] == "--http":
        base_url = args[1] if len(args) > 1 else "http://127.0.0.1:21600"
        test_http(base_url)
    else:
        host = args[0] if len(args) > 0 else os.environ.get("SHMTU_OCR_HOST", "127.0.0.1")
        port = int(args[1]) if len(args) > 1 else int(os.environ.get("SHMTU_OCR_PORT", "21601"))
        test_tcp(host, port)


if __name__ == "__main__":
    main()

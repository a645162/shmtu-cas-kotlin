---
layout: home

hero:
  name: "SHMTU CAS Kotlin"
  text: "上海海事大学统一认证平台 Kotlin 实现"
  tagline: 基于 OkHttp + Jsoup 的 CAS 认证、Epay 账单查询、微信平台热水查询
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/get-started
    - theme: alt
      text: API 参考
      link: /api/cas-auth

features:
  - title: CAS 统一认证
    details: 完整实现上海海事大学 CAS 统一认证平台登录流程，支持验证码自动识别、execution 提取、重定向处理。
  - title: Epay 账单查询
    details: 通过 CAS 认证后自动跳转 Epay 一卡通平台，支持分页查询消费账单记录，解析日期、金额、类型等信息。
  - title: 微信平台认证
    details: 支持微信平台（wengine_new_ticket）认证流程，实现后勤服务平台热水信息查询。
  - title: 验证码 OCR
    details: 内置验证码图片下载与远程 OCR 识别功能，通过 TCP 协议与推理服务器通信，支持多线程并发识别。
---

## 项目简介

SHMTU CAS Kotlin 是上海海事大学统一认证平台的 Kotlin 实现，使用 OkHttp 进行 HTTP 请求，Jsoup 解析 HTML 页面，支持 CAS 登录、验证码识别、Epay 账单查询和微信平台热水查询等功能。

本项目为课程设计项目，仅用作学习用途。

## 相关项目

- [shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server) - 验证码 OCR 推理服务器（C++ Drogon + ncnn）
- [shmtu-cas-ocr-model](https://github.com/a645162/shmtu-cas-ocr-model) - 验证码识别模型训练（PyTorch + ResNet）
- [shmtu-cas-go](https://github.com/a645162/shmtu-cas-go) - Go 语言版本实现

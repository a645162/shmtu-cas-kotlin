import { defineConfig } from 'vitepress'

function resolveBase() {
  const repo = process.env.GITHUB_REPOSITORY?.split('/')[1]
  if (!process.env.GITHUB_ACTIONS || !repo) {
    return '/'
  }
  return repo.endsWith('.github.io') ? '/' : `/${repo}/`
}

export default defineConfig({
  base: resolveBase(),
  lang: 'zh-CN',
  title: 'shmtu-cas-kotlin 开发者文档',
  description: '上海海事大学 CAS 统一认证、Epay 账单、微信平台热水查询的 Kotlin 多端库',
  cleanUrls: true,
  lastUpdated: true,
  themeConfig: {
    nav: [
      { text: '快速开始', link: '/guide/get-started' },
      { text: '整体架构', link: '/guide/architecture' },
      { text: 'API 总览', link: '/api/overview' },
      { text: 'Android 集成', link: '/platforms/android' },
      { text: 'FAQ', link: '/faq/' },
    ],
    sidebar: [
      {
        text: '概览',
        items: [
          { text: '文档首页', link: '/' },
          { text: '快速开始', link: '/guide/get-started' },
          { text: '整体架构', link: '/guide/architecture' },
          { text: '模块与子项目', link: '/guide/modules' },
        ],
      },
      {
        text: '认证流程',
        items: [
          { text: 'CAS 认证原理', link: '/auth/cas-flow' },
          { text: '验证码抽象', link: '/auth/captcha' },
          { text: 'Epay 一卡通', link: '/auth/epay' },
          { text: '微信平台/热水', link: '/auth/wechat' },
        ],
      },
      {
        text: 'API 参考',
        items: [
          { text: 'API 总览', link: '/api/overview' },
          { text: 'CasAuth (底层)', link: '/api/cas-auth' },
          { text: 'EpayAuth', link: '/api/epay-auth' },
          { text: 'WechatAuth', link: '/api/wechat-auth' },
          { text: 'Captcha (底层)', link: '/api/captcha' },
          { text: 'CaptchaResolver', link: '/api/captcha-resolver' },
          { text: 'BillParser', link: '/api/bill-parser' },
          { text: 'HotWaterParser', link: '/api/hotwater-parser' },
          { text: 'BillSync', link: '/api/bill-sync' },
          { text: 'BillClassifier / PositionTranslator', link: '/api/classifier' },
          { text: '数据类型与枚举', link: '/api/datatype' },
        ],
      },
      {
        text: '平台集成',
        items: [
          { text: 'Android 集成指南', link: '/platforms/android' },
          { text: 'JVM / 桌面端', link: '/platforms/jvm' },
          { text: 'CLI 工具', link: '/platforms/cli' },
        ],
      },
      {
        text: '进阶主题',
        items: [
          { text: 'Cookie 与会话持久化', link: '/advanced/session' },
          { text: '错误码与重试', link: '/advanced/errors' },
        ],
      },
      {
        text: 'FAQ',
        items: [
          { text: '常见问题', link: '/faq/' },
        ],
      },
    ],
    outline: [2, 3],
    search: {
      provider: 'local',
    },
    footer: {
      message: 'shmtu-cas-kotlin Developer Docs',
      copyright: 'Copyright © shmtu-cas-kotlin',
    },
  },
})

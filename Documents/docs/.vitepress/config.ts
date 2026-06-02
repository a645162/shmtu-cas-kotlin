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
  title: 'SHMTU CAS Kotlin 文档',
  description: '上海海事大学统一认证平台 Kotlin 实现文档',
  cleanUrls: true,
  lastUpdated: true,
  themeConfig: {
    nav: [
      { text: '快速开始', link: '/guide/get-started' },
      { text: 'CAS认证流程', link: '/auth/cas-flow' },
      { text: 'API参考', link: '/api/cas-auth' },
      { text: 'FAQ', link: '/faq/' },
    ],
    sidebar: [
      {
        text: '快速开始',
        items: [
          { text: '文档首页', link: '/' },
          { text: '快速开始', link: '/guide/get-started' },
        ],
      },
      {
        text: 'CAS认证流程',
        items: [
          { text: '认证流程概览', link: '/auth/cas-flow' },
          { text: '验证码识别', link: '/auth/captcha' },
        ],
      },
      {
        text: 'Epay认证',
        items: [
          { text: 'Epay认证与账单查询', link: '/auth/epay' },
        ],
      },
      {
        text: '微信平台认证',
        items: [
          { text: '微信平台认证与热水查询', link: '/auth/wechat' },
        ],
      },
      {
        text: 'API参考',
        items: [
          { text: 'CasAuth', link: '/api/cas-auth' },
          { text: 'Captcha', link: '/api/captcha' },
          { text: 'EpayAuth', link: '/api/epay-auth' },
          { text: 'WechatAuth', link: '/api/wechat-auth' },
          { text: 'BillParser', link: '/api/bill-parser' },
          { text: 'HotWaterParser', link: '/api/hotwater-parser' },
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
      message: 'SHMTU CAS Kotlin Docs',
      copyright: 'Copyright © SHMTU CAS Kotlin',
    },
  },
})

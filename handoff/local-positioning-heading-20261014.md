# 学员定位卡确认页隐藏课程服务名称

- Environment: local; workstream: local-positioning-heading-20261014; owner: Codex.
- Goal: 移除学员确认页顶部关联课程服务名称，保留导航、编号与提交时间。
- Non-goals: 不修改接口、课程服务数据、定位正文或确认流程。
- Branch: codex/order-void; worktree: D:\ZSJ-OS; base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e.
- Ownership: frontend/h5/src/pages/positioning/confirmation.vue; docs/api/positioning-service-application.md; 本记录。
- Dependencies: None; target branch/integration order: None.
- Verification: scoped diff、Vue 类型检查、现有 H5 fixture 桌面及移动浏览器核验；使用独立临时输出避免影响已有 tsbuildinfo。

## Delivery
- 移除确认页 serviceLabel 主标题与失用 h1 样式；同步确认页展示契约。
- PASS: vue-tsc --noEmit --incremental false；scoped git diff --check。
- PASS: 现有 positioning-independent fixture 于 Chromium 1440/390px 核验，接口仍含服务名称时页面不展示该名称，导航、编号和操作保留，无横向溢出；截图存于本会话 visualization 目录。
- 未部署；浏览器核验使用本地 fixture，未调用真实学员确认接口。原有 tsconfig.tsbuildinfo 修改保留。Ownership released.

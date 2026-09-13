# Workstream Registration

- ID: main-h5-version-update
- Goal: 实现兼职 H5 独立版本页、构建版本检测、主动刷新、入口提醒与静态更新记录。
- Non-goals: 后端、数据库、其他前端、强制更新、跨设备通知确认、新增依赖。
- Branch / target branch: main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Owner: Codex /root
- Ownership scope: frontend/h5/vite.config.ts; frontend/h5/env.d.ts; frontend/h5/src/pages/profile/index.vue; frontend/h5/src/router/index.ts; frontend/h5/src/pages/profile/version-update.vue; frontend/h5/src/services/version.ts; frontend/h5/src/utils/version.ts; frontend/h5/releases.json; frontend/h5/tests/version.test.mjs; frontend/h5/docs/version-update.md; output/playwright/h5-version-update/; 本记录。
- Dependencies: 现有 Vue、Vant、Vite、Node 测试能力；保留已有未提交修改。仅本工作流修改上述范围。
- Integration order: 构建清单与逻辑 → 页面与入口 → 定向测试、构建、浏览器 → 交付记录。
- Verification plan: Node 定向测试；vue-tsc 与生产构建；清单和构建一致性；桌面与移动宽度浏览器验证更新、失败重试、关闭提示、刷新路径保留。

## Delivery Entry - 2026-09-10 14:22:11 +08:00

- Branch: main
- Worktree: D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 仅实现 H5 版本页面、检查更新、主动刷新、入口提醒及静态历史记录。
- Key decisions: 同一构建对象注入 JS 并生成 version.json；构建不同即提示（含回滚）；不强制更新；通知关闭仅浏览器持久化；保留登录及完整原 URL 参数，不清空同源缓存；已授权范围内使用现有登录保护，无后端/数据库变更。
- Execution result: 独立 /profile/version-update 页面、真实构建标识、检查并发合并与超时/失败重试、关闭提示、历史展开、确认刷新和刷新未更新提示完成。个人中心错误主题链接修复。
- Changed files: frontend/h5/vite.config.ts; frontend/h5/env.d.ts; frontend/h5/src/pages/profile/index.vue; frontend/h5/src/router/index.ts; frontend/h5/src/pages/profile/version-update.vue; frontend/h5/src/services/version.ts; frontend/h5/src/utils/version.ts; frontend/h5/releases.json; frontend/h5/tests/version.test.mjs; frontend/h5/docs/version-update.md; 本记录；output/playwright/h5-version-update/ 测试脚本和截图。
- Verification evidence: node --test tests/version.test.mjs 3/3 通过；npm run build（vue-tsc -b + Vite）通过；scoped git diff --check 通过。Playwright 独立浏览器使用测试身份和隔离 API 响应，验证入口跳转及有更新标记、最新版本、不同构建、历史/详情展开、关闭后刷新不重复提示、取消刷新、503 失败与重试、空历史、原路径/重复参数/hash 保留、旧构建警告。使用实际第二次生产构建完成从旧构建刷新到新构建，确认已是最新版本且旧构建警告消失。360/390/430/1280 宽度无横向溢出，最终截图 final-390.png、final-1280.png。浏览器仅见原有 favicon 404 与主动模拟的 503，无版本页运行异常。
- Dependency or integration impact: 无新增依赖、后端、SQL、其他前端改动；保留原有未提交修改；无提交、推送、分支操作。构建产物需作为整体部署，发布前配置入口/清单缓存策略。
- Remaining work: 真实部署环境/CDN 缓存与企业微信内核未验证；本次浏览器测试为本地生产产物，身份接口采用测试响应。线上部署与缓存配置不在本次范围。None for implementation.

# 公告编辑与阅读排版

## 2026-11-09 方向修正登记

- 用户明确要求恢复宽编辑区，通过扩大桌面阅读与分享实现同宽。
- 沿用本记录的 local/main、工作树、owner、文件所有权及非目标。重新激活本工作流。
- 桌面基准改为原 Workbench 1400px 编辑弹窗扣除左右各 24px 后的 1352px。同步扩大 Admin 编辑容器与两端阅读/预览、服务端和 H5 公开页。窄视口按可用空间适配。
- 验证计划沿用定向测试与浏览器夹具，重点验证宽版桌面以及手机三图/表格。

### 宽版交付结果（取代下文首轮 880px 方案）

- 恢复原 Workbench 1400px 编辑弹窗的完整正文可用宽度，正文上限 1352px。预览弹窗扩大到 1400px，公开页外框 1384px，正文上限 1352px。Admin 编辑外框同步扩至 1400px，阅读及预览同步扩大。
- 同步更新图片像素尺寸换算、表格编辑适配基准、公开页测试与文档。清理首轮在 Admin 手机媒体查询中重复插入的编辑宽度规则。
- 为避免长内容触发纵向滚动条后减少正文宽度，公告编辑器使用 `scrollbar-width:none`，滚动区域及滚轮/键盘滚动机制保留。
- 本轮 System 定向 10 项测试通过，Workbench 类型检查通过，Workbench 夹具生产构建与 H5 生产构建通过。
- 服务端无 JS 阅读与 H5 在 390/768/1440/1920px 下四种表格样例共 32 组浏览器验证及打开后缩放验证通过。
- Ego 浏览器测得 1440px 视口下编辑、阅读、表格均为 1352px；390px 视口下编辑/阅读均约 358px。两档三图均同行。
- 宽版截图为本次可视化目录下 `notice-wide-1440.png` 和 `notice-wide-390.png`。
- 本轮未执行真实账号/原公告/微信环境验收及 Admin 完整表单浏览器验收，沿用上轮对 Admin 全量类型检查既有问题的说明。本地代码交付，部署验收待执行。
- 本轮交付后释放文件所有权。

- Workstream ID: local-notice-layout-20261109
- Environment: local（环境变量和环境文件均未配置）
- Goal: 统一公告正文版心，保留分享图片尺寸，消除阅读阶段人为扩大表格的最小宽度，提供手机预览。
- Non-goals: 数据库修改、历史正文回写、权限变更、服务重启、部署。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base: 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c
- Owner: Codex
- Ownership: System notice 分享内容/表格/页面及定向测试，Workbench 公告编辑/预览/SafeRichText/公告样式与定向夹具，Admin system/notice 组件及 test/notice-layout 夹具，H5 notice/share.vue 及 notice-share-tables-browser.py，docs/api/notice-public-share.md、docs/frontend/notice-tables.md，本记录。
- Dependencies: 既有 wangEditor、React/Ant Design、Vue/Element Plus、Jsoup。无新增依赖。
- Target branch / integration order: None
- Verification: System 定向测试，前端静态检查，桌面与手机浏览器夹具验证图片并排、表格适配和编辑预览宽度。
- Contract decision: 用户确认以一致版心及自适应表格替代阅读阶段强制像素最小宽度的旧展示策略。保存的正文保持原始数据，阅读适配仅作用于投影。

## 交付

- 桌面正文以现有 880px 为基准，公开外框 912px（左右各 16px）。Workbench/Admin 编辑预览增加桌面和手机切换，手机参考正文 358px。
- 公开清理保留白名单内图片尺寸和正文格式，同段多图的已知像素尺寸按版心转换为比例；移除公告编辑器图片容器额外边距，避免手机端三图被挤换行。
- 阅读表格按原列宽比例适配正文，取消人为生成的最小像素宽度，保留合并、对齐、颜色和行高。Workbench 载入/粘贴时将相对列宽按当前编辑器宽度转换为整数像素，过宽列组按比例缩小。
- System reactor 定向测试成功，NoticeShareContentTest 5 项、NoticeSharePageControllerTest 5 项通过，包含安全样式、重复清理幂等及像素/百分比多图。
- Workbench 类型检查通过，公告相关 26 项测试通过。额外样式守卫共 30 项，26 项通过、4 项失败，涉及已有 class-statistics-preview/layout/calendar/bpm 样式及缺失 token，失败清单没有本次公告文件。
- H5 `vue-tsc --noEmit --incremental false` 通过，H5 专用输出目录生产构建通过。Workbench 真实组件夹具生产构建通过，包含上游体积提示；这项是定向夹具构建。
- Admin 全量类型检查默认堆内存不足，提高至 8GB 后报现有两处 TS6133，分别为 mes/pro/task/edit/index.vue 的 handleRefresh、zsjos/leadQualification/index.vue 的 Refresh。公告文件未报告类型错误。
- 浏览器使用合成数据。Workbench 桌面编辑/阅读均为 880px，390px 手机视口下均约 358px，三图在编辑/阅读中同行，表格无横向溢出，重开后保留。实际公告表单的桌面/手机预览分别测得 880/358px；内存保存及重新打开通过。
- Admin 阅读组件桌面 880px，手机随可用容器适配，三图同行、表格完整。Admin 完整编辑表单的浏览器验收仍待执行。
- 既有 `notice-share-tables-browser.py` 已按新契约更新并执行通过。实际服务端无 JS HTML 与生产 H5 资源分别在 390/768/1440/1920px 下验证四种正文样例，共 32 组并覆盖打开后缩放。
- 截图位于 `C:\Users\EDY\.codex\visualizations\2026\11\09\01a1bdf4-9dbb-7f52-ba40-39d928e0da34\notice-desktop.png`、`notice-mobile.png`。
- 真实账号保存、用户原公告、线上分享和微信内置浏览器尚待部署环境验收。当前交付为本地代码与夹具验证结果。
- 浏览器工具提示 Ego Lite 有可用更新，本次使用当前版本验证。
- Ownership released on delivery.

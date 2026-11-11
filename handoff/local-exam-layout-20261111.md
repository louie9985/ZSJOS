# 考试资料收集页面布局重构

## 登记 2026-11-11

- 工作流 local-exam-layout-20261111，owner Claude，环境 local（ZSJOS_AGENT_ENV 与 /etc/zsjos/agent-environment 均缺省）。
- 分支 main，工作树 D:\ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。目标分支与集成顺序 None。
- 目标：用户要求完整重构 /zsjos/exam-material-collection 主从界面布局（列表工具栏归入左栏、详情头部单卡、分享区单行+二维码弹出、结束说明内联、名单页签与表格对齐）。
- 非目标：接口、权限、数据库、共享 business-inbox / BusinessTable 组件、表格模式与汇总页。
- 所有权：frontend/workbench/src/pages/ExamCollectionPage.tsx、src/components/examCollection/ExamCollectionShare.tsx、src/styles/pages/exam-collection.css、frontend/workbench/docs/ui-guidelines.md（考试资料收集段落）、本记录。保留各文件既有未提交改动。
- 依赖：既有 StudentInfoQrCard compact、ResourceLink、business-inbox 骨架。
- 验证：npm run typecheck、真实浏览器桌面与 390px 移动宽度检查、scoped diff 检查。

## 登记更新

- 所有权新增隔离浏览器夹具 frontend/workbench/test/exam-collection-layout.html 与 exam-collection-layout.tsx（仅合成数据，拦截全部真实请求）。原因：共享浏览器当前登录账号已无该菜单，不切换账号。

## 交付 2026-11-11 17:56 北京时间

- 分支 main，工作树 D:\ZSJ-OS，HEAD 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c（未变化）。
- 结果：主从模式搜索与“发起收集”移入左栏顶部，状态改为全宽分段筛选；列表项改用 business-inbox 标题/元信息结构并加进度条。详情改为标题卡（统计与结束说明内联、操作右侧居中、移除桌面“关闭详情”）、单行链接卡（主机路径展示、复制完整链接、二维码弹出层）、页签卡（名单表格与页签左对齐）。表格模式、接口、权限不变。
- 决策冲突：ui-guidelines 2026-11-06 记录了“二维码直接展示”“可见完整 URL”的既往决定；按用户本次明确的整体重构要求执行，已在文档追加 2026-11-11 条目说明取代范围，并在答复中提示用户确认。ExamCollectionShare 同时被班主任待办/跟进窗口复用，一并生效。
- 变更文件：ExamCollectionPage.tsx、ExamCollectionShare.tsx、exam-collection.css、docs/ui-guidelines.md、test/exam-collection-layout.*、本记录。保留各文件既有未提交改动。
- 验证：npm run typecheck 通过；scoped git diff --check 通过。真实浏览器（ego）隔离夹具 1371px 桌面检查已完成/收集中任务、二维码弹出层、表头与页签左缘对齐（374/374）；390px 移动检查列表与详情抽屉无横向溢出（scrollWidth 390/378）；班主任待办夹具确认单行链接卡显示。vitest（exam/styles.guard/inbox 过滤）176 通过、12 失败，失败均位于销售订单、审批中心、日历等其他文件及全局样式守卫，与本次改动无关，未修复。
- 未验证：真实账号真实数据下的页面（共享浏览器当前账号无此菜单）；表格模式未改动未复测。
- 依赖与集成影响 None。所有权释放。

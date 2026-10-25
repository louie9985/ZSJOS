# 诊断截止时间与填写入口响应修复

## Registration — 2026-10-22（北京时间）
- ID: local-diagnosis-loading-20261022; environment: local（ZSJOS_AGENT_ENV 与环境文件均未指定，按规则回退）。
- Goal: 修复账号诊断截止时间查询放大、点击填写无反馈和未开放节点入口状态不一致。
- Non-goals: 周节点开放业务规则、历史数据、统计口径、权限配置、数据库结构/数据、部署/重启、提交/推送。
- Branch: main; absolute worktree: D:\ZSJ-OS; base commit: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Owner: Codex root; target branch/integration order: None.
- Ownership: 后端贡献者独占 MediaAccountDiagnosisReminderService、WeeklyDiagnosisService、MediaAccountDiagnosisController、WeeklyDiagnosisVO、BusinessTaskMapper 与相关诊断/任务专项测试；前端贡献者独占 AccountProfilePanel.tsx 与 services/mediaAccountProfile.ts；浏览器验证贡献者独占 test/weekly-account-diagnosis.tsx、test/weekly-account-diagnosis.py 及新增 diagnosis-loading 专项浏览器脚本；root 独占本记录、docs/api/weekly-account-diagnosis.md、docs/api/media-account-profile.md。保留这些文件现有未提交改动。
- Settled contract: 新增业务 GET /zsjos/media-account/diagnosis/overview?accountId，返回 {tasks: DiagnosisTodo[], nodes: Node[]}；任务包含原定截止时间，nodes 仅包含已开放节点，parts.metric 不计算（可空），对象/编导与功能权限沿用现有边界。既有 tasks/nodes/preview 接口保持兼容；打开表单复用当前账号 overview 的已加载/进行中请求，仅 preview 加载统计。
- Dependencies: 既有每周综合诊断未提交实现（handoff/local-weekly-diagnosis-20261022.md 已交付并释放所有权）；当前多项本地变更保留。无新依赖，沿用 React/Ant Design、现有服务/权限和任务设施。
- Verification: 后端 scoped query/授权/任务过滤与轻量 overview 专项测试及编译；Workbench typecheck 与适用专项测试；现有 Chrome 实际组件合成传输验证慢请求、重复点击、失败重试、未开放节点、切换账号与关闭时迟到响应、桌面/手机及既有诊断提交流程。各贡献者串行使用自己的构建/浏览器资源，不修改运行服务。

## Ownership update — fixture compatibility
- 浏览器验证贡献者范围增加 frontend/workbench/test/account-positioning-summary.tsx 与 account-review-delivery.tsx：只补充新 overview 的合成返回以维持既有 fixture 兼容，不改业务组件或扩大诊断规则。旧独立浏览器脚本若需调整，先报告直接受影响断言与范围。

## Delivery — 2026-10-22（北京时间）
- Result: 账号任务改为 SQL 按业务类型/账号/受派范围/待办状态过滤，同账号复用资格校验；overview 一次返回截止与开放节点且不计算统计。原 tasks/nodes/preview 保持兼容。点击立即开窗显示加载；复用当前账号已加载/进行中 overview，仅加载所选 preview，防重复点击；失败原位重试、未来节点明确禁用；关闭/切换账号和资料重载使旧响应失效。
- Preserved: 原有责任编导、对象权限、定位轮次、重新定位、受派规则、周期开放及统计口径。无新增依赖、DB/schema/角色授权、服务重启、分支/提交/推送操作。保留此前所有未提交功能。
- Backend verification: Maven 编译及 48 个专项测试通过，0 failures/errors/skips。含真实 MyBatis + tenant interceptor 查询、账户/业务类型/已完成/软删/租户过滤、排序、受派规则及 overview 权限/空任务/无锚点/未来节点/无指标读取。日志 C:\Users\EDY\AppData\Local\Temp\zsjos-diagnosis-loading-backend.log。
- Frontend verification: `npm run typecheck` 通过；`npm test -- src/services/mediaAccountProfile.test.ts src/components/account-maintenance.guard.test.ts` 8/8 通过；本修复不涉及依赖、路由、打包配置或资源，未重复 production build。
- Browser verification: `python frontend/workbench/test/diagnosis-loading.py` 通过 12 个场景；1440/390 慢请求立即反馈与单 overview/preview、重复点击、任务/预览错误重试、未来/空任务、权限错误、关闭迟到响应、同组件切换账号迟到 overview/preview、深链接失败重试。`python frontend/workbench/test/weekly-account-diagnosis.py` 通过桌面/手机完整提交、校验、历史/统计与启动诊断回归。真实 admin-api 被阻断，均为实际组件的合成传输；截图 C:\Users\EDY\AppData\Local\Temp\zsjos-diagnosis-loading 已视觉检查，手机加载及桌面错误状态无溢出或遮挡。
- Documentation: weekly-account-diagnosis.md 补充 overview/交互/验证契约；media-account-profile.md 明确新周节点规则覆盖早期提前填写描述。
- Limits: 未部署或重启目标后端、未进行真实账号登录验收或目标环境耗时测量。新前端依赖 overview，需先部署后端再部署 Workbench；本地测试不代表线上已生效。

## Ownership release — AccountProfilePanel.tsx
- frontend/workbench/src/components/AccountProfilePanel.tsx 已完成本工作流写入、typecheck/专项测试/浏览器回归及最终差异检查，现明确释放该文件所有权，可由 handoff/local-account-delete-visibility-20261022.md 对应删除交互工作流接手。
- 本次验证版本 SHA256: A6213BDB2C24616B7FDC8370CE4391D80D0FDC74161E78E8F72015AC0F3F446D。接手者须保留诊断改动；后续删除交互修改应由其独立验证，本记录不覆盖接手后的变化。
- 本工作流不再修改 AccountProfilePanel.tsx；若后续发现需要更改，将先在本记录声明并协调，不与接手者同时写入。其余登记文件也随本次交付释放。

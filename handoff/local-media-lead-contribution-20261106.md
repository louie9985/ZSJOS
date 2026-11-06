# local-media-lead-contribution-20261106

## Registration
- 北京时间：2026-11-06
- Environment: local（ZSJOS_AGENT_ENV 与 /etc/zsjos/agent-environment 均未设置，使用 fallback）
- Goal: 新媒体期间表在成交率后新增业绩贡献金额列，覆盖本人及兼职冻结贡献归属。
- Non-goals: 不改权限、历史归属、数据库，不部署、不提交或推送。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: e23674d600e61ac87f3b6c8a13a8d81051d7b6a0
- Owner: Codex /root
- Ownership: MediaLeadFactMapper.java、MediaLeadOrderFact.java、MediaLeadAnalysisService.java、MediaLeadVO.java；MediaLeadConversionAmountTest.java、MediaLeadAnalysisServiceTest.java；frontend/workbench/src/services/mediaLeadAnalysis.ts、src/pages/MediaLeadAnalysisPage.tsx、test/media-lead.tsx；docs/api/media-lead-analysis.md；本记录。
- Dependencies: 金额门槛及时间归属等待用户确认；沿用现有冻结贡献范围、订单总金额。
- Verification: 聚焦 Maven 查询与期间聚合测试、Workbench typecheck、现有夹具浏览器桌面/手机验证。
- Target branch / Integration order: None

## Delivery — 2026-11-06（北京时间，统计口径待确认）
- Branch / Worktree / HEAD: 同登记，HEAD 未变。
- 用户目标：在成交率后新增业绩贡献，汇总本人及兼职提交客资成交首单金额。
- 已完成：frontend/workbench/src/pages/MediaLeadAnalysisPage.tsx 新增右对齐金额列，单位元、两位小数，旧接口未返回时显示“—”；src/services/mediaLeadAnalysis.ts 增加可选响应字段。
- 关键决策：冻结贡献归属沿用已有契约；时间归属和 1280 元门槛需用户明确后实现后端，不擅自增加业务限制。
- 验证：Workbench npm run typecheck 通过；scoped diff 确认列位于成交率后。尚未执行浏览器验证、后端金额汇总及测试。
- Changed files: 上述两个前端文件及本记录。
- Dependencies / integration impact: None；未新增依赖、未修改数据库、未提交或部署。
- Remaining: 等待用户回答提交日期/成交日期、金额门槛问题，再完成后端聚合、文档、聚焦测试与浏览器验证。工作流继续保留以上范围所有权。

## Registration update — 2026-11-06
- 用户已确认业绩贡献无金额门槛，其余沿用成交口径。按提交批次、当前有效状态、冻结贡献归属，每客资取截至查询时最早生效首购，生效时间相同时取内部订单 ID 最小者。缺失金额按零汇总。
- Scope / owner / verification 同登记。

## Delivery — 2026-11-06 10:34 北京时间
- Branch / Worktree / HEAD 同登记，HEAD 未变。
- 用户目标为成交率后新增业绩贡献，用户确认金额不设要求，其他沿用成交口径。
- 已实现 overview.periods.performanceContribution，按当前有效客资提交批次及冻结贡献归属，汇总截至查询时每条客资最早生效首购 total_amount。使用 BigDecimal 汇总，生效时间相同按内部订单 ID 取较小者，缺失金额按零处理。
- 页面显示业绩贡献（元），两位小数和千分位。明确列宽使 1440px 桌面展示全部列，390px 手机保留表内横向滚动，旧接口缺字段显示“—”。直接受影响统计契约及页面说明已同步。
- Changed files 包括登记中的四个后端主文件、两个测试文件、两个前端主文件、test/media-lead.tsx、docs/api/media-lead-analysis.md、本记录。
- 验证通过 Maven MediaLead*Test 共28项，包含 H2 MySQL 模式实际 MyBatis 查询的金额边界、多首购去重、同时间 ID 顺序、空金额、未来生效排除、复购/删除/跨租户排除和个人/部门/中心范围，以及服务提交批次聚合、无效客资排除。Workbench npm run typecheck 通过，git diff --check 通过。
- 浏览器使用已有5174页面夹具，经 Ego Lite 实测1440x1000及390x844宽度，金额列顺序、零值、千分位两位小数、页面无横向溢出通过，桌面截图确认完整显示新增列。截图保存在 C:/Users/EDY/AppData/Local/Temp/media-contribution-desktop-final.png 及 media-contribution-mobile-final.png。夹具验证不等于真实账号数据验收。
- 首次 Maven 命令因 PowerShell 属性参数拆分失败，改用完整引号后执行。首轮测试新增未来订单影响已有断言，修正夹具后28项全通过。浏览器初次断言误含测量行已修正，reload超时后检查当前页并继续验证。
- Dependencies / integration impact 为 None。Vue Admin 无该页面消费者。保留工作树其他已有修改。
- Remaining 为部署后的真实账号及生产 MySQL 数据验收。交付为本地代码，未执行提交、推送、重启或部署。当前实现文件所有权释放。

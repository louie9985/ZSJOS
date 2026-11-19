# 新媒体业绩目标

- 环境 local（ZSJOS_AGENT_ENV 及环境文件未设置）；负责人 Codex。
- 工作流 media-performance-target-20261116；分支 main；工作树 D:\ZSJ-OS；基线 21631a8a98a62c41a3c71596a6739e03a535c3b6；集成顺序 None。
- 目标：月度业绩目标设置、大盘目标与进度、金额列统一对齐。
- 非目标：改变贡献统计口径、权限、真实目标值、部署和服务重启。
- 所有权：MediaLeadAnalysisPage.tsx、MediaLeadTargetPage.tsx、MediaLeadTargetHistory.tsx、mediaLeadAnalysis.ts；后端 MediaLeadVO、MediaLeadTargetDO、MediaLeadAnalysisService 和对应测试；目标 SQL、API 文档、本记录。保留已有页面改动。
- 依赖：已有目标权限、组织汇总和首购贡献统计。用户已授权本机开发库非破坏结构更新。
- 验证：聚焦后端测试、类型检查、SQL 受控执行与重复执行、浏览器桌面及手机。

- 范围细化：V372 追加迁移（V283 保留已部署兼容）、core.sql、00-bootstrap-schema.sql；测试夹具、独立验证脚本。保留 baseline 现有其他改动。开发库为本机 yudao-mysql / ruoyi-vue-pro，V371 已登记，49 条目标记录且租户均为 1。

## 交付
- 两个指标独立设置/覆盖/恢复汇总，金额最多两位小数；共享行版本及修订审计。旧人数请求兼容，金额初始化不制造人数目标。
- 大盘金额目标、实际贡献、完成率和下钻入口；贡献列左对齐。保留既有下钻样式修改。目标页按指标切换，草稿未保存时禁止切换；修订历史同时显示两种指标。
- V372 已应用本机 yudao-mysql / ruoyi-vue-pro；49 条既有目标保留，金额仍未设置，租户均为 1。双账本成功，utf8mb4 中文注释 HEX 正确。未改真实目标值或授权。
- 隔离 MySQL 首次/重复、前置失败不登记成功、部分 DDL 恢复通过；完整 fresh 393 项迁移及零 schema drift 通过；结构差异测试 27 项通过。
- 后端 MediaLeadAnalysisServiceTest、MediaLeadConversionAmountTest 通过，涵盖金额小数汇总、独立覆盖/恢复、人数保留及过期版本拒绝。Workbench typecheck 通过。
- ego-browser 合成页面桌面 1440 与手机 390 宽检查通过；金额保存与恢复请求已核验。截图位于 C:/Users/EDY/ego/media-amount-20261116/。截图后金额输入加宽至 180 并修正成员空值提示，静态检查通过，未再截图。
- 未重启或部署后端；真实账号 API 保存及真实数据页面联调待运行新后端后验收。Vue Admin 无此页面消费者。无提交、推送。
- 交付后释放本工作流文件所有权。

## 布局反馈修订
- 重新接管 MediaLeadAnalysisPage.tsx、MediaLeadAnalysisPage.css、API 页面说明与本记录。环境仍 local；用户要求两组月目标及进度桌面并排，缩短卡片宽度。仅布局变化，验证 typecheck、桌面/手机浏览器与差异检查。

- 修订交付：两组采用自适应双列布局，桌面四张卡片同排、两条进度并排；390px 手机自动分组换行。1440px 与 390px 真实浏览器合成页面截图已检查，typecheck 和范围差异检查通过。截图 C:/Users/EDY/ego/media-goal-inline-20261116/。未改接口或数据库；释放修订所有权。

## 持续目标与同表编辑修订登记
- 当前分支/worktree/local 环境沿用原登记。目标：服务端菜单改名新媒体业绩设置，新媒体双指标同表编辑；新媒体月目标和销售周/月目标按各自周期持续沿用最近设置，修改不回写过去。
- 扩展所有权：PerformanceTargetService.java、SalesPerformanceTargetPage.tsx、新媒体 VO/服务/目标页和 typed API、目标聚焦测试、V373 菜单迁移、相关 API 文档。本机菜单名称更新属本次用户明确请求，不修改权限关联。保留销售合并现有实现。
- 验证：跨周期读取/首次修改/并发冲突/两个指标独立保留测试、typecheck、浏览器同表操作、菜单 SQL 首次重复与数据 HEX。非目标：修改业务目标数值、账号权限、部署或服务重启。

### 持续目标修订交付
- 服务端菜单 id 602334 已改为新媒体业绩设置；V373 只修改名称与版本账本，权限/角色关联不变。隔离 MySQL 前置失败、菜单缺失失败、恢复及重放通过，本机执行后 HEX 正确。
- 新媒体双指标同表一次提交，分别恢复汇总；sourceId/version 验证来源。月目标按最近生效配置沿用，跨月修改插入新行保留历史及另一指标。
- 销售周/月分别沿用；季度/年度汇总各月有效目标；跨周期保存保留历史及来源冲突校验。保留用户已有销售合并改动。
- 47 项后端聚焦测试通过；Workbench typecheck、范围 diff 检查通过。新媒体桌面/手机合成页面和一次双指标保存请求通过；销售使用 review 测试夹具展示持续沿用说明通过。截图 C:/Users/EDY/ego/media-target-table-20261116/。
- 5174 原服务连接被拒绝，使用自有临时 5186 端口验证；未重启原服务，真实账号联调未完成。未写业务目标值。没有提交或推送。
- 本轮交付释放所有权。

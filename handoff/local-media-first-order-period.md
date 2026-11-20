# 新媒体首单业绩期间调整

- ID: media-first-order-period；环境 local；负责人 Codex；分支 main；工作树 D:\ZSJ-OS；基线 82b5e11532bd13cb7965f80edd436a8a9eb25cdc。
- 范围：MediaLeadAnalysisService、MediaLeadQueryMapper、MediaLeadAnalysisServiceTest、MediaLeadPageQueryTest、MediaLeadAnalysisPage.tsx、docs/api/media-lead-analysis.md 和本记录。汇总、月目标实际和下钻按最早生效首购日期，保留成交率提交批次与原冻结归属。
- 非目标：权限、数据库、部署、其他统计。依赖：现有首购事实及生效时间。验证：聚焦后端统计与明细回归、工作台文案与类型检查。目标分支/集成顺序：None。

交付：汇总与金额明细改用全历史最早生效首购日期，顶部月实际复用期间汇总；同步工作台与 API 口径。32 项 MediaLead 后端测试通过（含跨月下钻、跨日汇总、成交率不变），Workbench TypeScript 检查通过。未部署，未作真实账号验收，未修改数据库。

# registration-class-cleanup | local | Codex
main / D:\ZSJ-OS / 21631a8a98。集成 None。范围：报名履约工作台、管理端详情、履约 API 文档与定向验证。目标：移除任务中分班步骤。保留订单自动分班与班级管理。依赖：历史逐商品交付归属待确认。验证：前端类型检查及履约页面检查。
用户确认未选班历史商品沿用销售归属流程。扩展范围：RegistrationController、RegistrationServiceImpl、所属回归测试及两端履约测试。
交付：两端移除履约选班控件、Workbench 分班筛选和自动分班步骤，移除分班写接口及客户端调用。历史未选班商品按销售归属建立交付，已保存选择按原规则校验。49 项后端定向测试通过，Workbench 类型检查通过，Edge 合成页面验证 Workbench 桌面/手机、可完成任务提交及 Admin 历史任务详情。Admin 全量类型检查仍有 MES handleRefresh TS6133。未部署、未改业务数据。

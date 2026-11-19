# local-registration-material-dialog
local，Codex，main，D:/ZSJ-OS，21631a8a98。集成 None。工作范围：Workbench 报名资料组件、履约清单、资料与履约文档、RegistrationServiceImpl 和针对性测试。目标：独立查看资料，履约完成与学员填写解耦。保留资料权限、协议和收集任务状态。验证：服务测试、两端完成契约、Workbench 类型及浏览器验证。
交付：移除完成命令和完成资格投影中的表单状态限制；Workbench 的资料字段按按钮打开才读取，缺少清单修改权限时提示只读。共享后端 48 项测试通过，Workbench 类型检查通过；Edge 桌面和手机合成任务验证资料弹窗、未提交时勾选、拒绝权限。Admin 源码使用相同 completable 结果，本次未做真实账号验收或部署。

工作流 account-field-responsibility，local，Codex，main，D:\ZSJ-OS，基线 21631a8a98。范围为 MediaAccountFieldPolicy、MediaAccountProfileServiceTest 与 docs/api/media-account-profile.md 中普通人工字段责任说明。目标为责任双方协填，排除诊断流程、权限配置和数据库。依赖现有账号对象授权，验证字段矩阵、上传与保存。集成顺序与目标分支 None。
补充范围：同步 frontend/workbench/docs/ui-guidelines.md 中账号字段只读规则。
交付：字段列表、上传、追加与保存复用更新后的责任策略，责任标签及待补统计沿用原配置。账号档案服务及控制器权限测试通过。尚未部署或使用真实账号验收。

# 账号诊断要求与周期跟进部署

UTF-8。适用于当前开发基线与已授权本地修正，不作为其他部署环境校验和豁免。

1. 前提：账号档案 V209、定位卡凭证 V261、交付轮次 V264；后台已有 System 权限、业务任务及事件表。
2. 本次无新表列。当前开发 V209 修正 delivery_goals 的 AUTO/ACCOUNT/非人工必填配置。已有本地库只执行 `script/sql/mysql/media-account-diagnosis-fields.sql`，禁止重跑整个 V209。目标为所有租户非删除 published/draft 配置中 delivery_goals 三项元数据，其他字段和业务值不改。
3. 执行前暂停配置编辑，备份配置；用 utf8mb4 连接执行。脚本事务内归档旧发布版并追加新配置版，草稿更新并发版本。可重复执行；回退配置需通过发布前版，业务只读约束仍需配套代码，不删除历史。
4. 验证入口 `python script/sql/mysql/tools/test_account_diagnosis_fields.py`；加 `--sync-local` 仅同步本地 yudao-mysql/ruoyi-vue-pro。会保留受控验证库，不删除数据。
5. 更新后端与工作台构建。本文不授权服务重启或发布。后端调度在重新运行后每 5 分钟同步来源、修正旧 pending 任务及生成新周期任务。要求同步和首轮锚点由真实学员同意与凭证时间恢复，缺失时待核实；完成记录及旧正文不重写。
6. 远程已部署环境先核验其迁移记录并审核升级流程，不覆盖已应用 V209 校验和。新接口无需单独角色授权，沿用账号维护与对象责任权限；管理端使用已有 AUTO/ACCOUNT 元数据。

本地验证记录：2026-09-18 受控库 diagnosis_fields_verify_20260918182857；配置备份 backups/mysql/diagnosis-fields-20260918182857.json；首次、重复执行、中文 HEX、其他字段及业务/role_menu 校验通过。未执行真实账号诊断提交、真实通知、服务重启或生产发布。

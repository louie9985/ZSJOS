# 本地整合双入口订单审批与报名协议
负责人 Codex，环境 local，main，D:\ZSJ-OS，基点 21631a8a98。目标与顺序 main 工作区先整合 dual-entry-order-approval，再整合 enrollment-agreement；范围为两工作树变更路径及本记录。依赖现有 main 未提交功能，保留其修改。排除其他工作树、共享库、部署与推送。验证文件合并、针对性测试与类型检查。
交付：两工作树未提交内容已整合到 main 工作区，协议提示迁入 RegistrationStudentInfo，沿用历史月份可空处理。五组后端测试共 143 项、工作台 29 项、工作台类型检查及三个前端生产构建通过。Admin 全量类型检查有 MES handleRefresh 和 leadQualification Refresh 未使用；Docker 引擎不可用，未复跑 V378 隔离库验证，未写业务库或进行真实账号验收。未提交、推送，释放文件所有权。

# 媒体账号删除审批

账号删除使用 `zsjos_media_account_delete` BPM 流程。申请接口为
`POST /admin-api/zsjos/media-account/{id}/request-delete`，请求体为 `{ "reason": "删除原因" }`。
申请需要 `zsjos:media-account:delete` 与账号对象权限。当前责任编导或运营可以申请。

审批人从申请人部门向上寻找非本人、启用且具有 `zsjos:media-account:delete-approve`
权限的部门负责人。无有效审批人时拒绝提交。申请期间账号冻结；驳回或撤回恢复使用。
申请人通过 `POST /admin-api/zsjos/media-account/{id}/withdraw-delete` 撤回。

部署前执行 V271，并部署 `script/bpm/zsjos_media_account_delete/1.0.0/process.bpmn20.xml`。
权限元数据由迁移创建，角色权限仍由管理员配置；迁移不分配角色权限。

V271 在 V270 后、V272 前执行，要求已有媒体账号表、账号页面菜单及两张版本表。
MySQL 8 加列通过 `information_schema.columns` 与预处理语句逐列判断；部分执行后可重跑，
保留已有字段值，仅补齐缺失字段和索引。使用 utf8mb4 客户端并设置遇错停止。
执行后核验六个 `delete_*` 字段、`idx_media_account_delete_process` 索引、审批快照表、
两个按钮及两张版本表；版本记录存在不代表迁移完整。回滚应用时保留新增结构与快照，
不通过删表或删列回滚。已部署环境的文件校验和变更需单独审查发布，不自动对账。

专项验证：`python -B script/sql/mysql/tools/verify_media_account_delete_upgrade.py`，
覆盖首次执行、部分完成恢复、重复执行、跨租户数据保留和角色授权不变。

## 当前交付限制

本轮包含申请、撤回、BPM 结果回调、账号冻结与 Workbench 申请入口；申请快照存入
`zsjos_media_account_delete_request`，审批中心通过公共内容提供器读取快照。
审批通过时取消账号维度待办，并从共享拍剪工单、内容审核批次的账号集合移除该账号；
没有剩余账号的共享拍剪工单标记取消。关联任务取消使用幂等更新。

若关联业务终止失败，申请状态为 `approved_pending`，账号保持冻结，主管可调用重试接口，
成功后才标记账号删除完成。V271 已通过受控 MySQL 首次执行、部分完成恢复及重复执行验证，
本地开发库缺失字段和索引已补齐。目标部署环境迁移、BPM 部署、审批中心浏览器流程和共享单据
运行验收仍需按目标环境执行；本地 SQL 验证不代表审批流程已上线。

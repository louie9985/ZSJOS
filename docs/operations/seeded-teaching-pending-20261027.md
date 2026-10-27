# 轻创教学服务恢复待接收（2026-10-27）

## 授权与精确范围

用户在确认原因后明确要求将95条服务恢复为待接收，不重启后端。
此前分班修复保留历史accepted但没有教学首联任务，教学阶段也为空，造成既不能首联也不能普通跟进。
本次确认替代前次对这95条服务“保留接收状态”的决定；前次执行记录仍保留为历史证据。

目标仅为test环境 `zsjos-mysql-1` / `zsjos` / 租户1，服务内部ID：11–46、837–839、841–890、892、893、896、897、1001、1003。
将acceptance_status设为pending，accepted_at及accepted_by_user_id清空；version递增，更新系统修复审计字段。
旧接收字段完整保留在私有前像备份。每条服务追加一条系统修复事件，不伪造员工接收记录。
教学阶段继续为空，实际学习规划师接收后由原业务事务设置first_contact并创建首联任务，时限从实际接收时计算。

轻创课程、班51、教学负责人400、media_ready=true、编导/运营归属及媒体交付进度保持原值。
服务1000/1002/1080已有真实教学进度，明确排除修改并纳入保护检查。
不修改订单、权限、配置、应用、schema、版本台账；不手动创建任务或发送通知。

## 执行、依赖与恢复

执行器：`script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py`。
依赖已有 `assign_seeded_light_course_20261026.py` 的快照、锁、事务、临时表验证和重放检查；本次单独定义发现条件、修改、审计及结果检查，不执行旧课程修改逻辑。
依赖当前MySQL schema、有效班级负责人、已配置接收权限、已发布首联配置，以及运行后端的MediaServiceReadiness隔离支持。
只有仍为accepted、无教学阶段/跟进/任务/调班申请/教学进度的精确95条服务可进入计划。
任何前像变化、接收任务幂等键占用、部分审计或后端变化均拒绝执行，防止覆盖用户的新工作。

```bash
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py prepare
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py fixture --sha256 <计划摘要>
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py preview --sha256 <同一摘要>
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py apply --sha256 <同一摘要>
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py verify --sha256 <同一摘要>
python3 -B script/sql/mysql/repairs/restore_seeded_teaching_pending_20261027.py apply --sha256 <同一摘要>
```

顺序为只读一致性快照、同引擎临时表演练、真实事务试跑并回滚、正式事务提交、独立连接核验、重复执行0更新。
所有连接使用utf8mb4；正式写入只UPDATE服务与INSERT审计，采用单事务、前像校验、版本校验、5秒锁超时和命名锁。
临时表演练覆盖首次执行、1/39/50/95行处故障回滚、恢复、重复执行、部分审计拒绝和反向恢复，不删除真实业务数据或历史审计。

私有证据目录 `/opt/zsjos-runtime/backups/seeded-teaching-pending-20261027-test` 为0700，文件0600；完整前像和备份不进入日志或版本库。
正式恢复需按届时授权重新核对状态，只能在无后续业务变化时按备份恢复本次字段并追加恢复审计；已正常接收或跟进的服务不可覆盖。
本脚本不是bootstrap或编号迁移，不记录schema版本。重复执行要求完整审计及精确后像，业务推进后拒绝重放而非重置。

## 验证边界

逐字段核对保护范围内的课程、媒体账号/交付、订单、任务及其他服务；核对95条待接收及接收字段清空、95条审计中文HEX。
后端PID、启动时钟及模块SHA在执行前后须一致。接收方法和Mapper的源码契约要求pending状态、当前负责人和版本匹配，并设置first_contact、按已发布配置创建任务。
数据库核验和部署检查不等同于登录浏览器的完整流程验收；不冒用规划师账号，不为验证而真实接收这95条服务。

## 执行结果

2026-10-27 14:25:38（北京时间）已提交95条服务更新及95条追加修复事件。
计划SHA256：`5f1a9587342df047b7fd7bc2a089f9f845e12fffa462d3b43662bd34de29f08f`。
独立连接核对90组查询、10770行前像与预期结果通过：95条均待接收、接收人及时间为空，班级、负责人、课程、media_ready及其他字段保持预期。
媒体、订单、任务和其他服务逐字段保护检查通过；事件中文HEX通过。临时表首次/4处故障回滚/恢复/重放/部分审计拒绝/反向恢复、真实事务试跑回滚均通过，正式执行后重复运行0更新。
运行JAR的接收/首联创建路径及MediaServiceReadiness字节码已检查；后端PID、启动时钟、模块SHA未变，没有重启后端。
Python语法、UTF-8、证据0700/0600权限及diff检查通过。未进行真实账号API或浏览器流程验收；用户刷新页面后由实际负责人接收，再按正常教学流程首联及跟进。

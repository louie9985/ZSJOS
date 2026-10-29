# 中世健历史报名任务关闭（2026-10-28）

## 授权与范围

用户确认关闭旧订单遗留任务，理由为 `旧数据处理20261028`，并在业务 API 无可用登录
会话后明确授权直接修改数据库。本次仅处理 test 环境、租户 1 的固定 40 条历史商品任务：
39 条属于 30 张 superseded 订单，1 条属于 terminated 订单。固定任务编号见执行器
`script/sql/mysql/repairs/close_legacy_registration_20261028.py` 的 IDS，不按动态查询扩展范围。
其中包含 OR202609170003 的两条任务；接续订单 OR202609180009 不受影响。
另有 36 条已生效订单的历史待处理任务，本次不处理。

## 关闭语义与边界

按 RegistrationServiceImpl.close 的商品任务语义，将 status 从 pending 改为 ended，
写入指定 cancel_reason、实际关闭时间 cancelled_at/completed_at，version 加一。
使用北京时间，与运行服务 TZ 和 JDBC 会话约定一致。历史导入的 completed_at 虽非空，
任务状态仍为 pending；原值完整保存在受限前像，当前字段改为本次关闭时间。

此次为直接数据库修复，没有已登录员工身份；completed_by_user_id 保留 null，
updater 使用 `registration-legacy-close-20261028`，update_time 记录实际操作时间。
不冒充员工、不伪造 API 幂等命令或操作日志。用户要求“关闭服务”优先于此前建议的
自动取消，因此结果是“已结束（无需交付）”，不是 cancelled 或 completed。

预检要求全部任务有商品身份、旧导入标记及 V317 更新标记，且没有活动的服务关系、
信息收集表、幂等命令、清单、路由、分班、附件、履约事实或报名业务任务。
发现任何前提变化即停止；有依赖的任务不能借用此脚本绕过正常服务取消逻辑。
订单、订单商品、审批、其他报名任务及关联记录保持原值。不删除记录，不变更权限、
数据库结构、版本台账或应用配置，不发送通知、不重启服务。

## 执行与验证

前提：现有 test 数据库、仓库 db_config 连接配置及已安装的 PyMySQL；不添加依赖。
执行器只接受明确解析为 test 的环境，连接使用 utf8mb4 并执行 SET NAMES utf8mb4。
会话临时表保留实际结构及索引，先验证首次 40 行、重复零写入、在第 1/20/40 行注入失败
全部回滚、恢复执行和后续编辑拒绝。临时表测试不会修改真实业务行。

```bash
python3 -B script/sql/mysql/repairs/close_legacy_registration_20261028.py \
  --evidence-dir /opt/zsjos-runtime/backups/registration-legacy-close-20261028/preview
python3 -B script/sql/mysql/repairs/close_legacy_registration_20261028.py \
  --evidence-dir /opt/zsjos-runtime/backups/registration-legacy-close-20261028/applied --apply
```

正式操作按订单、任务顺序锁定，重新核对前像及依赖。在写入前将 before.json 和
pending-after.json 以受限文件权限保存并 fsync，然后单事务更新 40 行；字段白名单、
所有未关闭任务和关联数据比较及中文 HEX 检查通过后才提交。独立新连接再次比较
提交后的全部前后像，并通过 UTF-8 读取理由及 HEX；结果保存为 verified.json。
备份可能包含业务信息，只留在受限目录，不贴入日志或交付报告。

同一证据目录重跑时，只有当前状态与完整预期后像完全相等才返回零写入；发生任何
后续修改或部分状态即拒绝。若提交时连接中断或提交后证据写入失败，应先比对
pending-after.json 与真实数据库，不能假定事务未提交。事务回滚但证据已经存在时，
先核对真实前像，再另建证据目录重试；不能覆盖或删除原证据。

回退需要另行确认，并确保目标行仍等于本次后像、没有后续业务操作；依受限前像恢复
本次修改字段并记录真实恢复审计，不覆盖其他业务进展。脚本不自动回退已提交修改。
本次不改变 SQL 初始化、迁移链或 schema，完整 fresh/upgrade 验证不适用。
数据库执行不等于已登录 API 或双端浏览器验收，后两者本次未执行。

## 执行状态

2026-10-28 12:55:19（北京时间）正式提交 40 行，全部为 ended，理由精确匹配
`旧数据处理20261028`。独立连接确认完整后像、中文 UTF-8/HEX 及关联数据保持不变；
再次运行同一证据目录为零写入。首次、重复、三处失败回滚、恢复及后续编辑保护均通过。

OR202609170003 的两条任务已结束；当前租户已替代或已终止订单下的待处理任务数为 0。
已生效订单下的 36 条历史待处理任务保留原值。未执行 API/浏览器登录态验收；
无应用部署、权限更改或服务重启。预演证据在 preview/，正式前像、预期后像和核验结果
在 applied/，均位于上述受限备份目录。

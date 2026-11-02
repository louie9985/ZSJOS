# 中世健历史订单收集任务补齐

工具：`script/sql/mysql/tools/backfill_student_info_tasks.py`。Python 3 + 现有 PyMySQL 运行环境；不增加项目依赖。先执行 V359 及匹配的后端部署，再运行本工具。工具检查两本版本台账、实际表引擎和任务唯一索引，实际查询/写入失败会回滚，不靠版本号跳过验证。

## 数据范围和保护

- 仅租户 1，未删除、状态仅为 `pending_approval`（审核中） 的订单及其未删除商品，每个商品补一条缺失任务；不依赖是否已产生履约任务。不补齐待补正或已生效订单。
- 排除待补正、已生效、终止、被接续替代和删除订单。客户必须存在且未删除。已有任务保持原样；逻辑删除任务、错误绑定或不明确的历史表单使整次执行停止，需先人工核查。
- 唯一且订单商品/客户一致的历史表单被关联到新任务，不改表单/答案/附件；仅关联履约但缺少商品绑定的历史表单不自动推断。
- 不改权限、字典、订单、履约、表单或迁移台账，不建立表，不补造发送/填写事实。新增审计标记为 `student-info-backfill-v1`（ASCII）；连接使用 utf8mb4，无中文内容写入。
- 默认只读预览。应用必须传入相同目标、相同范围的预览指纹，状态漂移/数量变化会拒绝。应用按订单优先加锁，使用单个 InnoDB 事务；唯一键冲突、超时、任何错误或后置校验失败全部回滚，重新预览再运行即可。
- 成功后可重复预览：同一范围缺失为 0。后续新订单可能改变结果。工具不提供自动删除回滚；提交后任务可能被使用，应保留资料，若确需撤销须核对审计标记和引用再单独制定修复方案。

## 本地

```powershell
python script/sql/mysql/tools/backfill_student_info_tasks.py --local
python script/sql/mysql/tools/backfill_student_info_tasks.py --local --apply --expect-plan <预览返回的plan>
```

可对两条命令同时追加 `--order-no <订单号>` 限定单笔。`--local` 明确读取仓库的 `application-local.yaml`，不能在线上使用它猜测生产目标。

## 线上

在受控运行环境通过安全方式注入 `ZSJOS_DB_HOST`、`ZSJOS_DB_PORT`（默认 3306）、`ZSJOS_DB_NAME`、`ZSJOS_DB_USER`、`ZSJOS_DB_PASSWORD`；工具不打印密码。账号需读取相关业务表/台账和插入任务表的权限，连接应使用受信的数据库网络或安全隧道。不要将密码放入命令参数、仓库或交付记录。

```sh
python script/sql/mysql/tools/backfill_student_info_tasks.py
python script/sql/mysql/tools/backfill_student_info_tasks.py --apply --expect-plan <预览返回的plan>
python script/sql/mysql/tools/backfill_student_info_tasks.py
```

先检查输出的目标 host/port/database、missing 和 blocked；有冲突时不执行。保留预览和应用输出作为执行记录。生产执行前遵循既有数据库备份流程；建议低峰运行，工具应用期间会锁定所选范围订单和关联行，可能短暂阻塞订单办理，锁等待上限 15 秒。此工具的交付不表示已对线上执行或验收。

验证：`python script/sql/mysql/tools/test_backfill_student_info_tasks.py`，使用本地连接上的临时表，不修改真实业务表。覆盖初次补齐、重复执行、回滚、部分完成后恢复、终态/租户排除和历史关联保护。

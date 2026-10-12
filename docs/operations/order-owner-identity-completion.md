# 成交归属身份补齐（V306）

2026-10-10 用户逐批确认：OD202609212049560024（528）与 OD202609221356340006（400）补 education，其余审核清单 284 笔补 sales。此决定是修复依据，不按当前角色、昵称、部门或旧 submitter_center_type 推断。范围为租户 1 当前生效订单；保留三个离职销售订单 OR202606250003、OR202606260004、OR202606260005 和此前排除的 107 笔批量录入订单。

## 写入与边界

迁移 `script/sql/mysql/migrations/V306__order_owner_identity_completion.sql` 内含固定订单号、预期成交负责人 ID 和目标身份。匹配当前库业务编号，内部轮次 ID 动态关联。

只修改：订单 formal_owner_identity；当前轮次 order_snapshot.order.formalOwnerIdentity；formalSales 对象已存在时同步其 identity；以及两表 version/updater/update_time。缺 formalSales 对象不新增人员。保留旧员工身份域、人员编号、姓名、采集时间、金额、其他 JSON 字段和客资档案。不会修改 Lead、业绩归属、BPM、账号权限或触发通知。

V305 已完成且有成功台账，因此本补充使用 V306，不重写 V305。V305 的旧 after-image 核验会正确报告本次合法后续变更；不能把它当作 V305 失败，更不能再次运行 V305 回滚覆盖 V306。恢复按 V306 后 V305 的逆序分别评估。

## 执行与恢复

前置：两张台账均有 V305、备份字段已为 LONGTEXT、MySQL 8、utf8mb4。部署目标可直接通过现有迁移 runner 或 UTF-8 fail-fast mysql 客户端执行 SQL，不依赖本地旧库或私有源包。禁止 mysql --force。

SQL 持有数据库级命名锁，在 SERIALIZABLE 事务内验证订单仍生效、负责人 ID 一致、当前轮次属于订单且 approved/V2/最新，首次只接受空身份。目标不存在时跳过，适用于新装空环境；已经存在但状态或负责人不同则停止全批。不得将用户确认清单扩大为所有空身份记录。

首次保存修复前后镜像到 zsjos_data_repair_backup，repair_key=order-identity-v306；订单留存身份及修改审计，轮次留存完整 JSON及修改审计。变更、后置核验及双台账在同一事务，任何失败 ROLLBACK/RESIGNAL。例程 DDL 在事务外，不声称可回滚。临时表明确采用 utf8mb4_unicode_ci，兼容默认排序规则不同的空库。

重复执行复用首次 before/after，不重新递增版本。部分恢复只接受镜像吻合的 before/after；单列混合状态、后续版本或审计变化、残缺备份均拒绝。回退先核验当前等于 after，按 before 恢复两表本次变化字段；不重写生成列，不删除订单。真实回退须重新核对后续变化并单独批准。

```powershell
python script/sql/mysql/tools/order_owner_identity_completion.py test
python script/sql/mysql/tools/order_owner_identity_completion.py apply-local
python script/sql/mysql/tools/order_owner_identity_completion.py verify
```

test 仅修改新建、保留的隔离 schema；apply-local 明确写本地 ruoyi-vue-pro 并先保存私有完整原行，执行后重放及检查逐字段差异；verify 只读。生产执行仍须单独授权。

## 已完成验证

最终隔离 schema v306_20261010164810 和其 empty 副本：首次、重复、前置失败、第二台账失败回滚、带版本标记部分恢复、后续修改拒绝、完整回退演练及空库重复通过。逐字段验证只有上述允许列和两个 JSON 路径变化。

本地已应用：286 条订单和286 条当前轮次，2 education／284 sales；身份与快照不一致为0，两张版本表均 V306。立即重复执行数据校验值不变。除107 笔既定排除批次外，剩余空身份仅为上述3 笔离职销售订单。私有备份和验收位于 backups/mysql/order-identity-v306-*。

本次不改接口、页面和新建订单写入逻辑。真实已登录页面验证仍受本会话登录态不可用限制；数据库中现有读取路径的源字段已一致，不能将此表述为浏览器验收通过。没有生产执行、服务重启、提交或推送。


## 2026-10-11 客户端重复执行修订

V306 的完成分支保留完整目标／人员／轮次检查，要求证据齐全并验证订单和快照的正式归属身份。已完成时不再因无关快照、版本或审计变化误报，直接零写入返回；真正恢复仍按原完整前后镜像执行。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。

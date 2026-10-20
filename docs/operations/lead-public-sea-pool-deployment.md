# 公海池命名升级

当前实现统一使用 公海池 / PublicSeaPool。升级脚本为 script/sql/mysql/migrations/V329__public_sea_pool_naming.sql。V034 等历史迁移保持原样，以保留已部署环境的升级和校验和兼容。

## 对象与范围

| 当前对象 | 用途 |
| --- | --- |
| zsjos_lead_public_sea_pool_cycle | 公海周期与当前参与关系 |
| zsjos_lead_public_sea_pool_event | 周期事件 |
| zsjos_lead_public_sea_pool_notify_stage | 提前提醒阶段及幂等状态 |
| zsjos_lead_public_sea_pool_compatibility_record | 历史兼容记录，不是另一个公海池 |

接口统一为 /admin-api/zsjos/lead/public-sea-pool；菜单页面为 /zsjos/lead-public-sea-pool；权限前缀为 zsjos:lead-public-sea-pool。筛选视角为 publicSeaPool；通知场景前缀为 zsjos.lead.public_sea_pool，模板变量前缀为 publicSeaPool。跟进规则列名为 public_sea_pool_timeout_days。

## 执行顺序

1. 确认目标数据库和发布窗口，备份四张公海表、跟进规则、菜单、筛选方案、通知规则和模板、两个迁移账本。
2. 停止旧应用产生新请求及提醒，先完成历史公海通知队列。脚本检查 pending/processing/retry 状态并拒绝带队列升级。
3. 使用 utf8mb4 客户端执行 V329，客户端必须遇错即停。旧表或新表必须恰好存在一个；冲突停止，不自动合并或删除。
4. 校验表及列名、关联关系、行数和内容摘要；角色菜单关系原样保留，原菜单 ID 原样保留，仅权限标识随代码一起更新。管理员无须重新分配角色授权。
5. 配套发布后端和两个前端，并在获准的服务启动后，验证有权限/无权限访问、页面筛选、规则保存和通知发送。

脚本不改变客资归属、状态、订单、审批或历史消息，不修复复购资格缺陷。新代码必须与 V329 同时发布，旧接口和旧表名没有运行时别名。

## 重复执行和失败恢复

MySQL DDL 隐式提交。脚本允许旧/新表混合的部分重命名状态，检查实际结构后重跑即可继续；所有配置更新及迁移登记在一个事务内，执行失败不登记成功。现有迁移成功记录不能代替结构检查。名称冲突、排队通知或其他前置条件失败应先解决具体原因，不能跳过检查。

回滚需要协调反向重命名表、列、索引及配置，并回退应用版本。不得在运行的新应用旁直接反向改表；不提供自动数据删除或覆盖恢复。

## 验证

python script/sql/mysql/tools/verify_public_sea_pool_naming.py 仅在同容器新建唯一命名的隔离数据库，复制相关表后验证初次升级、重跑、部分应用恢复、执行失败与账本、授权不变，并输出中文 HEX。原数据库只读；隔离库保留供复核。

历史基线继续使用当时结构；Core manifest 的 baselineNamingUpgrade 声明仅允许 V329 的名称映射，静态校验仍拒绝任何其他结构差异。完整初始化链及目标环境运行验收另行记录，隔离升级成功不等于部署完成。

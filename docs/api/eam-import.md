# EAM 分类配置与资产台账导入

## 范围

EAM 管理端支持两类独立导入：分类配置导入，以及中世健资产台账导入。资产台账只读取工作表 `在岗资产初始申报表`，第 2 行是正式表头，第 3 行开始是数据；其他工作表不会创建 EAM 资产卡片。

## 接口与权限

| 接口 | 权限 | 行为 |
| --- | --- | --- |
| `GET /admin-api/eam/category/get-import-template` | `eam:category:import` | 下载分类与字段配置模板 |
| `POST /admin-api/eam/category/import/preview` | `eam:category:import` | 预检新增、更新、跳过和冲突，不写库 |
| `POST /admin-api/eam/category/import/commit` | `eam:category:import` | 无冲突时幂等新增或更新，不删除已有配置 |
| `GET /admin-api/eam/asset/get-import-template` | `eam:asset:import` | 下载双层表头 54 列台账模板 |
| `POST /admin-api/eam/asset/import/preview` | `eam:asset:import` | 预检每一行的分类、默认值、人员匹配、动作和警告 |
| `POST /admin-api/eam/asset/import/commit` | `eam:asset:import` | 按预检规则提交并记录批次及行来源 |

分类配置模板以分类编码作为稳定标识。分类按编码幂等新增或更新，字段按“分类编码 + 字段标识”幂等新增或更新；导入不执行删除。管理端自定义字段始终选填，`collectionVisible`、`collectionRequired` 和 `conditionRule` 只为未来员工收集表保存规则。

## 台账映射

- `资产标签` 映射为资产业务编号；空值提交时使用现有编号规则生成。
- `资产大类` 与对应明细列定位叶子分类。选择“其他...”时，说明文本优先作为资产名称；`其他` 根分类稳定映射到叶子 `其他资产`。
- `使用人姓名` 通过 System `AdminUserApi` 在当前租户的启用用户中按姓名唯一匹配。唯一匹配时写入用户和其权威部门；未匹配或重名只产生警告并保留来源姓名。
- 数量空值或非法值按 1；单件分类数量固定为 1，批量分类保留正整数数量。
- 使用状态空值或未知值按闲置；采购日期无法解析时保留原文本并产生警告。
- 附件只保留来源文件名快照，没有文件 URL 时不写 `fileUrls`。
- 责任人、上级、入司日期、签核、核对、创建和交接信息保存在分类扩展字段中。

列 `微信密码`（索引 36）不会被解析器读取，不会进入扩展字段、预检响应、警告、失败明细或日志。导入代码不得增加其他凭据列映射。

## 重复与更新

每次提交写入 `eam_asset_import_batch`，每个已处理行写入 `eam_asset_import_row`。来源幂等键为当前租户内的文件 SHA-256、工作表名称和 Excel 行号：

- 相同文件的相同行再次提交时显示并记录为重复跳过。
- 已有资产标签默认跳过；只有请求显式传入 `updateExisting=true` 才更新已有资产。
- 无资产标签的行首次提交后通过来源记录阻止同文件重复创建。
- 预检和提交统计分别返回新增、更新、跳过与警告，不把历史缺项视为失败行。

## 数据库变更

受控升级顺序为 EAM `V001`、`V002`、`V003`、`V004__eam_import_and_quantity.sql`。V004 非破坏性地增加分类管理模式、字段收集规则、资产数量快照、导入批次/行来源表和分类导入权限，不删除业务数据。

V004 可重复执行：列、表和菜单均有存在性保护，行来源有租户级唯一键。MySQL DDL 不能由事务完整回滚；执行前应备份 EAM 表结构，执行后检查新增列、两张导入表和 `eam:category:import` 权限。任何环境执行迁移、授权角色或重启服务都需要单独确认。

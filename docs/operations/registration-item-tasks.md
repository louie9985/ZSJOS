# 报名履约按订单商品拆分

## 范围

V317 依赖 core V316 双台账。新任务以订单商品明细为唯一身份；同一学员仍是一份 Person，
每项课程拥有独立任务、清单、班级和服务关系。订单 BPM 与财务生效前置条件不变。
2026-10-15 后保存分班可在财务待审时建立交付，任务完成仍待财务通过；详见 [提前交付](registration-early-delivery.md)。
无需交付的任务通过原 close 权限独立结束，状态 ended，不创建新服务；若已有提前交付则取消本任务服务及未完成待办。

## 升级与恢复

1. 只读核对当前 V316 台账、任务和订单商品关联；备份五张 registration 可变表及两张版本台账。
2. 在获准的报名写入维护窗口停止旧后端写入。旧后端按整单查询，与新结构不兼容。
3. 用 UTF-8 MySQL 客户端、失败即停执行 V317__registration_tasks_per_order_item.sql。
4. 核对按商品拆分结果、附件/清单/分班关联、历史终态及服务关系不变、两张 V317 台账和中文 HEX。
5. 发布匹配的后端、Workbench 和 Admin，再恢复写入并进行登录态接口验收。

迁移只拆租户 1 的 pending/processing 整单任务。最小商品明细保留原任务 ID；其余复制清单、
附件引用及旧路由，分班记录移动到所属商品任务。不删除文件、不调整账号授权，不更改订单、
审批或已有服务。completed/cancelled 历史整单任务保持原样。
DDL 隐式提交；业务拆分及两张台账在同一事务内。失败后先查真实结构和双台账，再原脚本重试；
不能把台账存在作为跳过校验依据。两个历史整单唯一索引名称均兼容。
新任务已被使用后不能直接回滚旧结构，须按备份及新增操作审计进行单独核准的恢复。

## 已验证与未执行

以下为原功能交付时的验证记录；不代表其他环境当前的执行状态。

- 46 项 Registration Java 测试通过，覆盖相同产品不同明细独立创建、重放、历史兼容、
  当前商品完成及拒绝兄弟商品分班、无需交付结束。
- MySQL 隔离副本首次、重复、部分 DDL 恢复、空表、前置/数据失败台账保护通过。
- zsjos_db.py check 通过；Workbench typecheck 通过。
- 真实 React/Vue 页面使用隔离响应验证桌面独立完成/结束；Workbench 390px 产品列表通过。
  移动端既有详情操作区不可用，未宣称移动端操作验收。
- Admin 全量 ts:check 被 withdrawal/index.ts 既有重复字段阻塞，报名文件无诊断。
- 本地只读预检：125 pending + 4 processing 将拆成 141 商品任务；16 completed 和 14 cancelled 保留。
- 未执行本地业务库 V317（版本记录为 0），未停止/重启运行中旧后端，未部署、未执行真实登录态验收。
  未进行整条 fresh/upgrade 发布验收；以上 scoped MySQL 验证不代表全链发布验收。

复现：python script/sql/mysql/tools/test_registration_item_tasks.py（保留隔离库）。
其 --sync-local 仅能在另行获准且确认旧后端停止写入后使用，不是启动应用自动迁移。
页面 fixture：Workbench test/registration-item-tasks.html（5297）；Admin test/registration-items.html
配合 test/registration-items.vite.ts（5296）。验证脚本位于各端 test 目录。

## V317 结构快照同步（2026-10-15）

schema/core.sql 与 00-bootstrap-schema.sql 已同步 V317 的最终结构：
报名表新增可空 order_item_id，唯一索引为
uk_registration_case_item(tenant_id, order_item_id, deleted)，订单查询索引为
idx_registration_case_order(tenant_id, order_id, deleted)，移除旧 uk_tenant_order。
历史整单任务的商品身份仍可为空。两份文件仅同步这一张表的结构，不包含数据修复。

适用范围：空库仍按 bootstrap 后执行待执行迁移的顺序初始化；已有库仍执行原 V317
并遵守上述维护窗口要求。不得在已有库上重放 bootstrap 来修补结构，不新增迁移编号、
不改写 V317 文件或历史账本校验和。已经完成 V317 的数据库只需更新仓库校验依据，
不需要再次修改表。建表使用 IF NOT EXISTS，不能作为既有表结构升级手段；
回滚限制仍按 V317 执行后的业务状态处理，不能恢复订单唯一约束而丢弃商品任务。

验证：MySQL 8 隔离空库完整 Core 链、最终结构 drift 检查、非空 bootstrap 保护通过；
专项覆盖 V316 旧结构升级、重复执行、缺少前置版本、第二账本失败回滚、部分 DDL
恢复及中文 HEX。专项命令：python -B script/sql/mysql/tools/test_registration_schema.py。
test_fresh 现会在迁移完成后比对实际结构，防止只检查数据快照而漏过此类问题。
测试服务器当前数据库经全部模块只读结构比对无 drift，V317/V318 双账本存在；未写入该库。
全模块 fresh 验收未在本次重跑，既有 PMS 前置依赖限制不由 Core 链通过而解除。

数据级 WARN 与结构 drift 分开处理：业务字典非空可符合投入使用后的状态；
菜单、模板、筛选和默认路由需核对管理员配置。编号格式/重复、角色菜单重复和
联系人认领完整性需单独审计；本修复没有抑制告警，也不清理业务数据或权限。

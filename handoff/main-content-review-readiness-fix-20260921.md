# Workstream: main
- Goal: 修复生产内容批审提交报"生产内容审核配置尚未完成或已失效"。
- Branch: main
- Beijing time: 2026-09-21

## 根因

`ACT_RE_PROCDEF` 中 `zsjos_production_content_review` 生效版本（v2，2026-09-20 14:17 部署）
的两个 userTask 编码是设计器自动生成的 `Activity_<uuid>`，名称仍是默认"审批人"。
`ContentReviewConfigService.validTaskSequence` 要求编码恰为 `directorReview` + `finalReview`，
因此 `requireReadyConfig` 在第二次判定处抛 `CONTENT_REVIEW_CONFIG_INVALID`（1_900_020_020）。

该定义由 `model_id f8072ddb` 复制而来——同一模型先后以 `zsjos_material`(v1) 和
`zsjos_content_review`(v2) 两个分类部署，说明是复制素材审批模型后仅改流程标识。

其余六项判定均正常：配置行存在且映射非空、`production_content` 素材类型 status=0 且
`allow_auto_collect=TRUE`、扩展表 category=`zsjos_content_review`、无在途实例。

## 变更

生产库 `zsjos`：

1. 以应用自身 `SimpleModelUtils`/`BpmnModelUtils` 从修正后的 simpleModel 重新生成 BPMN，
   经 Flowable `RepositoryService` 部署为版本 4（`f656e6cb-b599-11f1-bb3f-c2a437d6307f`），
   节点编码改为 `directorReview`（编导审核）/ `finalReview`（终审）；保留租户原有按钮、
   字段权限、超时等配置，仅改编码与名称。
2. 挂起 v1、v2；`ACT_RE_MODEL f8072ddb` 指向新部署。
3. 扩展表新增一行 `process_definition_id=f656e6cb...`，category=`zsjos_content_review`，
   表单/管理员/触发器等字段照抄旧行。
4. 逻辑删除 17 条回滚残留的 DRAFT 批次及其 17 条 item（`deleted=b'1'`）。
   误建的 v3/v4 中间状态已清理：误部署版本经 `deleteDeployment` 删除，无孤儿行。

仓库：`docs/operations/zsjos-bpm-versioned-assets.md` 补充发布后节点编码复核步骤。

## 验证

- 克隆库 `zsjos_crfix`（生产快照全量还原）先跑通全流程，确认校验由 FAIL 转 PASS。
- 生产 v4 的 BPMN 解析结果：`StartUserNode → directorReview → finalReview → End`，
  两节点 `SINGLE`，`validTaskSequence = PASS`。
- 真实流量：16:55 运营提交批次 18（`CRB-25B5A070EF8B467E`）成功，流程实例
  `0004c8dbfe954a66a6778bc2982a44f6` 于 16:58 流转到 `finalReview`，审批人解析为角色
  3031（董事长）下的用户 12。

## 回退

- `/opt/zsjos-runtime/backups/content-review-model-20260921/model-backup.sql`
  （ACT_RE_MODEL/DEPLOYMENT/PROCDEF/GE_BYTEARRAY/bpm_process_definition_info）
- `/opt/zsjos-runtime/backups/content-review-model-20260921/draft-batches-backup.sql`
- 回退方式：挂起 v4、将 v2 置为 `SUSPENSION_STATE_=1`、`ACT_RE_MODEL` 指回原部署。

## 遗留

- 终审绑定角色 3031（董事长）为文档允许的过渡方案；总监角色补齐后应改绑并重新发布。
- v1、v2 保留为已挂起的历史定义，未删除。
- `script/bpm/validate_manifest.py` 在 `zsjos_sales_order_dual_approval/1.0.0/process.bpmn20.xml`
  报 checksum mismatch，属既有问题，与本次无关，未修改。

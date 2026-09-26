# 爆款拆解审核人改绑程伟 — 变更记录 2026-09-26

## 目标

把「爆款账号拆解」「爆款内容拆解」两个审批流的审核人固定为程伟老师（`system_users.id=12`），
取代原来的「发起人部门负责人」策略。

## 变更前的实际状态（与文档/资产库均不一致，需留存）

`docs/operations/viral-material-review-deployment.md` 要求审核节点用「角色」策略绑董事长；
`script/bpm/**/process-model.json` 里写的也是 `candidateStrategy=10`、节点 id `viralReview`。
但线上真正生效的是管理员手工建模发布的版本，两者都对不上：

| | 资产库 `script/bpm/` | 线上 v1（变更前） |
|---|---|---|
| 审核节点 id | `viralReview` | `Activity_0d6b214f…`（账号）/ `Activity_fb10be5a…`（内容） |
| 节点名称 | 爆款审核 | 审批人（设计器默认） |
| 审批人来源 | 10 角色 | **37 发起人部门负责人**，param `1` |

走策略 37 时审核人 = 发起人所在部门的第一级负责人。编导邓穆祺/王文欣在 1013 新媒体三部
（负责人 12 程伟），栗伊琳在 1011 新媒体一部（负责人 20 陈薇）—— 即**并非所有编导的爆款
拆解都审到程伟**，这正是本次要修的问题。

## 变更内容

通过 Admin API（`PUT /admin-api/bpm/model/update` + `POST /admin-api/bpm/model/deploy`）修改，
**未重新编译**，未手改数据库，未使用 standalone Flowable driver。

审核节点仅改四个字段，其余（`approveMethod`、`reasonRequire`、`timeoutHandler`、`fieldsPermission`、
`buttonsSetting`、`rejectHandler` 等）原样保留：

| 字段 | 变更前 | 变更后 |
|---|---|---|
| `name` | 审批人 | 爆款审核 |
| `candidateStrategy` | 37（发起人部门负责人） | **30（用户）** |
| `candidateParam` | `"1"`（部门层级） | **`"12"`（程伟的 AdminUserDO id）** |
| `showText` | 发起人的部门负责人 | 程伟老师 |

选策略 30 而非 10（角色）的原因：角色策略虽可直接绑董事长角色，但角色成员会随人事变动而漂移，
且多环境部署依赖各环境自行维护角色成员。策略 30 直指用户 id，审核人身份确定、不随角色/部门变动。

> 曾担心角色策略会把停用账号纳入候选人形成死待办（`system_user_role` 里董事长角色 3031 挂着一个
> 已停用的程伟账号 `id=11, status=1`）。**经核对此担忧不成立**：角色策略最终走
> `PermissionServiceImpl.getUserRoleIdListByRoleId` → `UserRoleMapper.selectList`，而 `UserRoleDO`
> 继承 `BaseDO` 带逻辑删除，MyBatis-Plus 会过滤 `deleted`；id 11 那行已被逻辑删除，不会成为候选人。
> 因此策略 10 在租户 1 同样是可用的安全选项，选 30 是出于确定性与可移植性的取舍，不是规避缺陷。

> 注意 id 12 与 id 11 同名「程伟」，另有 id 260「新媒体三部主管【程伟老师】」。本次绑的是 **12**
> （`username=ChengWei`，`status=0` 启用）。

## 变更结果

| 流程 | 模型 id | 新流程定义 id | 版本 | 状态 |
|---|---|---|---|---|
| zsjos_viral_account_review | `2be5a425-b388-11f1-a04c-d2ce0d52b6f7` | `8f4039db-b971-11f1-9ab5-8e534d5dae38` | v2 | 启用 |
| zsjos_viral_content_review | `69eed618-b388-11f1-a04c-d2ce0d52b6f7` | `8f5ce99f-b971-11f1-9ab5-8e534d5dae38` | v2 | 启用 |

旧 v1（`5d09c994…` / `5b767f60…`）已自动挂起，在途实例保留原定义继续走完，未迁移、未重写。

部署结果核对：
- `ACT_RE_PROCDEF.CATEGORY_` = `zsjos_material`（走 API 部署保留了分类，没有 standalone 驱动
  那个 category 不落盘的问题，因此**不需要** SQL 兜底）。
- `bpm_process_definition_info` 扩展行 id 33/34 自动生成，category、model_id 均正确。
- BPMN 中审核节点 `candidateStrategy=30`、`candidateParam=12`、节点名「爆款审核」，
  `StartUserNode` 仍为 36（发起人自己）。
- `multiInstanceLoopCharacteristics` 保留（账号为或签，内容为按比例会签）。

## 端到端验证（真实提交，非构造）

变更发布后 14:14:46，编导栗伊琳（`user_id=31`，**属于 1011 新媒体一部，负责人是陈薇**）
提交了素材 `MAT-DF2CE07F135442E7`（version 16）—— 这条以前会审到陈薇，是本次改动的关键用例。

结果：
- 流程实例 `a182cfd25a8a4b1f82c5bebcc2c01d7d` 跑在**新定义 v2** 上；
- `ACT_RU_TASK` 生成待办，`NAME_=爆款审核`，**`ASSIGNEE_=12`（程伟）**；
- `zsjos_material_approval_round.id=31` 记录 `process_definition_version=2`。

即：新提交已正确路由到程伟，且不依赖部门层级。

## 回滚

备份在 `tools/viral-bpm-20260926/`：

- `model-export-account.json` / `model-export-content.json` — 变更前的完整模型（含旧节点树），
  可经 `PUT /bpm/model/update` 原样写回，再 `POST /bpm/model/deploy` 发布 v3 回退。
- `deployed-v2-{account,content}.bpmn` — 本次部署产物的 BPMN，留档比对。
- `rebind_approver.py` — 本次使用的脚本，默认 dry-run，`--apply --token <t>` 才写。

回滚时同样应发布**新版本**而不是删定义；旧 v1 与在途实例不要动。

## 资产库与文档对齐（2026-09-26 后续完成，方案 A）

资产库取"可移植模板"口径，**不**如实记录租户 1 的用户绑定 —— 因为校验器不允许：

```python
# script/bpm/validate_manifest.py:151-160
allowed = {35, 60}
if asset.get("requiresTenantRoleConfiguration") is True:
    allowed.add(10)
```

策略 30（指定用户）被明确排除，理由正当：资产是跨环境复用的，写死用户编号搬到新环境即悬空引用。
先例是 `zsjos_production_content_review` 1.1.0 —— 终审节点从 35 改成 `10 + "0"` 占位符 +
`requiresTenantRoleConfiguration: true`，并在 `description` 里写明导入后需在 BPM 绑定角色。

本次照此办理：

- 新增 `script/bpm/zsjos_viral_account_review/1.1.0/process-model.json`（sha256 `c41691f2…`）
- 新增 `script/bpm/zsjos_viral_content_review/1.1.0/process-model.json`（sha256 `f73a6d0c…`）
- 两资产的审核节点 id 对齐线上（`Activity_0d6b214f…` / `Activity_fb10be5a…`，替换占位的 `viralReview`），
  `candidateStrategy=10`、`candidateParam="0"`、`requiresTenantRoleConfiguration: true`
- `manifest.json`：`1.0.0` 的 `recommended` 置 false（已发布资产冻结，只允许改这一项），
  插入 `1.1.0` 条目并置 `recommended: true`；资产总数 28 → 30
- `docs/operations/viral-material-review-deployment.md`：第 2/3/4/8 步更新，并新增说明块点明
  **资产是角色模板、租户 1 线上是用户绑定，两者口径不同不可直接比对**

校验结果：4 个 viral 资产全部通过 `validate_asset`。全量 `validate_manifest.py` 仍失败，
但失败项均为**既有**问题，与本次改动无关：

- `zsjos_sales_order_dual_approval/1.0.0/process.bpmn20.xml` checksum 不匹配
- `zsjos_media_account_delete@1.0.0` assetFormat 为 `xml`，不在支持集合 `{bpmn, simple}` 内
- 10 个未在 manifest 登记的 `1.1.0/process-model.json`（改动前为 12 个；本次登记掉其中 2 个）

已用 stash 还原 manifest 验证：以上三项在基线（无本次改动）下同样存在。CI 的
`zsjos-bpm-assets.yml` 会跑该校验，因此**这条流水线在我们动手前就是红的**，需要单独处理。

## 遗留

1. **全量 manifest 校验在基线上即失败**（见上），含 sales_order checksum 与 media_account_delete
   格式两处既有破损，以及 10 个未登记资产。本次未处理，以免把无关修复混进这次改动。
2. **部署文档已对齐**到"资产按角色、租户 1 按用户"的双口径，但若日后决定把租户 1 也改回角色绑定，
   文档第 2 步的"角色里残留停用账号不构成死待办"这一句仍成立（角色策略会过滤逻辑删除行）。
3. **多人审批方式仍不一致**：账号拆解 `approveMethod=3`（或签），内容拆解 `approveMethod=2`
   （按 100% 比例会签）。当前审核节点只有一人，该差异不产生实际影响；若日后扩回多人需留意。
   本次资产 1.1.0 沿用原值未统一，以免引入未经验证的行为变更。

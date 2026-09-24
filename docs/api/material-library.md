# 素材库 API

素材库以“素材主记录 + 不可变内容版本”保存内容包。模板版本发布后不可修改，素材提交审批时冻结字段定义、字典标签快照、文件引用和 BPM 定义版本。

## 核心接口

- `GET /admin-api/zsjos/material-types`：查询当前租户可用的素材类型和已发布模板版本。
- `POST /admin-api/zsjos/material-types`、`PUT /admin-api/zsjos/material-types/{id}`：配置素材类型；模板变更必须发布新版本。
- `GET /admin-api/zsjos/materials`、`GET /admin-api/zsjos/materials/{id}`：分页搜索和查看素材及有效版本。
- `POST /admin-api/zsjos/materials`、`PUT /admin-api/zsjos/materials/{id}`：按当前模板创建或编辑草稿。
- `POST /admin-api/zsjos/materials/{id}/submit`：提交当前草稿审批；旧有效版本继续可用。
- `POST /admin-api/zsjos/materials/{id}/disable`、`POST /admin-api/zsjos/materials/{id}/restore`：停用或恢复素材。
- `GET /admin-api/zsjos/material/page?recommendation=true&accountId={id}`：按账号画像返回确定性匹配结果。推荐维度由模板字段字典自动归属（`zsjos_persona_type`→账号类型、`zsjos_material_profession`→专业方向、`zsjos_media_account_stage`→账号时期），类型级推荐配置只决定启用哪些维度与返回数量，实际生效维度为类型配置与模板字段的交集。
- `POST /admin-api/zsjos/materials/{id}/like`、`DELETE /admin-api/zsjos/materials/{id}/like`：用户级可切换点赞。
- `POST /admin-api/zsjos/materials/{id}/favorite`、`DELETE /admin-api/zsjos/materials/{id}/favorite`：用户级可切换收藏。
- `POST /admin-api/zsjos/materials/{id}/references`：按 `materialVersionId` 复制字段到指定生产内容版本；必须携带幂等键。

引用成功写入生产内容草稿后才增加被调用量。同一素材版本对同一生产内容版本只计一次；重试时必须复用原幂等键，参数不一致会被拒绝。

字典字段保存 value、类型和选择时的 label 快照，历史详情不重新解析当前字典。文件通过 Infra 预签名直传，再由服务端确认对象元数据。

Infra S3 的两个 PUT 预签名入口均使用存储配置的 `endpoint`，由 SDK 按桶名和
path-style 配置生成上传地址；`endpoint` 必须能被上传浏览器访问，并允许站点来源、PUT
及上传请求头的 CORS 预检。自定义 `domain` 继续用于文件访问，公开读取 URL 和私有 GET
签名保持原有逻辑。修复上传不需要改写 `domain` 或历史文件链接；部署后应验证初始化、
浏览器 PUT、上传确认与保存后预览的完整流程。

Workbench provides direct creation pages for `viral_account` and `viral_content` under the server-owned
`/zsjos` menu. Both use `zsjos:material:create`; the browse page also exposes the same forms. The stable
`viral_content` type is displayed as “爆款内容” and its `zsjos_viral_content_type` selections retain their
dictionary value/type/label snapshots. Material browsing remains server-paged but appends pages through
infinite scrolling; no alternate material API or authorization source is introduced.

## 爆款账号拆解

素材类型编码为 `viral_account`。页面左侧账号主页截图是固定封面列，模板字段按 `sort`
依次进入账号详情、编导拆解、搭建建议三个分区；Workbench 只合并相邻且同名的分组。
缺少分区、分区值为字符串 `"null"` 或分区未知的字段不会静默丢弃，而是显示在“模板待完善”区域。

账号名称自动作为素材标题；草稿保存只校验已填写值的格式、HTTPS 链接、字典值和文件引用，提交审批时才校验全部必填字段、两个重复组至少一行以及主页截图。内容矩阵和 S6 稳定增长期均为可增删排序的重复字段组。

V208 以确认的 V2 字段内容发布不可变 V3：36 个一级字段按账号详情、编导拆解、搭建建议
各 12 个排列；`build_notify` 位于编导拆解末尾，S1-S6 使用稳定键
`s1_stage_plan` 至 `s6_stage_plan` 并直接按顺序显示在搭建建议中。迁移只升级空模板及两个
已知应用模板哈希，遇到未知租户自定义哈希保持原模板和当前版本指针不变。

该类型暂不绑定 BPM 流程定义。后台发布流程后，通过素材类型配置绑定流程 Key；编导审核、总监审核、节点顺序、退回和驳回均由 BPM 模型管理，业务代码只消费通用流程状态事件。


### 拆解草稿恢复（2026-09-21）

中世健员工工作台的爆款账号与爆款内容拆解入口先读取当前用户、当前素材类型的草稿，分页读取完整后供用户选择继续填写；新建必须显式点击“新建拆解”。草稿读取失败显示错误并可刷新重试，不将失败当作空列表。编辑入口按服务端 UPDATE 动作控制，后端继续独立授权。

保存草稿保留当前编辑器与已创建记录标识，不清空表单；再次进入可从草稿选择器恢复。切换记录、新建或关闭时若存在未保存修改，会提示确认。编辑 DRAFT 使用该草稿 currentVersion.fields，查看版本使用版本字段；仅新建及从非草稿生成新版本使用当前发布模板，和后端模板选择保持一致。未修改旧字段随 values 一并保存。

此次不修改业务权限、数据库记录、已发布模板或草稿必填契约；不会自动修复此前已丢失的字段值。

## 当前租户管理员读取

当前租户管理员可读取他人草稿、历史版本及素材审批历史。列表保持 mine、收藏、状态等用户筛选；响应选取草稿不改变 availableActions 的原始管理判断。推荐账号读取扩大，内容引用等生产操作候选保留原规则。编辑、提交、停用、恢复与审批不继承读取能力。

## 素材浏览与个人审核进度（2026-09-24）

Workbench 浏览页采用响应式封面卡片；“我的素材”采用紧凑摘要卡，展示当前修订版本状态、当前审核人、最近提交时间和驳回原因。主页、最火作品及重复组中的 HTTPS 链接统一使用 ResourceLink 展示、复制与安全打开。

`GET /admin-api/zsjos/material/page?mine=true&versionStatus=IN_APPROVAL` 按当前草稿版本（不存在时按生效版本）筛选，保留本人、租户、逻辑删除限制。`versionStatus` 可取 DRAFT、IN_APPROVAL、EFFECTIVE、REJECTED，仅在 mine=true 时生效；原有 status 仍筛选素材主状态，两者不互相替代。主记录为 EFFECTIVE 的在审修订也能进入个人审批中列表。流程没有独立的“已读／开始处理”状态，因此 IN_APPROVAL 展示为“待审核／审批中”。

版本响应新增 `pendingApproverNames: string[]`，仅审批中通过 BPM 公共任务 API 获取实际当前指派人（含加签），姓名通过 System 公共 API 解析；不按部门名称、角色或管理权限推断。未分配／已不可用人员明确显示占位信息，已结束版本为空数组；此字段不构成审批权限或历史审核人快照。原有素材和版本读取授权继续适用。

已通过记录有 UPDATE 动作及 `zsjos:material:update` 时展示“修改并重新送审”；复用 `PUT /admin-api/zsjos/material/{id}` 创建修订草稿，再以 `zsjos:material:submit` 调用 `POST /admin-api/zsjos/material/{id}/submit`。旧生效版本直到新版本通过前保持可用。

审批中仅当前版本的提交人且具有 `bpm:process-instance:cancel` 时返回 CANCEL 动作。Workbench 同时检查动作和权限，填写原因后调用既有 `DELETE /admin-api/bpm/process-instance/cancel-by-start-user`（JSON `{id, reason}`）；BPM 独立校验真实发起人、运行状态及流程是否允许撤回，拒绝原因原样反馈，不自动调整配置或授权。该入口不是管理员代撤回。

新的 BPM CANCEL 事件将版本恢复 DRAFT，主记录无生效版本时恢复 DRAFT、有生效版本时保持 EFFECTIVE；审批轮次记为 CANCELLED，并保留撤回原因和结束时间。重复事件无重复变更；旧审批轮次和历史取消记录不回写。重新提交创建新轮次。Admin 消费的原有字段、状态枚举及接口均保留；新增字段可忽略，Admin 未增加员工端专用按钮。本次不修改审批人策略、菜单授权或 SQL。

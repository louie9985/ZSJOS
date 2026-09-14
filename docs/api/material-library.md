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

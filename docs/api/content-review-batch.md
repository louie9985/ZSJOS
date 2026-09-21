# 生产内容批审 API

生产内容批审以账号为边界组成批次，一个批次只能包含同一账号的 1 至 20 条内容版本。提交后冻结账号、运营、责任编导关系快照和审核上下文。

## 核心接口

- `POST /admin-api/zsjos/content-review/batches`：创建批次并校验条数、账号一致性和内容版本状态。
- `POST /admin-api/zsjos/content-review/batches/{id}/submit`：提交 BPM 审批实例。
- `PUT /admin-api/zsjos/content-review/batches/{id}/items/{itemId}/director-decision`：编导逐条暂存通过或退回。
- `POST /admin-api/zsjos/content-review/batches/{id}/director-complete`：编导完成本轮，要求所有条目已有结论。
- `PUT /admin-api/zsjos/content-review/batches/{id}/items/{itemId}/final-decision`：总监逐条暂存结论并选择是否收录素材库。
- `POST /admin-api/zsjos/content-review/batches/{id}/final-complete`：总监完成整批结论并统一落地。
- `POST /admin-api/zsjos/content-review/items/{itemId}/publish`：登记实际发布平台链接和时间。

总监完成全部条目结论后，批次内通过项统一进入待发布，收录项在同一事务中生成“生产内容”素材；退回项退出本轮。收录字段无法从内容、账号或系统默认值映射时，仅禁止该条收录，不阻止批审完成。BPM 回调、重复提交和重复收录均按业务键与事件号幂等处理。

## 运营审核附件（2026-09-21）

每件作品可附加最多 20 个审核附件，封面另计 1 张。附件随内容版本绑定，复用
`deliverableSnapshotJson` 和 `zsjos_content_version_file` 的 `deliverable` 字段分类；不新增表。
支持图片、视频、PDF、Word、Excel、PPT；单文件非空且不超过 1GB。
上传初始化和版本绑定使用相同 MIME 白名单，封面绑定仍只接受图片。

工作台学员页新建、审核页草稿编辑和驳回修订均使用相同附件输入：文件多选、拖拽、
附件区域 Ctrl/Cmd+V 粘贴图片，以及“粘贴截图”按钮。快捷键只处理当前附件区域的图片事件，
不拦截正文文字粘贴。按钮读取依赖浏览器安全上下文与剪贴板权限，失败显示原因并保留文件选择入口。
附件选择后本地预览，保存草稿/提交时调用内容版本的 `/file/upload/init`、`/file/upload/complete`
完成上传。任一文件失败不发送业务保存命令，表单保留成功引用及失败项，再次保存仅重试未完成文件。

实际工作台入口使用以下 POST 接口（均以 `/admin-api/zsjos/content-review` 为前缀）：

- `/batch/create-from-student`：新建，`works[].deliverableSnapshotJson` 传文件 ID 数组的 JSON 字符串。
- `/batch/{batchId}/save-student-draft`：保存新草稿轮次，保留旧轮次供审计。
- `/batch/{batchId}/resubmit-from-student`：驳回后生成新草稿轮次；保存后仍需点击“提交审批”。

修订时省略 `deliverableSnapshotJson`（或 null）继承原版本附件；传 `"[]"` 显式清空；
传非空 ID 数组替换当前新版本附件。清空不删除文件对象或修改历史审批版本。
编辑器回填原版本文件 ID、名称、类型、大小及服务端预览地址；草稿保存走新作品版本，
驳回修订带 `sourceContentId/sourceVersionId` 走原作品后续版本。
文件绑定继续校验当前用户上传归属、租户及既有版本继承关系，业务保存继续执行原权限和状态校验。

审核详情按作品显示“审核附件”；BPM 沿用现有附件投影，无新增审批数据或协议。
Vue 管理端没有对应运营提交页面，本次不新增页面；既有 BPM 附件字段结构不变。
UI 使用工作台主题 token、文件类型图标和图片放大预览，窄屏切换单列并保留底部保存操作。

### 素材自动收录边界

现有生产内容模板的 `deliverable_files` 是视频字段，自动收录映射仍读取
`deliverableFileIds`。若审核附件包含图片或文档，勾选收录可能触发既有“字段映射或文件快照无效”
校验；不勾选收录的普通审批不受影响。本次不改变素材模板、管理员映射或静默丢弃非视频附件。
支持混合附件自动收录需要另行明确素材字段归属并调整对应模板/映射。

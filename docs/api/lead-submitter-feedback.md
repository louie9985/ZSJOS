# 客资销售反馈

## 行为与边界

销售在 Workbench 客资详情的“销售反馈”中回复提交人。表单只有“反馈”（必填多行文本，最多 5000 字符）和“附件”（可选，最多 20 个，每个 20 MiB）。
接受 JPEG/PNG/WebP、PDF、纯文本、Word 和 Excel，服务端按文件内容识别类型。
记录只追加，不编辑、撤回或删除，不进入通用反馈工单或 BPM，不改变客资主状态、跟进时间和订单状态。
当前正式负责人才能发送；无效、关闭和已成交客资拒绝新增。提交成功递增 Lead version，用于检测与其他客资命令的冲突。

提交方使用 Lead 的 providerOwnerType/providerOwnerId 权威归属。员工保存 ADMIN 用户快照，兼职保存 Partner 与 Partner Account 两个独立标识。
当前销售可读该客资反馈；员工提交人只能读取接收人是自己的记录；Partner 必须同时满足启用账号、当前 Partner 归属和记录接收账号匹配。
新销售接手后可读历史反馈，原销售不因曾经发过反馈而保留读取权。姓名保留历史快照，响应仍执行客资现有身份脱敏契约。

## API

ADMIN 前缀为 /admin-api，Partner 前缀为 /part-api；以下路径均不含前缀。

| 方法与路径 | 契约 |
| --- | --- |
| GET /zsjos/lead/{leadId}/submitter-feedback/page | ADMIN/Partner；pageNo、pageSize；返回 CommonResult<PageResult<LeadSubmitterFeedbackRespVO>> |
| POST /zsjos/lead/{leadId}/submitter-feedback | ADMIN；feedback、attachmentIds、version、idempotencyKey；返回反馈记录 ID |
| POST /zsjos/lead/{leadId}/submitter-feedback/attachment/upload | ADMIN；multipart file；返回 fileId、originalName、contentType、fileSize、url |

列表按 ID 倒序，响应包括 id、feedback、salesName、submitterName、createTime、attachments。
附件响应的读取 URL 有效期为 600 秒，刷新记录重新签名；文件失效显示“文件不可用”，不制造下载地址。
上传时在 ZSJOS 记录租户、Lead、上传人和 24 小时临时有效期。发送时校验 Infra creator、包含租户/Lead/上传人的目录、有效期、未绑定状态和文件类型。
绑定后不受临时有效期影响，业务表不保存短期签名 URL。取消表单不会主动删除 Infra 文件，过期临时记录不能再绑定；本期不新增自动清理任务。
同一租户、Lead、销售、idempotencyKey 构成发送意图；同一内容、附件顺序和请求版本重试返回原 ID，变化则返回冲突。前端提交结果不明时保留该键与版本。

| 错误码 | 含义 |
| --- | --- |
| 1900003120 | 客资状态不允许反馈 |
| 1900003121 | Lead version 冲突，刷新客资后重试 |
| 1900003122 | 同一发送意图内容不一致 |
| 1900003123 | 附件无效、已过期、已绑定或归属不符 |
| 1900003124 | 缺少可用接收人 |

对象不存在及无权访问复用 Lead 稳定错误。Controller 功能权限与 Service 对象权限累积执行，Partner 不借用 ADMIN 角色授权。

## 权限与通知

服务端菜单按钮权限为 zsjos:lead:submitter-feedback:read 和 zsjos:lead:submitter-feedback:create。
销售使用页面需已有客资读取入口以及这两个权限；提交人只需已有客资入口和反馈读取权限。V182 创建菜单，不改变现有账号或角色授权。
Lead visibleTabs 投影增加 submitter-feedback，availableActions 增加 REPLY_SUBMITTER；客户端不按角色名推断。

zsjos.lead.submitter_feedback_created 通过已有 System Outbox 发布，和反馈记录处于同一事务。Outbox 写入失败回滚；提交后投递失败由 System 重试。
界面“已提交”指业务记录成功，不能代表对方已读或某个外部渠道送达。禁用通知规则时仍可保存并在详情查看。
事件冻结接收主体类型/账号；通知只放 leadNo 和最多 200 字符摘要，完整内容与附件在授权详情中查看。
Workbench 消息进入销售反馈页签，Partner 消息进入客资详情的反馈区。铃铛、未读和 WebSocket 沿用原有通知机制。
原“催促”逻辑不改动，仍由 zsjos.lead.submitter_urged 通知催促时的当前销售。

2026-09-06 对当前开发库的只读核查显示，催促场景已有启用的 in_app 和 wecom 规则，
模板均启用、接收角色为 owner、动作为 business_detail；这是配置证据，不代表已完成真实送达验证。

## 数据库交付

V182 在 V181 后执行；fresh baseline 与 schema/core.sql 包含两张空反馈表，bootstrap 继续按顺序执行 V182 幂等创建权限和通知默认项。
SQL 使用 SET NAMES utf8mb4，需使用 utf8mb4 客户端。范围为两张空表、两个权限菜单、一个模板、每个现有租户一条站内规则和版本登记。
不会写入反馈样例、真实账号授权或业务字典；新租户需使用通知管理配置该场景。
回滚以禁用入口和通知为主，保留记录、附件和版本，不删除业务历史。
执行 script/sql/mysql/verify-lead-submitter-feedback.sql 检查结构、关系、权限、通知，并检查中文 HEX。

## 本次验证结果（2026-09-06）

- 后端定向测试 26 项通过，覆盖服务命令、附件、对象与功能权限及员工/Partner 通知接收人；应用服务 package 通过。
- Workbench 相关测试 33 项、类型检查、生产构建及 H5 类型检查/生产构建通过。
- V182 在按开发库真实结构创建的独立临时库 zlf182b 连续执行两次，验证脚本全部 PASS，两个中文权限名称 HEX 正确。
- zlf182c fresh baseline 的两张反馈表与 zlf182b 升级结果完成 SHOW CREATE TABLE 完整比对，列、索引和表属性一致。
- 全量后端测试被 Infra CodegenEngineUniappTest.testExecute_treeSearch 失败阻断；LeadManagementServiceImplTest 中现有协助请求动作断言与工作区权限实现不一致。
- Workbench 全量测试 541 项通过、5 项失败：已有 feedback.css 的固定字号约束，以及媒体学员源码守护的单引号匹配。
- 完整 fresh bootstrap 被 V158 菜单 ID 79913 冲突阻断；zsjos-db check 被两份公告接收人表定义的既有排版差异阻断。没有改写这些历史行为。
- 当前浏览器无已登录会话，真实发送、铃铛/未读刷新、附件下载和桌面/移动 UI 未验证。
- V182 尚未同步至现有开发库，未授权真实账号、未重启服务。上述全量检查和启用步骤完成前，不视为可直接上线。

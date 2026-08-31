# ZSJOS 强制表单

强制表单由 ZSJOS 拥有业务定义、版本、批次、接收人快照、提交答案、字典快照和附件绑定；System 继续作为用户、部门、岗位和字典来源，Infra 继续作为文件来源。该能力不接入 BPM、公告已读或普通任务体系。

## 业务规则

- 表单状态为 `DRAFT`、`PUBLISHED`、`WITHDRAWN`。
- 发布会创建新的 `zsjos_forced_form_version`，版本保存字段 JSON、schema hash、发布人和发布时间；已发布版本只读，不在原版本上改字段。
- 发送会基于指定版本创建 `zsjos_forced_form_batch`，解析范围后写入 `zsjos_forced_form_recipient`，并冻结用户昵称、部门、岗位和来源快照。
- 同一租户同一用户对同一表单只完成一次；新批次或新版本不会再次要求已完成用户填写。
- 提交时保存字段快照、答案 JSON、字典 label 快照和平台。历史详情、审计和导出使用提交时的快照 label，不重新读取当前字典名称。
- 附件先临时上传获得 upload token，提交成功后绑定到 `zsjos_forced_form_submission_file`；未绑定或过期文件不得进入历史提交记录。

## 字段契约

字段数组只允许以下类型：

- `text`
- `textarea`
- `radio`
- `multi-select`
- `checkbox`
- `attachment`

服务端在保存、发布和提交时都会校验字段：

- `key` 必须匹配 `[a-z][a-z0-9_]{0,63}`，同一表单内不可重复。
- `label` 非空，`type` 必须在白名单内。
- `radio`、`multi-select` 必须配置有效 `dictType`，生产选项不得来自静态 `options`。
- 文本长度、附件数量、附件大小和扩展名限制必须合法。
- 提交答案不能包含 schema 外字段；必填字段不能为空；必填 checkbox 必须为 `true`。
- 字典值必须是当前有效字典值，失败统一返回 `FORCED_FORM_DICT_INVALID`。

## 管理端接口

路径均挂在 `/admin-api` 下，Controller 注解只声明 `/zsjos/forced-form`。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/zsjos/forced-form/page` | `zsjos:forced-form:query` | 表单分页，返回状态、版本、接收和完成统计 |
| POST | `/zsjos/forced-form` | `zsjos:forced-form:create` | 创建草稿 |
| GET | `/zsjos/forced-form/{id}` | `zsjos:forced-form:query` | 管理端读取表单定义 |
| PUT | `/zsjos/forced-form/{id}` | `zsjos:forced-form:update` | 修改草稿或可编辑表单 |
| DELETE | `/zsjos/forced-form/{id}` | `zsjos:forced-form:delete` | 删除未进入正式发送链路的表单 |
| POST | `/zsjos/forced-form/{id}/copy` | `zsjos:forced-form:create` | 复制为新草稿 |
| POST | `/zsjos/forced-form/{id}/publish` | `zsjos:forced-form:publish` | 校验字段并生成不可变版本 |
| POST | `/zsjos/forced-form/{id}/withdraw` | `zsjos:forced-form:withdraw` | 撤回发布态表单 |
| POST | `/zsjos/forced-form/{id}/recipient-preview` | `zsjos:forced-form:send` | 按范围预览接收人数量和过滤统计 |
| POST | `/zsjos/forced-form/{id}/send` | `zsjos:forced-form:send` | 创建发送批次和接收人快照 |
| GET | `/zsjos/forced-form/submission/page` | `zsjos:forced-form:submission-query` | 提交记录分页 |
| GET | `/zsjos/forced-form/submission/{id}` | `zsjos:forced-form:submission-read` | 提交详情，展示字段和字典快照 |
| POST | `/zsjos/forced-form/submission/export` | `zsjos:forced-form:submission-export` | 导出提交记录 |

发送范围 `scopeType` 支持 `ALL`、`USERS`、`DEPARTMENTS`、`POSTS`。所有用户、部门、岗位解析通过 System 公开 API 完成；部门发送会展开子部门，发送事务会去重并过滤禁用、删除和非员工主体。

## Workbench 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/zsjos/forced-form/pending` | 查询当前用户待办，按发布时间和 ID 稳定排序 |
| GET | `/zsjos/forced-form/status` | 查询待办摘要，用于请求拦截返回和前端恢复 |
| GET | `/zsjos/forced-form/{id}` | 读取当前用户可见表单摘要 |
| GET | `/zsjos/forced-form/{id}/runtime` | 返回规范化字段、字典选项和附件限制，不暴露后台设计器原始 JSON |
| POST | `/zsjos/forced-form/{id}/attachment/upload` | 上传临时附件并返回 upload token |
| POST | `/zsjos/forced-form/{id}/submit` | 提交答案并绑定附件 |

Workbench 在权限信息成功后查询待办。存在待办时 PC 使用不可关闭 Modal，Mobile 使用不可关闭移动展示；页面刷新、路由切换、401 恢复和 WebSocket 重连后都会重新检查。提交成功后继续查询下一个待办，全部完成才恢复普通业务操作。

## 请求拦截

ZSJOS 在认证完成后增加 Workbench 强制表单 Filter。它只处理带 `X-ZSJOS-Workbench-Platform` 标记的 `/admin-api/**` ADMIN 请求，并且 OAuth client 必须是 `zsjos-pc` 或 `zsjos-mobile`。Vue Admin 不发送该标记，因此不受拦截。

白名单包括登录、刷新、权限信息、登出，以及强制表单 pending/status/runtime/upload/submit 等完成链路接口。普通业务接口在存在待办时返回 HTTP 200 的标准业务 envelope，错误码为 `FORCED_FORM_REQUIRED`，data 为待办摘要；客户端不得把它当作 401 或触发登出。

## 稳定错误码

- `FORCED_FORM_NOT_EXISTS`
- `FORCED_FORM_VERSION_INVALID`
- `FORCED_FORM_FIELD_INVALID`
- `FORCED_FORM_DICT_INVALID`
- `FORCED_FORM_ATTACHMENT_INVALID`
- `FORCED_FORM_SUBMIT_INVALID`
- `FORCED_FORM_REQUIRED`
- `FORCED_FORM_PERMISSION_DENIED`
- `FORCED_FORM_IDEMPOTENCY_CONFLICT`
- `FORCED_FORM_BATCH_INVALID`

## 数据库交付

`V165__zsjos_forced_form.sql` 保留为开发基线骨架；`V166__zsjos_forced_form_formal_model.sql` 是 forward-only 正式模型升级，创建版本、批次、提交附件结构，补齐接收人和提交表字段、租户唯一约束、索引、菜单和按钮权限。`bootstrap.sql` 依次执行 V165、V166。迁移不删除业务数据、不写入本地表单/接收人/提交/文件/测试账号，也不向业务字典写入未经确认的选项。

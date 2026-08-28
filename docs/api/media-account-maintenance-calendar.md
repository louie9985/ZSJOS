# 媒体账号状态维护与日历 API

所有路径使用 `/admin-api` 前缀、标准 `CommonResult` 包装和当前租户上下文。账号对象范围始终由服务端校验。

## 当前状态维护

`PUT /zsjos/media-account/{id}/maintenance` 需要 `zsjos:media-account:maintenance`。请求体：

服务关系指派运营后，服务端在同一事务内同步该学员现有媒体账号的运营负责人；新建账号在存在唯一现任运营时使用该运营作为负责人。账号状态维护仍同时受按钮权限和账号对象关系约束。

```json
{
  "version": 3,
  "currentStatusValue": "a_active_growth",
  "stageValue": "s3",
  "primaryProblemValues": ["b4", "b6"],
  "executionMeasureValue": "content_validation_7d",
  "adjustmentDirection": "收紧选题范围并补强案例证据",
  "startDate": "2026-08-26",
  "endDate": "2026-09-01"
}
```

除 `version` 外的七项维护字段均可为空。日期必须同时为空或同时填写，且 `endDate >= startDate`。字典字段只提交 value；服务端校验当前启用项并保存 label 快照。主要问题按请求顺序去重。标准化后无实际变化时返回当前版本，不更新账号、不增加历史、不发通知。

有变化时，服务端使用乐观锁更新当前快照，写入一条不可变维护版本，并通知更新后的所属编导和运营；收件人去重且排除操作人。返回值为更新后的账号 `version`。

## 历史查询

- `GET /zsjos/media-account/{id}/maintenance-history?pageNo=1&pageSize=20`：需要 `zsjos:media-account:query` 或 `zsjos:media-account:maintenance`，返回完整维护快照、`revisionNo`、`changedFields`、操作人和操作时间。
- `GET /zsjos/media-account/{id}/legacy-stage-history?pageNo=1&pageSize=20`：需要 `zsjos:media-account:query` 或 `zsjos:media-account:maintenance`，只读返回原 S0-S6 阶段日志。

两个接口都叠加账号对象读取权限。账号响应仅在功能权限和对象权限同时通过时返回 `VIEW_ACCOUNT_HISTORY`；Workbench 以该服务端能力决定是否加载和展示历史。维护版本不提供删除或恢复命令，原阶段日志不被改写。

## 日历总览

`GET /zsjos/media-account/calendar` 需要 `zsjos:media-calendar:query`。查询参数：

- 必填：`rangeStart`、`rangeEnd`、`pageNo`、`pageSize`。
- 可选：`keyword`、`currentStatusValue`、`stageValue`、`directorUserId`、`operatorUserId`。

查询使用闭区间相交规则 `startDate <= rangeEnd && endDate >= rangeStart`。普通查询始终限制为当前用户是所属编导或运营的账号；只有同时具备 `zsjos:media-calendar:query-all` 时才取消该对象范围。编导/运营筛选不扩大可见范围。迁移将原 `zsjos:media-account:query-all` 关系同时继承为日历页面查询和查看全部，避免只有范围按钮而无法访问页面。

响应包含 `list`、`total` 和 `unscheduledCount`。`list` 每个账号只返回当前快照区间一次；日期不完整的账号不进入 `list`，但计入当前其他筛选下的 `unscheduledCount`。

## 已移除的旧阶段流转

`POST /zsjos/media-account/{id}/advance-stage` 和 `rollback-stage` 已从运行时 Controller 和 Service 移除，旧客户端请求按标准路由不存在处理（404）。阶段不再通过推进或回退命令改变，只能在上述状态维护接口中作为普通字典字段自由选择。原阶段日志仍通过 `legacy-stage-history` 只读保留。

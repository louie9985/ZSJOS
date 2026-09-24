# 通知渠道管理

管理后台路径：`系统管理 → 通知管理 → 通知渠道`。

接口属于 System 管理 API：

- `GET /admin-api/system/notify-channel/get?channelCode=wecom`
- `PUT /admin-api/system/notify-channel/update`

更新请求只允许启停渠道。企业微信 CorpID、Secret、AgentID 仍由
`系统管理 → 社交通讯 → 社交客户端` 的管理员类型企业微信客户端维护；启用渠道时，服务端会校验客户端已启用、CorpID 以 `ww` 开头、Secret 非空且 AgentID 为数字。

发送对象还必须有企业微信 userid，并开启个人 `wecom_enabled` 推送偏好。渠道开关、业务通知规则和个人接收偏好是三个独立条件。


## 场景注册与启动依赖

`NotifySceneRegistry` 在构造阶段收集 Provider 并读取 `getScenes()`，此时只应读取场景元数据。
订单 Provider 延迟注入 `CashbackService`，实际解析成交结果通知的返现变量时才获取业务服务。
否则返现服务会经高级筛选、财务追溯、对象权限、公海服务和通知规则回到尚未构造完成的注册器；
即使启用循环引用也无法解决该构造阶段闭环。延迟边界保留原有返现查询和 Spring 代理，不改变权限或事务规则。

`NotifyStartupContextTest` 使用真实依赖链及事务、方法安全代理验证不同 Bean 创建顺序和启动后的返现变量解析；
外部数据访问使用 Mock，该测试不替代连接真实基础设施的整机启动验收。

## 业务时效检查

`NotifySceneProvider.deliverySkipReason(event)` 默认返回 null（允许投递）；业务模块可返回
稳定原因码以跳过已失效通知。System 在每个待发企微接收人发送/重试前调用该扩展，
不直接访问业务模块表。返回原因码时逐人状态为 skipped、retryable=false，原因保存在
errorCode；总任务沿用原有成功完成语义，不能据此断言实际收件。查询异常按准备失败重试。
已成功/已跳过的不重发；结果不确定的不盲目重放。

Lead 首次/重新派单实现该扩展；其他场景默认行为不变。参见
[派单契约](zsjos-lead-submission-dispatch.md#企微待接单通知与轮次校验2026-09-24)。


## 按规则判断业务适用性（2026-09-24）

`NotifySceneProvider.evaluateRule(event, recipientRoles, specifiedUserIds)` 默认返回 null，
继续原有处理；业务模块可返回明确 skipped 或 failure。站内信确认投递、企微首次准备及
已冻结接收人的重试都会检查，不能把全部空接收人结果改成成功。

不适用时 outbox 状态为 `skipped`，`last_error` 保存稳定原因码、不增加失败次数、清除租约，
不再进入 pending/processing 扫描。`succeeded_at` 此时表示终止处理时间，不表示实际收件；
跳过记录与成功记录采用相同 30 天保留周期。原有历史失败记录不回写、不重放。
`GET /system/notify-rule/delivery-page` 的 status 筛选新增 `skipped`，响应 errorCode 返回跳过原因。
状态字段原为 varchar，无 schema 变更。Admin/Workbench 当前均没有该投递查询接口消费者。

来源关联仅对 `zsjos.lead.created` 下“只有 new_media_provider 角色、没有指定用户”的规则进行
整条适用性判定，双渠道一致；混合角色或指定用户规则保留管理员配置的收件人并集。
普通新媒体/兼职、不选择提供方、提供方等于提交人分别跳过；自拓历史选择标记缺失、
来源无法识别、选择与归属不一致或缺少实际提交人作为具体、不可自动修复的失败处理，不重试。
查询临时异常不算业务跳过；其他适用规则仍为空接收人时继续返回可重试的
`NOTIFY_RECIPIENT_MISSING`。跳过与异常码见派单诊断记录。

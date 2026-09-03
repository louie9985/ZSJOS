# 通知渠道管理

管理后台路径：`系统管理 → 通知管理 → 通知渠道`。

接口属于 System 管理 API：

- `GET /admin-api/system/notify-channel/get?channelCode=wecom`
- `PUT /admin-api/system/notify-channel/update`

更新请求只允许启停渠道。企业微信 CorpID、Secret、AgentID 仍由
`系统管理 → 社交通讯 → 社交客户端` 的管理员类型企业微信客户端维护；启用渠道时，服务端会校验客户端已启用、CorpID 以 `ww` 开头、Secret 非空且 AgentID 为数字。

发送对象还必须有企业微信 userid，并开启个人 `wecom_enabled` 推送偏好。渠道开关、业务通知规则和个人接收偏好是三个独立条件。

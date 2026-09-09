# 客资与学员头像

Workbench 的客资和学员业务主体统一使用 `SubjectAvatar`。风格由共享包中的稳定哈希
`selectAvatarVariant` 在四种风格中确定性选择，生成规则固定为 Avatar Kit v1。

业务页面必须使用稳定业务编号作为 `seed`：客资使用 `leadNo`，学员使用 `personNo`。只有姓名或缺少业务编号时使用统一匿名头像，不得使用内部 ID、姓名、列表下标、订单号或时间戳作为 seed。

```tsx
<SubjectAvatar seed={lead.leadNo} src={lead.avatar} size={36} label="" />
```

`src` 真实头像优先，加载失败后自动回退为本地生成 SVG data URI。与姓名相邻的头像传 `label=""`，独立头像才传可访问名称。组件的 `variant` 可显式传入 `geometric`、`abstract`、`character` 或 `collection`；未传时根据 `avatar-style-v1`、固定 namespace `zsjos:subject` 和业务编号稳定随机选择。同一编号在客资、学员、列表、详情和审批页面得到相同内容。`version` 默认使用 Avatar Kit v1；需要新增图案时可显式传 `version="v2"`，不会影响现有 v1 头像。主题由 Workbench 主题上下文映射为 `light`/`dark`，不会发起外部头像请求。

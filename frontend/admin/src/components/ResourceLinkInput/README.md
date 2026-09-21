# 中实建通用链接输入

`ResourceLinkInput` 使用 Element Plus 输入框，保留 `v-model` 原文、属性、事件和插槽。合法 HTTP(S) 地址提供新标签页预览；含凭据、控制字符、反斜线或其他协议的值不会成为可点击链接。它不新增业务校验；必填、HTTPS、模板表达式等约束由原表单负责。`previewValue` 可用于协议选择器与地址分开存储的表单，提交值仍由原表单控制。

适用范围：素材模板的 `https-link`（含重复组）、消息和菜单链接、文档和图片 URL、商城跳转地址、接口和回调 HTTP 地址。`AppLinkInput` 在此组件上保留商城链接选择。JDBC、MQTT、WebSocket 技术连接串、OAuth 多值 URI 选择器、上传组件和自动生成的只读分享地址保留专用控件。

Vue 与 React 保持各自框架组件；不新增共享包或依赖。工作台对应组件位于 `frontend/workbench/src/components/ResourceLinkInput.tsx`，复用既有 `ResourceLink`。

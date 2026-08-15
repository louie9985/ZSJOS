# API 约定

## 员工头像

`GET /admin-api/system/auth/get-permission-info` 在 `user.avatar` 返回当前员工个人头像，并在
顶层可选字段 `defaultAvatar` 返回全平台默认员工头像。员工头像固定按
`个人头像 > 默认头像 > 昵称首字` 展示；图片加载失败时继续使用下一层兜底。

员工列表仍由各自接口返回 System 用户的个人 `avatar`。下属销售响应新增从 System 用户 API
透传的 `avatar`。Workbench 不把默认头像写入员工记录，也不直接请求 Infra 参数配置接口。

员工工作台通过 typed service 调用现有 system 和业务接口，不建立通用 `yudao-module-zsjos` 工作台聚合接口。中世健自建业务可以提供聚焦的 `/zsjos/**` 接口，例如客资派单能力，但不能复制 system 或 CRM 已有契约。

- `POST /admin-api/system/auth/login`
- `POST /admin-api/system/auth/logout`
- `POST /admin-api/system/auth/refresh-token?refreshToken=...`
- `GET /admin-api/system/auth/get-permission-info`
- `GET /admin-api/system/notify-message/get-unread-count`
- `GET /admin-api/system/notify-message/get-unread-list`
- `GET /admin-api/system/notify-message/my-page?pageNo=...&pageSize=...&readStatus=...`
- `PUT /admin-api/system/notify-message/update-read`
- `PUT /admin-api/system/notify-message/update-all-read`
- `GET /admin-api/system/area/tree`

菜单来自权限接口返回的 `data.menus`，这是员工工作台菜单的唯一事实源。前端只做路径规范化和已确认的两栏展示转换，不按角色名称推断菜单，也不使用管理接口或静态数组重建另一套权限事实。后端 `component` 只作为显示和本地注册表映射信息，不能作为动态代码执行。

所有 HTTP 路径、租户头、Token 刷新和响应解包集中在 `src/services`。组件不得直接调用 Axios。

认证失败既可能使用 HTTP 401，也可能使用 HTTP 200 包裹业务码 `401`。工作台对两种响应执行同一套单次刷新与请求回放；刷新失败通过全局事件立即卸载工作台并进入登录页。HTTP 403 保留当前会话并显示无权限，网络错误和服务端错误保留独立的重试状态。

`/messages/all` 调用 `my-page` 获取当前用户全部消息；`/messages/unread` 固定传递 `readStatus=false`。两个页面均由权限接口中的服务端菜单决定是否可见，前端不自行制造入口权限。

WebSocket 使用 `/infra/ws?token=...`，不带 `/admin-api` 前缀。当前消费 `notify-message-new` 和 `zsjos_lead_assignment`；事件只触发对应 HTTP 数据刷新，不替代站内信或客资业务记录。

`GET /admin-api/zsjos/lead-follow-up-rule/runtime-setting` 返回当前租户右下角消息浮窗时长，工作台按分钟换算为秒，非法值或请求失败使用 5 分钟默认值。该配置不影响待接单功能弹窗。

`PUT /admin-api/zsjos/lead-follow-up-rule/update` 必须回传最近一次读取到的 `version`。服务端只在版本仍一致时更新并递增版本；并发管理员已先保存时返回稳定冲突错误 `1_900_003_079`，客户端应提示刷新后重试，不得用旧表单覆盖新配置。

具备 `zsjos:lead:accept` 的工作台调用 `/zsjos/lead/dispatch-status/my`、`heartbeat`、`mode` 和 `offline` 维护销售页面在线与接单偏好。前端权限只决定是否发起请求和展示控件，后端仍通过销售专员岗位资格决定 `eligible`。WebSocket 断开时工作台停止发送在线心跳并尽力调用 offline；Redis TTL 负责异常关页兜底。

客资提交页从 `/system/area/tree` 读取启用的中国地区树，以两级 `Cascader` 展示省、市，不使用地区字典、静态省市数组，也不在前端补造“其他”。节点提交值来自后端 `selectionCode`；支持数据库配置的 `OTHER + OTHER` 和具体省份 `+ OTHER`。香港、澳门等标记为 `leafSelectable` 的省级节点可直接选择，前端仍按既有契约提交对应 `provinceCode` 与 `cityCode=OTHER`。同级普通地区顺序使用后端 `sort`，System 服务始终把“其他”固定在末尾，管理员可调整普通地区顺序。地区请求拥有独立的加载、空态、错误和重试状态，课程或字典请求失败不会清空已加载地区。

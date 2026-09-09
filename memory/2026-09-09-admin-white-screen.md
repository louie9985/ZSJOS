# Admin 白屏排查记录

## 症状

Vue Admin 初始化后白屏，点击部分菜单后报错并进入无法访问或 404 页面；更换浏览器仍可复现。

## 根因

数据库连接、关键表结构、迁移版本和菜单引用完整性均正常。服务端菜单中存在 `component=zsjos-workbench` 且 `workbench_render_mode=native` 的 React Workbench 页面，Vue Admin 原先将其当作 Vue 动态路由生成，组件无法解析后触发动态导入/404。另有一个未提交的 `DocAlert` 改动启用了模板调用，但删除了对应脚本函数。

## 修复

- 在权限路由生成前过滤 React Workbench 原生菜单，保留服务端权限语义和 Workbench 消费路径。
- 为 Admin 启动入口增加初始化失败回登录页兜底，避免 `router.isReady()` 拒绝导致永久空白。
- 恢复 `DocAlert` 的 props、文档链接跳转和环境开关函数。

## 验证

- MySQL `CHECK TABLE`：`system_menu`、`system_role_menu`、两个 ZSJOS 版本表均为 `OK`。
- 菜单父引用、角色菜单引用：均为 0 个悬挂引用；schema/module 版本均为 `V191`。
- Admin 路由/启动回归测试：5/5 通过；定向 ESLint 通过；`pnpm build:local` 成功。
- 全量 `pnpm ts:check` 仍有 26 个既有 PMS `DICT_TYPE` 常量缺失错误。

## 限制

未使用真实凭据执行登录后的浏览器点击回归；数据库没有执行写入或修复操作。

## 追加定位

截图 URL 是 `localhost/zsjos/tasks/today`，对应本地 Admin 端口 80 的 404 页面。Admin 原守卫只保留当前 URL，不检查它是否属于当前账号的动态授权路由；因此无权或过期菜单 URL 会直接显示 404。现已在动态路由注册后检查匹配结果，未授权路径和未授权登录回跳均回到服务端计算的首个可访问页面。

## 直接刷新复现

在用户 Chrome 中直接刷新 `/zsjos/tasks/today` 仍可复现 404；从 `/` 进入则可正常显示“今日待办”。原因是首屏深链接匹配发生在动态菜单路由注入之前，原守卫注册路由后继续沿用初始匹配结果。已在动态路由首次注册后对原本无有效匹配的首屏地址执行一次 replace 重导航，确保新路由参与匹配。

历史检查显示，`cf391285` 将默认落点从静态首页改为 `/zsjos/tasks/today`，因此此前根路径不会触发该深链接时序问题。

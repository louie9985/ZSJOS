# 内容审核布局交互预览

本页是用户确认正式接入前的独立设计预览。入口为运行中的 Workbench Vite 服务的
`/test/content-review-layout.html`，本地默认地址为 <http://127.0.0.1:5174/test/content-review-layout.html>。

## 可体验内容

- 三行顶部：操作、状态分类、搜索与筛选；默认待审批。
- 学员/账号名称收件箱、多账号对应责任人、内容数量标签。
- 分组账号快照、单列作品；宽屏右侧流程，详情宽度不足时流程前置并默认折叠。
- 现有 180px 正文阅读框，第一条作品含恰好 10,000 字、连续长文本和明确末尾标记。
- 点击账号名称共用一个“媒体学员”内部标签；返回审核保留未保存意见和详情滚动位置。
- 账号页编辑中切换账号或关闭标签的离开确认。
- 右上角切换加载、空列表、失败、账号无权限和账号已失效场景。
- 逐条选择结论、填写意见、保存，全部保存后模拟整批审批。所有状态仅存在于内存，刷新页面恢复。

## 隔离边界

预览只引用现有 React、Ant Design、主题组件和 token。示例人员、账号、平台选项来自
同目录的隔离数据文件，不是生产字典或授权来源；没有业务 API 调用、数据写入、生产路由注册。
媒体学员标签内为导航目标的简化示意，不代表已集成实际账号页面。正式接入仍须复用现有媒体学员页、
服务端菜单、对象授权与 BPM；浏览器测试不能替代真实接口、权限、后端分页或标签缓存集成验收。
创建、编辑草稿、重新提交入口仅说明后续复用现有表单，不复制业务流程。

## 布局尺寸

收件箱使用现有 320px token，较窄桌面使用 300px；流程侧栏 288px。
详情内容宽度不超过 900px 时流程前置，流程节点默认折叠但审批进度和动作保留；
不超过 600px 时作品封面和字段上下排列。视口不超过 768px 时列表/详情分屏切换。
正文保持最大高度 180px 与内部滚动。所有颜色、间距、圆角、字号使用现有主题变量。

## 验证命令

在 `frontend/workbench` 执行：

```powershell
npx tsc --noEmit --target ES2022 --lib ES2022,DOM,DOM.Iterable --module ESNext --moduleResolution Bundler --jsx react-jsx --esModuleInterop --allowSyntheticDefaultImports --skipLibCheck --strict --types vite/client test/content-review-layout.tsx
npx vitest run test/content-review-layout-data.test.ts
python test/content-review-layout-browser.py
```

浏览器脚本复用已有 Python Playwright，访问已有 5174 服务；检查 1600、1280、390 宽度、正文末尾、
搜索筛选、标签复用、编辑保护和无业务 API 请求。截图输出到忽略目录
`node_modules/.cache/content-review-layout/`。正式页与后端接入在预览确认后实施。


## 正式组件验收入口（2026-09-22）

用户已批准接入。当前最终布局请访问 `/test/content-review-integration.html`：搜索和筛选已移入收件箱顶端，使用实际 ContentReviewBatchPage、MediaStudentsPage、TabBar 和受控保留宿主。旧 layout 入口保留为初版设计记录。

该入口用只读 Axios adapter 隔离所有业务传输，非 GET 请求直接拒绝，不能用于真实审批。可用查询参数 `noMenu=1`、`accountMissing=1`、`accountDenied=1`、`listError=1` 检查权限、失效和加载错误。执行 `python test/content-review-integration-browser.py` 检查生产组件的桌面/手机布局、账号定位、意见保留、切换保护、搜索竞争和筛选。截图位于 `node_modules/.cache/content-review-integration/`。

浏览器验收使用隔离响应，不能代替已登录真实 API 联调或发布验收。后端服务/查询测试独立验证真实 Mapper 与租户拦截链。

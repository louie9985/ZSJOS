# 返现管理两级页签

## Registration — 2026-09-29
- Environment: local（ZSJOS_AGENT_ENV 与环境文件未设置，按规则回退）。
- Workstream: local-cashback-two-level-tabs-20260929; owner: current chat /root.
- Goal: Workbench 返现页一级按类型、二级按状态筛选，两级均提供全部，点击立即查询并回第一页。
- Non-goals: Admin/H5 页面、后端/API/权限/数据库变更、新依赖、分支操作、提交或发布。
- Branch: main; worktree: D:/ZSJ-OS; base: 5fadfb3e9be563ac4cb3e5c142308549944aa465; target branch/integration order: None.
- Ownership: frontend/workbench/src/pages/ManagementPages.tsx 的 CashbackPage；新增 test/cashback-tabs.html、test/cashback-tabs.tsx、test/cashback-tabs-browser.py；frontend/workbench/docs/cashback-tabs.md；本记录。
- Existing work: cashback-search 工作流已于 2026-09-29 15:34:32 交付并释放所有权；保留其搜索并发保护及同文件提现导出等既有未提交变更。
- Dependencies: 现有 finance filter catalog、Ant Design Tabs、BusinessTable、运行中的本地 Vite 与现有 Playwright；无新增依赖。
- Verification: Workbench typecheck；真实 Chrome 隔离数据测试类型/状态交集、两级全部、分页复位、关键词、快速切换、错误重试及桌面/移动视觉；scoped diff/UTF-8 检查。

## Delivery — 2026-09-29 18:22 +08:00
- 完成：CashbackPage 类型卡片页签 → 状态小尺寸页签，两级首项与默认选中均为全部；点击立即查询并回第一页，保留另一筛选维度、关键词与高级筛选。服务端目录提供业务选项和排序；加载/失败/无权限禁用，无静态生产选项。复用该组件的我的返现同步具备两级页签。
- Changed files: ManagementPages.tsx 的 CashbackPage；docs/cashback-tabs.md；test/cashback-tabs.html、cashback-tabs.tsx、cashback-tabs-browser.py；本记录。保留同文件提现导出与原返现搜索改动，其余工作区修改未处理。
- 验证通过：npm run typecheck -- --incremental false；python test/cashback-tabs-browser.py；npm test -- src/services/managementApi.test.ts -t 'posts finance filters'（1 passed）；scoped git diff --check；6 个范围内文件 UTF-8 解码及替换字符检查。
- 浏览器证据：真实 Chrome + 合成接口数据，1440×1000 与 390×844；两级全部、类型/状态交集、分页复位、关键词联动、空结果、快速切换旧响应保护、列表失败重试、目录失败重试/空目录、个人查询与无权限均通过。已实际查看 desktop.png、mobile.png，路径 C:/Users/EDY/AppData/Local/Temp/cashback-tabs-browser/。测试桩最初未覆盖既有 my-page 路径，修正后完整通过。
- 附加回归限制：运行 managementApi.test.ts 与 finance-table-values.test.tsx 全文件时，15 项中 12 项失败、3 项通过。11 项因旧测试未提供 Router 而在现有 useSearchParams/useNavigate 处失败；另 1 项为未改动关系日志接口 scene 与测试 sceneCode 的断言差异。本次不扩展修复这些既有问题，不能宣称整个财务测试集通过。
- 未涉及路由、依赖、构建配置或发布验收，未执行生产构建；未重启共享服务，未改变后端或真实数据，未提交/推送。浏览器验收为隔离合成数据，不宣称已验证真实财务数据。
- Status: completed; ownership released. Environment local / main / D:/ZSJ-OS / base 5fadfb3e9be563ac4cb3e5c142308549944aa465 unchanged; integration None.

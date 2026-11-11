# 兼职管理状态展示与快捷筛选

## Registration

- Environment local, owner Codex /root, branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c, integration None.
- Goal 用户可区分启用与停用底色，按启用、全部、停用筛选，默认启用。
- Ownership frontend/workbench/src/pages/SubordinatePartnerPage.tsx, docs/api/zsjos-subordinate-partners.md, this record.
- Scope 工作台页面，复用现有服务端 status 筛选，保留历史 converted 状态及既有权限。
- Dependencies 现有 Ant Design Segmented、Tag 和 partnerPage API。
- Verification TypeScript 检查、现有相关测试、浏览器交互检查、scoped diff。

## Delivery

- 已实现默认启用及启用、全部、停用快捷筛选，两种视图共用状态条件。切换清空分页缓存和选中详情，服务端请求保留搜索条件。
- 收件箱、表格、详情的状态标签分别采用绿色、红色、中性底色，并保留文字。
- npm run typecheck 通过，scoped diff check 通过。
- Ego 浏览器确认默认启用选中，依次切换全部、停用、启用及表格视图，请求分别省略 status、传 disabled、传 enabled，均从第一页开始。390px 手机截图已检查，控件可见。
- 当前账号列表为空，真实记录状态底色及状态变更后的列表行为尚未完成浏览器验收。
- 现有 subordinate-partner.guard.test.ts 1 项通过、2 项失败，断言依赖此前已迁出的 manager-readonly、detailRequestRef 页面代码。HEAD 页面已使用 LeadManagementPage。此次保留原测试及无关代码。
- Ownership released.

## Registration update — test repair

- User requested repair of the two stale assertions. Environment local, branch/worktree/base unchanged, owner Codex /root, integration None.
- Ownership extends to frontend/workbench/src/pages/subordinate-partner.guard.test.ts and this record. Source pages are read-only for this follow-up.
- Verify current Partner-to-LeadManagementPage integration, scoped API routing, readonly detail mode, snapshot display and request invalidation in their owning components using existing source guards. Run focused Partner and unified Lead tests plus scoped diff checks.

## Delivery — test repair

- Replaced the obsolete inline detail assertions with checks of LeadManagementPage integration, Partner-scoped list/detail API routing, manager-readonly mode, ownership snapshot and request-version guards in the owning component. Partner cleanup checks retain coverage of both existing request refs.
- Partner source-guard tests passed 4/4. Additional unified Lead source-guard tests passed 5/7, with two existing unrelated stale assertions concerning filter-profile call syntax and table-mode row pinning. Those tests and production source remain unchanged in this follow-up.
- These are source-contract checks, not runtime concurrency tests or real-account read-only acceptance. No runtime behavior changed. Ownership released.

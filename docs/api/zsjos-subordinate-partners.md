# 兼职管理与归属 API

## Permission model

The Partner page is a permission-free route container. Its three server-owned permissions are additive:

- `zsjos:partner:query` keeps the existing read scope: the current employee, enabled employees covered
  by System department data permission, and employees configured through Partner visibility relations.
- `zsjos:partner:manage` is read-only and strictly limited to Partners currently assigned to the logged-in
  employee. It does not consult System department data permission or Partner visibility relations.
- `zsjos:partner:manage-all` grants tenant-wide visibility and all supported management commands.

To configure strict self-only visibility, grant `zsjos:partner:manage` without `zsjos:partner:query`.
When permissions are combined, their read scopes are unioned and `manage-all` wins. Unassigned Partners are
visible only to `manage-all`. Disabling an employee or reassigning a Partner immediately removes self-only
access without deleting ownership history. No permission is inferred from a role name.

## Unified endpoints

- `GET /admin-api/zsjos/partner/page`
- `GET /admin-api/zsjos/partner/{partnerId}/leads/page`
- `GET /admin-api/zsjos/partner/leads/{leadId}`
- `POST /admin-api/zsjos/partner/create` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/enable|disable` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/mobile` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/reset-password` (`zsjos:partner:manage-all`)
- `POST /admin-api/zsjos/partner/{partnerId}/convert` (`zsjos:partner:manage-all`)
- `GET /admin-api/zsjos/partner/assignment-candidates` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/assignment` (`zsjos:partner:manage-all`)
- `GET /admin-api/zsjos/partner/{partnerId}/assignment-log/page` (`zsjos:partner:manage-all`)
- `POST /admin-api/zsjos/partner-student-link/bind|unbind` (`zsjos:partner:manage-all`)

The list returns account identity and state, current ownership and lifecycle timestamps. It never
returns passwords, tokens or the internal bound System user identifier to the Workbench contract.
Assignment updates require a reason and use the current relation version to reject stale changes.

The former `/admin-api/zsjos/subordinate-partners/**` GET endpoints remain temporary aliases for a
rolling frontend/backend release. They execute the same three-permission read and object checks and
must not become a second authorization contract.

## Partner Lead visibility

Once a Partner is visible, the reader may inspect every historical and future Lead whose persisted
`partnerId` matches it. Detail, follow-up, appeal, complaint, customer-order and flow-history reads use
the same live Partner scope and remain read-only. Reassignment moves this complete visibility to the new
owner and does not rewrite Lead snapshots.

Partner Leads created after V143 snapshot `partnerOwnerUserIdSnapshot` and
`partnerOwnerNameSnapshot` at submission. Older null snapshots display `未记录`; current ownership is
never substituted as historical fact.

## Workbench presentation

兼职管理提供收件箱和表格两种视图，复用相同的服务端搜索与分页结果。收件箱沿用学员管理的
头像卡片、搜索同行收起按钮与窄头像栏；折叠偏好只存储在当前浏览器，切换不改变授权范围。
桌面列表、搜索工具栏使用一致左右边界，移动端收起后显示横向头像条。

点击兼职默认进入“概览”，展示账号身份、当前归属、生命周期时间与客资统计。“累计提交客资”
取该兼职客资分页接口的 `total`，不受明细分页影响。概览不展示分页内指标；现有接口没有提供
全量已分配、成交等汇总，不以当前页数据推断整体成交率、业绩或趋势。“客资明细”提供服务端分页表格，通过客资编号或查看详情
进入现有只读详情；返回保留兼职选择、搜索和客资分页。编号使用 `leadNo`，分类和提交时归属展示
保存的快照，缺失时显示未记录。管理命令继续仅对 `zsjos:partner:manage-all` 开放。

兼职列表与客资明细使用现有 ProTable，启用刷新、密度、全屏、列设置；两张表独立保存浏览器列配置。
搜索仍使用原服务端搜索，分页仍为每页 20 条，不启用重复的自动搜索表单。归属历史弹窗使用紧凑 ProTable，关闭额外工具栏。

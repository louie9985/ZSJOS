# 销售客资跟进日历

员工工作台页面位于 `/calendar/sales-lead-follow-up`，菜单组件为
`zsjos/leadFollowUpCalendar/index`，Vue Admin 不实现该员工业务页。

## 数据与权限

- `GET /admin-api/zsjos/lead-follow-up-calendar/days`：返回 `{date,count}[]`。
- `GET /admin-api/zsjos/lead-follow-up-calendar/cards`：返回 `{list,total}`，每项包含
  `lead`（现有客资详情投影）、`deadline`、`lastFollowUp`、`canReadFollowUp`。
- 两接口都要求 `zsjos:lead-follow-up-calendar:query` 和 `zsjos:lead:query`。
  当前实现为本人日历：使用登录用户与当前租户，不接受目标用户参数；Lead 当前负责人和待办执行人都必须为本人。
- 客资来自未删除的 Lead 和未删除、未完成的 `lead_first_follow_up` /
  `lead_follow_up_reminder` 业务待办。取消、完成、其他任务类型、已转移客资不计入。
  每个客资只计一次，按全部未完成跟进任务中最早的截止日期归档；历史逾期保留在原截止日。
- 上次跟进复用现有 Lead / Opportunity 合并记录服务，遵循
  `zsjos:lead-detail:follow-up-read` 和对象读取权限。未获授权时返回空记录与
  `canReadFollowUp=false`，页面明确提示无查看权限。
- 快捷跟进复用现有接口、`zsjos:lead-follow-up:create` 和 `ADD_FOLLOW_UP` 动作投影，
  后端继续检查对象、归属、状态、幂等和事务，不通过日历另建写入路径。
- 用户可见信息沿用字典快照；缺失快照显示“未记录”，不以当前字典标签编造历史名称。

## 查询与交互

`start` / `end` 为 `YYYY-MM-DD`，开始包含、结束不包含，最多 42 天。
前端按当前月历的 42 天网格请求统计，日期弹窗按一天请求卡片。
时间戳继续使用现有 epoch 毫秒契约。`pageNo` 从 1 开始，`pageSize` 默认 24、最大 48。

`sort=deadline|category`，`direction=asc|desc`。默认截止时间升序。
分类优先顺序来自 System 字典公共 API（现有顺序为管理员 sort 降序），S / S级 / S类优先；
其余不设置静态分类列表。缺失／已删除的分类在正序末尾；同级按截止时间和内部技术 ID 稳定排序。
内部 ID 只参与查询与稳定排序，不展示为客资编号。

日期格只展示数量。点击日期打开宽屏卡片弹窗，点击遮罩不关闭。
卡片展示姓名、可复制手机号和微信、分类与销售阶段快照、截止时间、上次跟进信息。
底部两个等宽按钮分别打开现有客资详情和跟进弹窗。保存成功后重新查询日期统计与当前卡片页，
保留日期和排序；若最后一页变空则回到有效页。远程请求采用取消与序列检查，支持加载、空态、
错误、重试及未授权状态；未提交的详情编辑关闭前沿用确认保护。

## 安装与验证边界

V277 在 V276 后执行，仅在 `/calendar` 下新增缺失的菜单元数据和版本记录；
不分配角色权限、不修改业务数据、不新增依赖或表。日历目录由既有 V146 创建，
因此 fresh 安装沿用 bootstrap 后完整迁移链，无需在目录尚不存在的基础 seed 中重复定义。
管理员通过角色管理配置新页面权限及所需现有客资操作权限。

验证：`LeadCalendarServiceTest`、`tools/test_lead_calendar.py`、两端菜单测试、Workbench
typecheck / build，以及 `frontend/workbench/test/lead-calendar-browser.py` 的真实 Chrome
桌面／手机合成接口流程。浏览器夹具不代表已更新运行后端；本次未重启本地旧后端，
认证后端到端验收仍待加载新版本。全链 fresh/upgrade 验证受既有 Core schema/baseline
差异阻断，不能以本次独立菜单与查询验证代替发布验收。

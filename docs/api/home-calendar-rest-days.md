# 首页共享休息日

仅员工工作台首页小日历消费，人工标记、租户共享，当前租户为 1。不联动 HRM、排课、业务期限或其他日历；周末和节假日不自动推断。

## 权限与接口

- GET `/admin-api/zsjos/calendar-rest-day/list?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`：首页权限 `zsjos:business-task:query`；返回 `{date,rest,version}[]`，包含已取消的日期状态。最大366个自然日，空结果表示未标记。
- PUT `/admin-api/zsjos/calendar-rest-day/batch-update`：`zsjos:calendar-rest-day:manage`；请求 `{changes:[{date,rest,version}]}`，1至366个不重复日期；返回保存后的日期状态。
- 无记录日期版本为0。按租户＋日期唯一，先按日期排序加锁，再校验版本并事务更新；取消保留行。目标状态相同的重复请求不推进版本，不覆盖其他状态；版本不符且目标状态不同返回 `1900018061`，整批回滚。`1900018060` 为对象权限拒绝，`1900018062` 为非法范围或变更。
- 身份和租户从既有 ADMIN 上下文取得，不接受客户端传入。Controller权限和Service对象权限独立校验；租户共享配置不按创建人或部门限制。PUT接入既有 `ZsjosAuditInterceptor` 业务审计，不新增敏感请求体记录。

## 交互

首页按可见六周范围读取，重新进入、刷新页面或点击刷新重新查询，无轮询或推送。休息日红色圆底白字，今天叠加外圈。首页日历独立使用，日期点击仅更新当前选中日期，不跳转个人日历，也不读取个人日历权限。保留卡片原有高度，内部日期区填充剩余空间，六周日期行均匀展开。

管理弹窗支持跨月多选、标记/取消、逐条撤销和保存前清单。关闭有修改时确认放弃，保存失败保留草稿。发生版本冲突后刷新所有待修改月份，保留目标状态并更新版本，用户核对后再次保存。加载失败显示错误并提供重试，不按空数据处理。

## 安装与回退

V319在V318之后运行，要求两个账本均存在V318和首页菜单6780；新增空表 `zsjos_calendar_rest_day` 和按钮73650，不修改任何角色授权。新建库结构同步于Core期望结构和bootstrap结构，权限由迁移定义。管理员在系统角色管理的首页节点分配“管理休息日”；Vue现有菜单树读取服务端按钮，无需新增业务页面。

迁移可重放，校验实际结构、唯一键和菜单身份，失败不登记成功；DDL不能依赖事务回滚，部分失败保留已建结构，修复前置条件后重放。回退应用时保留新增表及数据，可在系统禁用按钮。升级前备份目标表、菜单和两个账本。数据库执行需要对目标环境单独授权，不自动应用到业务库。

## 验证入口

- `python script/sql/mysql/tools/test_home_rest_days.py --java`：独立MySQL测试库的迁移失败/恢复、权限、版本和事务并发。
- `python script/sql/mysql/tools/test_home_rest_days.py --fresh-core`：独立库完整Core基线和待执行迁移链及结构比较。
- Workbench：`npm run typecheck`；`npm test -- src/services/calendarRestDays.test.ts src/services/todayTasks.test.ts`。
- `python frontend/workbench/test/home-rest-days-browser.py`：现有Vite测试地址上的隔离传输浏览器验证，真实账号、权限配置与部署后验收不由此替代。

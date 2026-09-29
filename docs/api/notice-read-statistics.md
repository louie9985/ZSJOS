# 公告阅读统计

中世健公告管理的 Vue 管理端、React 工作台均在公告详情提供“公告正文 / 阅读情况”。
阅读统计沿用 `system:notice:query`；员工读正文和标记已读仍使用 `system:notice:read`。
System 持有公告、接收人和阅读事实，不新增 ZSJOS 代理业务、角色授权或依赖。

## 发布名单与阅读事实

新发布的 ALL、TARGET 公告在现有发布事务中写入 `system_notice_recipient`，
并将 `system_notice.recipient_snapshot_complete` 设为 true。标识、名单和发布状态一起提交。
ALL 使用当前租户启用且拥有公告阅读权限的 ADMIN 用户；TARGET 沿用指定部门（含后代）
和指定用户并集，按相同条件筛选去重。TARGET 没有可用接收人继续拒绝发布；ALL 可生成有效零人名单。

接收人保存 `user_name_snapshot`、`dept_id_snapshot`、`dept_name_snapshot` 和
`profile_snapshot_complete`。后续改名、调岗、停用、删除、权限变化不改变该名单及快照。
账号状态来自当前系统记录，删除账号仍显示其新公告中保存的姓名、部门快照。
ALL 的阅读可见性保持原规则，后入职或后获权人员仍可阅读，单列为名单外阅读。
TARGET 仍只允许原接收人且须具有当前阅读权限；统计名单不授予任何访问权。

React 员工正文实际挂载、绘制且可见后才调用 `PUT /system/notice/mark-read?id=...`。
移动端和表格模式不自动打开第一条正文；列表、隐藏详情、管理预览不写已读。
正文加载成功但写已读失败时保留正文和未读状态，显示具体错误并允许“重试记录”。
重复/并发写入继续由租户、公告、用户唯一约束和现有幂等逻辑保证，保留首次时间。
已读表示打开正文，不代表阅读完全文、附件或确认签收。

## 接口

以下路径在 ADMIN API 前缀 `/admin-api` 下，均为 GET，使用现有 CommonResult 包装。
两端通过各自已有 typed API 层调用，后端配置权限与租户隔离独立执行。

### `/system/notice/read-summary?id=...`

- `published`：非草稿，包含已下线；草稿不返回人数。
- `rosterComplete`：本次名单可靠。新公告依据完成标识，历史 TARGET 依据实际存在的冻结名单。
- `expectedCount/readCount/unreadCount`：固定名单总数、名单内已读、名单内未读。
- `readRate`：0–1 比例；零人名单和未知名单为 null。
- `extraReadCount`：实际已读且不在固定名单的人数；未知名单为 null。
- `actualReadCount`：全部实际阅读人数，包含名单外人员。
- `departments/extraDepartments`：该公告对应名单/名单外人员的部门候选，字段为 id、name。
  历史未知名单时 departments 来自实际阅读人员；没有部门标识的不生成虚构选项。

固定统计满足 expectedCount = readCount + unreadCount。筛选不影响整体统计。
例如固定 100 人，名单内 80 人阅读，名单外 5 人阅读：应读 100、已读 80、未读 20、
阅读率 80%、名单外阅读 5、实际阅读 85。

### `/system/notice/read-page`

参数：id、pageNo（默认 1）、pageSize（默认 10，1–200）、scope、可选 name、deptId。
scope 使用技术查询协议：EXPECTED 全部应读、READ 名单内已读、UNREAD 名单内未读、
EXTRA 名单外阅读、ACTUAL 全部实际阅读，默认 EXPECTED。未知历史名单强制使用 ACTUAL。
姓名按文字包含匹配，部门按资料口径的实际标识匹配；SQL 完成聚合、过滤和分页，按 userId 稳定排序。

返回 PageResult：list、total。行字段：userId、userName、deptId、deptName、
profileSource（SNAPSHOT/CURRENT）、accountStatus、accountDeleted、readTime（首次阅读时间）。
userId 仅作为内部关联和行键。时间沿用系统序列化约定，两端使用各自时间组件。

快照行不从当前资料回填空字段；CURRENT 行显示“当前资料（非历史快照）”。
丢失用户资料显示“账号已删除”，不编造历史身份。跨表连接显式包含 tenant_id/deleted 条件，
公告先通过租户过滤查询；汇总和单次分页查询使用一致的只读事务。
汇总与分页属于不同请求，期间新阅读可能使两者短暂相差，可刷新获取新状态。

## 兼容与页面状态

- 历史 ALL 没有完整名单：只展示实际阅读人员，应读/未读/阅读率明确显示无法统计。
- 历史 TARGET 复用既有接收人；没有资料快照的行仅显示当前资料并标明口径，不回填历史快照。
- 草稿提示发布后生成统计；下线后统计保留；复制公告生成独立草稿，不继承名单或阅读记录。
- 两端支持汇总、四类明细、姓名/部门筛选、服务端分页、刷新、加载/空/错误/重试/403 状态。
- 管理阅读统计不调用 mark-read；第一版无导出、催读、确认知晓、时长、实时推送。
- 两端只新增少量 API 类型，不引入共享 workspace 或依赖；Vue/React 状态与表格保持各自所有权。

## 迁移与验收

执行顺序：Core V283 → `V284__notice_read_statistics.sql` → 更新后端 → 更新两端前端。
使用仓库迁移器先检查计划及现有校验和。V284 只增加五列，不重写历史公告、阅读、接收人、
角色权限或已有版本校验和；初始化仍沿用既有 baseline + 有序迁移链，无需改 bootstrap。
DDL 隐式提交，失败后检查实际列及两个版本账本，按实际状态前向恢复。
两个版本记录在全部后置检查后同事务写入，任一失败不能登记部分成功；旧标记不能跳过缺失结构。
回滚先回退应用，新增列可保留；不通过删除历史记录回滚。共享数据库变更和服务重启需单独授权。

验证入口：

```text
cd backend
mvn -pl yudao-module-system -am test -Dtest=NoticeServiceImplTest,NoticeMapperTest,NoticeControllerTest,NoticeReadStatisticsTest -Dsurefire.failIfNoSpecifiedTests=false
cd ..
python script/sql/mysql/tools/test_notice_read_statistics.py
python frontend/workbench/test/notice-reading-browser.py
```

SQL 测试仅连接独立 `notice-v284-verify*` 容器，覆盖初次、重复、部分结构、先决条件失败、
列契约错误和账本错误、既有标记下恢复，并检查中文 HEX；保留隔离库供核查。
浏览器测试分别使用 5187/5188 的隔离 Vite 夹具，全部接口为合成响应，不发送实际公告。
本地验证不替代部署后的真实登录用户、权限和租户验收。

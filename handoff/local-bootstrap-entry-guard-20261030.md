# Bootstrap非空库前置保护
## 2026-10-30 12:50 北京时间 注册
- owner root；环境local（沿用已核对变量/marker均未设置）；main / D:/ZSJ-OS；base 208abf277065706cbee45164113903eded9e6303；target/integration None。
- 用户明确要求修复非空库重复bootstrap副作用。范围仅bootstrap入口/执行器所需失败控制、隔离测试与直接文档；不改编号迁移、业务数据、实际账本或权限；保留前轮未提交校验器修复。
- root负责入口方案集成与文档；sources只读分析及隔离实验前置guard/mysql SOURCE失败语义；config负责新bootstrap保护测试和既有fresh合同测试更新（待方案确认）。数据库只允许本任务创建的隔离fixture，任何拒绝必须结构/行数据/账本/routine/event不变；空库首次/完整迁移仍成功。
- 不提交推送、不重启现有服务，不把验证库视为业务数据库。失败控制不可依赖可能继续的CALL后续无条件写入。

## 2026-10-30 13:23:17 北京时间 — 修复交付
- main / D:/ZSJ-OS；HEAD仍208abf277065706cbee45164113903eded9e6303；环境local；未提交/推送。保留前轮fresh校验修复及原有dirty缓存。
- 实现：bootstrap.sql在首SOURCE前以纯SELECT检查是否选择数据库及tables/views/routines/events为空；非空用稳定诊断+有意JSON SQL错误终止，不创建永久guard对象。MySQL客户端和官方docker fixture路径强制--skip-force，覆盖my.cnf force。原生MysqlClient.execute_file另对canonical bootstrap做独立Python只读预检，仅明确1才发送文件；异常/非空/无库均拒绝，其它迁移不受此fresh-only检查影响。
- 源码边界：仅bootstrap入口、zsjos_db.py必要客户端/预检、test_bootstrap_client_guard.py、test_bootstrap_entry_guard.py、既有fresh合同脚本恢复严格0及直接operations文档；没有改V001–V358、00-bootstrap-schema、Core desired schema、bootstrap-compatibility、种子或角色权限。
- 验证：合并前轮校验回归与本轮guard测试共66项Python单测通过；本轮原生执行器9项用例覆盖不发送DDL、探测异常、空库允许、其它迁移不受影响；原生预检用单测验证，与下述Docker SQL执行路径分开报告。
- 真实MySQL8.4.11入口11组全部通过：旧/已升级schema、仅table/procedure/function/view/event、force配置覆盖、未选库、空库完整初始化、全升级后再执行。非空拒绝前后13类快照逐字节相同（含DDL、全部行、列、索引、FK、触发器、视图、例程参数、事件及双账本），无4旧表重建。
- 真实MySQL严格fresh合同6组全部通过：完整379迁移、379文件/账本检查、schema drift0，以及错误name/permission/version/checksum/缺列反例。已删除前轮4旧表补偿，负例前要求真正零drift。core/V061仍按既有明确deferred政策，不冒称文件完整性已验证。
- CLI语义另有隔离实测：MySQL8.4.11默认batch及3层SOURCE错误立即中止；force会继续，显式skip-force能覆盖CLI及配置文件force；不使用该客户端不支持的abort-source-on-error。生产迁移镜像使用发行版客户端，故增加原生Python预检而不假定它等同实测客户端。不更改镜像依赖；部署时需重建migrator以携带新入口/工具。
- 证据：D:/zsjos-bootstrap-guard-20261030/bootstrap-entry-guard.log 与 fresh-contract-strict.log；完整测试自有容器已清理，原mysql/redis保持。早期bsprobe_bd7299c9dd_*隔离实验schemas与/tmp对应文件保留，未触碰业务schema。
- git diff --check通过；历史迁移、baseline/schema/compatibility目录相对HEAD零diff，index未变；业务数据、真实账本、账号权限和应用源码未改。未启动/重启现有业务服务。直接bootstrap仍要求独占空库和fail-fast客户端，不支持force/忽略SQL错误的GUI或绕过入口单独执行baseline。
- Status: completed（本地代码及上述验证）；未提交/部署，文件所有权释放。

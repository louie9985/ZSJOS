# 历史fresh校验修复
## 2026-10-30 12:10 北京时间 注册
- Environment local；branch main；worktree D:/ZSJ-OS；owner root；base以本轮git rev-parse为准；target/integration None。
- 用户授权修复9条历史验收失败，仅校验器/工具/测试/文档，不修改V001–V358、业务数据、真实账本或角色权限；不自动提交推送。
- Ownership: config子任务负责只读Git历史调查报告（不改源）；sources负责统一checksum校验工具及测试；root负责verify-bootstrap.sql结果断言、文档及统一受控fullfresh验收。避免同文件并写；SQL工具测试及fullfresh资源由root协调。
- 验证：历史源码字节签名保持；真实fresh全链、来源结构/菜单/账本缺失与篡改负例；EOL兼容不接受实质文本修改；不靠扩充未知hash或更改账本消除FAIL。

## 2026-10-30 12:49:13 北京时间 — 本地修复交付
- Base / HEAD: 208abf277065706cbee45164113903eded9e6303，main，D:/ZSJ-OS，local，未提交/推送。root完成SQL断言与文档，sources完成工具与回归，config完成Git历史审计及真实MySQL正反例；写入所有权释放。
- 历史原因已核实：V114/V116/V136/V192/V193均在2402fe0c7d按用户授权移除10条system_role_menu写语句，其余SQL token及literal与旧版一致；V196/V208是EOL差异；V159旧名被V329合法替代。审计见docs/development/fresh-verifier-history-20261030.md。
- 修改文件：verify-bootstrap.sql仅迁出8处module-file hash名单并按V329调整最终菜单名/path，其他业务/registry检查保留；tools/zsjos_db.py新增统一只读integrity与7个受审计seed兼容pin并接入preflight/verify/fresh，fresh取消自动reconcile；checksum/execution/schema测试、独占test_fresh_verification_contract.py；operations文档、本记录及原整合记录的后续指针。
- 安全边界：普通release严格actual文件hash/既有窄EOL规则；7目标seed仅接受已审计source pin+原明确认可hash，不接受未知值；缺任一expected模块版本拒绝；7已安装seed篡改在任何升级写入前阻断；不修改已应用文件、真实账本、业务数据或角色授权。core/V061仍按原SQL provenance范围deferred，不冒称其文件完整性已验证。
- 回归：python -B -m unittest test_migration_checksum test_migration_execution test_schema_drift test_fresh_verification_contract，54项通过；错误内容/空格/BOM/未知hash/缺账本/skip绕过均有拒绝用例。
- MySQL8.4.11：完整fresh执行379条迁移，379个文件/账本integrity检查通过，fresh正向schema drift=0，原9条SQL失败全部消除，完整正向PASS。真实5反例（错误名称、错误permission、缺module版本、checksum篡改不自修、缺remark字段）均拒绝并验证恢复/准确新增差异。
- 发现并隔离的既有测试副作用：原非空bootstrap拒绝探测会先重建4张manifest旧名空表，再因重复表失败。独立最小fixture确认before drift0/after4旧空表；新负例明确输出preexisting_probe_drift并只接受精确新增missing column，不隐藏未知差异。不在本轮修改bootstrap/baseline，仍禁止对非空业务库重复bootstrap。
- 实际本地账本只读核对：V114/V116/V136/V192/V193/V196/V208七项均accepted_without_ledger_change=true，仅SELECT，无迁移/reconcile。报告D:/zsjos-fresh-verification-fix-20261030/local-ledger-readonly-check.json。
- 证据：D:/zsjos-fresh-verification-fix-20261030/fresh-verification-contract-final.log/result.json、guard-drift-diagnosis.log；初次negative harness失败记录保留，最终结果completed=true。自有容器均清理，仅原mysql/redis运行。
- 最终git diff --check通过；历史migrations目录相对HEAD零diff，Git index未变；原有缓存dirty保持，未改应用源码。未针对生产数据库执行验收，本轮修复尚未提交。

## 2026-10-30 13:23:17 北京时间 — 后续bootstrap副作用已修复
- 用户单独授权的入口修复已完成：拒绝非空库前不执行持久DDL/DML；11组真实MySQL保护用例及恢复严格零漂移的fresh合同通过。原4旧表补偿已移除。详见local-bootstrap-entry-guard-20261030.md；本条保留前次诊断历史，不改写当时观测结果。两轮修复均尚未提交。

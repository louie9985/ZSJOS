# Workstream: main-withdrawal-stackoverflow

- Goal: 修复提现分页接口因 Mapper default 方法代理递归导致的 StackOverflowError
- Non-goals: 不修改数据库、接口契约、权限模型或无关模块
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: fix/media-account-create-columns-v204
- Ownership scope: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/withdrawal/WithdrawalMapper.java; related withdrawal tests
- Owner: /root
- Dependencies: MyBatis-Plus BaseMapperX conventions; JRebel runtime behavior
- Integration order: Mapper change, focused tests, module compile
- Verification plan: focused withdrawal tests and Maven compile

## Delivery 2026-09-11 17:38:30 +08:00
- Beijing time: 2026-09-11 17:38:50 +08:00
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 修复提现分页接口 StackOverflowError
- Key decisions: 将 WithdrawalMapper 的 selectPage 重载改名为 selectPageByApplicant，避免与 BaseMapperX.selectPage 的 default 方法代理分派冲突；同步更新 Service 调用。
- Execution result: 源码修改完成；Maven 编译进入 zsjos 模块，但被已有 SalesOrderSubmitReqVO.java 与 SalesOrderDO.java 非法反斜杠字符阻断。
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/withdrawal/WithdrawalMapper.java; backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/withdrawal/WithdrawalServiceImpl.java; handoff/main-withdrawal-stackoverflow.md
- Verification evidence: mvn -pl yudao-module-zsjos -am -DskipTests compile failed on unrelated pre-existing syntax errors at SalesOrderSubmitReqVO.java:32 and SalesOrderDO.java:51.
- Dependency/integration impact: No dependency, database, or API contract changes.
- Remaining work: Restart application with JRebel and call /admin-api/zsjos/withdrawal/page; compile is pending unrelated source errors.

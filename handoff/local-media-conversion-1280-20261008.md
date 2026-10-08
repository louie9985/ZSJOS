# 新媒体客资成交金额门槛

## Registration — 2026-10-08（北京时间）
- Environment: local（ZSJOS_AGENT_ENV 与 /etc/zsjos/agent-environment 均未设置）；owner: current media-conversion chat。
- Branch: main; worktree: D:/ZSJ-OS; base HEAD: d2f836006ff07b5517bcd6d485d72df788bf3cb0; target branch/integration order: None。
- Goal: 新媒体客资分析在原生效首购条件上增加单笔 total_amount >= 1280 元，覆盖总览及分页/兼容明细；有效客资分母不变，不跨订单累加。
- Non-goals: 销售业绩、其他新媒体看板、权限、数据库写入、依赖、运行服务和 Git 操作。
- Ownership: MediaLeadFactMapper.java、MediaLeadQueryMapper.java；MediaLeadPageQueryTest.java；新增 MediaLeadConversionAmountTest.java；docs/api/media-lead-analysis.md；本记录（均在既有 performance 包目录内）。目标文件目前无未提交修改，保留其余工作流变更。
- Dependencies: 既有订单 total_amount（元）、有效首购与冻结贡献范围；None for integration。
- Verification: 真实 MyBatis 查询执行的 H2 MySQL 模式金额边界/重复订单/租户与范围测试、现有新媒体服务及 Mapper 聚焦 Maven 测试；核对 Workbench 消费服务端结果和 Vue Admin 无该页面；scoped diff。未授权重启服务或部署。

## Delivery — 2026-10-08 13:15 +08:00（北京时间）
- Context: environment local；main / D:/ZSJ-OS；HEAD 与注册一致 d2f836006ff07b5517bcd6d485d72df788bf3cb0；owner 同注册。
- Result/decisions: 原契约无金额门槛，依据本次明确需求追加单笔生效首购总金额 >=1280 元。MediaLeadFactMapper 维护共用条件，MediaLeadQueryMapper 复用；总览期间/漏斗/成员及兼容/分页明细一致。取最早合格订单，空或低额不计，不累加小额，不变更有效客资分母和持久化状态。
- Changed files: 注册内两个 Mapper；MediaLeadPageQueryTest.java 补充订单金额夹具；新增 MediaLeadConversionAmountTest.java；docs/api/media-lead-analysis.md；本记录。
- Verification: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=MediaLeadConversionAmountTest,MediaLeadPageQueryTest,MediaLeadAnalysisServiceTest,MediaLeadFactMapperContractTest,MediaLeadFactMapperParsingTest -Dsurefire.failIfNoSpecifiedTests=false test` BUILD SUCCESS，20 tests / 0 failures / 0 errors / 0 skipped。实际 MyBatis + H2 MySQL 模式覆盖金额边界、空金额、多笔小额、首个合格时间、去重、三类范围、租户、删除、生效/复购条件及分母/明细行数。scoped diff check 通过。
- Consumers: Workbench mediaLeadAnalysis.ts / MediaLeadAnalysisPage.tsx 直接使用返回字段，协议形状不变；Admin adminRouteFilter.ts 明确排除此 Workbench 页面，无该接口消费者。无 UI 代码变化，不需 UI 构建或视觉布局验收。
- Remaining: 未重启/部署运行服务；未进行生产 MySQL 或认证真实 API/浏览器验收，H2 证据不替代部署验收。无数据库写入、依赖或 Git 操作。Integration: None。Ownership released。

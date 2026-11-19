# 报名资料邮寄地址必填
- Environment: local（环境变量及环境文件未配置）
- ID/Owner: local-registration-address-required-20261113 / Codex
- Branch: main; Worktree: D:/ZSJ-OS; Base: 464c40f6d0eae8ed91cceddde915675ee8a38543
- Goal: 新生成收集表启用教材、选中礼品地址即必填。
- Non-goals: 历史快照、证书地址、数据库、发布。
- Ownership: StudentInfoProductRules.java、StudentInfoAddressRequiredTest.java、docs/api/order-student-information-tasks.md、本记录。
- Dependencies: 既有字段快照与前后端 required 校验；不修改并行工作文件。
- Target branch / integration order: None
- Verification: 定向 Java 测试、diff 检查、前端消费协议检查。

## Delivery
- 新表单生成时教材/礼品地址 required=true；未选礼品字段移除；模板及历史快照不修改。
- Maven reactor 定向 StudentInfoAddressRequiredTest 通过（exit 0）；diff whitespace 检查通过。
- 已检查 H5 required 展示/提交和服务端字段校验消费；未进行真实浏览器/在线请求验收，未重启或部署。
- Ownership released.

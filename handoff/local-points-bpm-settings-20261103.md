# local-points-bpm-settings-20261103
环境 local（环境变量与环境文件未配置）；owner Codex；分支 main；工作树 D:\ZSJ-OS；base f7abc09b91d30e140ef8bf4895b4b8b8181f0681。
目标：按用户确认，积分申请及发码完整采用 BPM 审批设置与终态。非目标：修改已发布流程、权限、数据库、部署。
所有权：PointsApprovalPolicy.java、PointsRequestService.java、PointsIssuanceService.java 及对应 Policy/Issuance 测试、docs/api/points-redemption.md、本记录。
依赖：现有 BPM 公共 API 和终态事件；target branch/integration None。验证：积分相关 Maven 测试、diff 检查。
旧文档人工链限制与用户当前确认冲突，以当前确认优先并同步文档。

交付：移除提交与发码中的独立审批规则，固定发布版本并采用 BPM 终态；保留非通过不发码、幂等与通知重试。文档同步。只读核对本地 yudao-mysql / ruoyi-vue-pro 租户1 最新发布：autoApprovalType=2，两审批节点 assignStartUserHandlerType=1。未改数据库或流程。
验证：Maven Points*Test reactor BUILD SUCCESS；git diff --check 通过。未重启部署，未执行真实账号申请及外部发码验收。所有权释放。测试日志 artifacts/points-bpm-settings-test.log。

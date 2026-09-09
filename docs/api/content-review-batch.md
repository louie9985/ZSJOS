# 生产内容批审 API

生产内容批审以账号为边界组成批次，一个批次只能包含同一账号的 1 至 20 条内容版本。提交后冻结账号、运营、责任编导关系快照和审核上下文。

## 核心接口

- `POST /admin-api/zsjos/content-review/batches`：创建批次并校验条数、账号一致性和内容版本状态。
- `POST /admin-api/zsjos/content-review/batches/{id}/submit`：提交 BPM 审批实例。
- `PUT /admin-api/zsjos/content-review/batches/{id}/items/{itemId}/director-decision`：编导逐条暂存通过或退回。
- `POST /admin-api/zsjos/content-review/batches/{id}/director-complete`：编导完成本轮，要求所有条目已有结论。
- `PUT /admin-api/zsjos/content-review/batches/{id}/items/{itemId}/final-decision`：总监逐条暂存结论并选择是否收录素材库。
- `POST /admin-api/zsjos/content-review/batches/{id}/final-complete`：总监完成整批结论并统一落地。
- `POST /admin-api/zsjos/content-review/items/{itemId}/publish`：登记实际发布平台链接和时间。

总监完成全部条目结论后，批次内通过项统一进入待发布，收录项在同一事务中生成“生产内容”素材；退回项退出本轮。收录字段无法从内容、账号或系统默认值映射时，仅禁止该条收录，不阻止批审完成。BPM 回调、重复提交和重复收录均按业务键与事件号幂等处理。

# 内容审批账号资料补全

V316 在 Core V315 之后执行，只补租户 1 未删除审批批次与未删除关联账号的缺失字段，包含草稿、进行中及已结束审批。按内部账号 ID 和批次账号集合关联；有学员信息时校验学员一致，不通过姓名匹配。

字段来源：`productGoal` 对应账号 `detail_values_json.product_goal`，`productFormLabel` 对应 `work_format`，`publishFrequency` 对应 `publish_rhythm` 已保存的 `detail_snapshot_json.displayValue`，`bottleneckLabel` 对应 `primary_problem_code_label_snapshot`。前三项优先使用账号已保存的 displayValue；只有前两项纯文本允许回退原值。发布节奏不查当前字典，不用原始字典码伪造标签。产品形式不是定位卡的 `content_format`。

历史非空值保留，包括审批已显示的 `primaryProblems` 瓶颈；缺少来源、账号失效或学员不匹配时保持空白。补入字段分别保存 `profileBackfill.<field>`，注明 `CURRENT_ACCOUNT`、V316、账号 ID 和补全时间。这是按执行时资料补全，不声称重建历史提交事实。每个发生变化的批次版本加一，阻止使用旧版本的并发提交覆盖修复；审批状态、流程、审批人、作品和权限不变。

执行前备份 `zsjos_content_review_batch` 与两份版本表；使用 UTF-8 文件和 utf8mb4 客户端，在目标数据库执行 `script/sql/mysql/migrations/V316__content_review_account_profile.sql`。不使用 `--force`。存储过程内的 DML、后置校验和两份版本登记共享事务，异常回滚并向外抛出。重复执行只补仍缺失且有来源的值，不覆盖已有补充值。无 DDL、无权限授权、无业务删除；恢复应基于备份逐字段审核还原 JSON，不能整表覆盖正在变化的审批状态。已应用文件不得静默改写或重算其部署校验和。

新建和重提在正式提交时从锁定账号读取上述四项，覆盖草稿同名旧值，来源缺失则清除旧值；账号名称与其余本轮上下文保持原逻辑。提交后的详情只读冻结快照，不实时追随账号变化。

验证入口：`python script/sql/mysql/tools/test_content_review_profile.py` 使用本地 MySQL 容器的独立保留测试库，覆盖首次、重复、部分恢复、前置失败、登记失败回滚及中文 HEX。Java 测试为 `ContentReviewAccountProfileTest,ContentReviewBatchServiceTest`。

2026-10-13 本地 `yudao-mysql / ruoyi-vue-pro` 验证并执行：203 个未删除批次中 179 个补全，产品目标 226、产品形式 230、发布节奏 227，共 683 项；无新增瓶颈值。状态及非空快照对比、重复执行零变化、中文 HEX、两份 V316 版本记录均通过。备份：`backups/mysql/content-review-profile-20261013-171134/before.sql`。第一次执行曾因业务库排序规则与测试库不同报错，核实数据和版本登记全部回滚为零后，显式统一比较排序规则并在业务库相同规则下完成四组验证再执行。远程环境尚未执行，后端未重启，真实提交验收仍待部署。

## 2026-10-14 后续完整刷新

新的提交规则见 docs/api/content-review-batch.md 的完整账号快照规则。正式提交现刷新全部账号展示资料，包含名称、平台账号、平台、期段、状态及责任人。此变更不执行 V316 或其他历史补全；此前 V316 的执行记录仍是历史记录，不代表本次新增数据操作。

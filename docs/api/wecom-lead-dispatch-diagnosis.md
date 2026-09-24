# 企微派单修复：生产只读诊断与验收

## 已有证据与边界

用户提供的生产核查显示 assigned/reassigned 的站内信及企微规则和模板已配置，
对应 outbox 为零。代码原有提前 return 与该现象一致。规则 464 的 128 条失败是用户提供的
统计，本机未复查，尚无具体错误码证据，不能断言全部由空接收人引起。

2026-09-24 本机为 local 环境；配置中的数据库 TCP 不可达，未发现可用数据库凭据或 SSH
配置。生产部署版本、租户及 acceptTimeoutSeconds、464 错误分布均待生产连接恢复后核对。
用户随后明确要求生产检查“先不做”，本轮停止生产核查；以下步骤供后续授权执行。
不要输出凭据、用户姓名/手机号/企微 userid 或完整 outbox payload。

## 只读检查

以下查询在已确认目标数据库、租户及 UTF-8 客户端后运行；`@tenant_id` 由操作者设置为
核实的租户。规则 ID 464 仅是本次生产线索，先核对场景/模板，不在应用代码写死。
不执行重试、补发、规则更新或账号绑定操作。

```sql
SELECT r.id, r.scene_code, r.channel_code, r.status AS rule_status,
       t.code AS template_code, t.status AS template_status, t.wecom_message_type
FROM system_notify_rule r JOIN system_notify_template t ON t.id=r.template_id
WHERE r.tenant_id=@tenant_id AND r.deleted=0 AND t.deleted=0
  AND (r.scene_code IN ('zsjos.lead.assigned','zsjos.lead.reassigned') OR r.id=464);

SELECT code, status, JSON_EXTRACT(config_json,'$.acceptTimeoutSeconds') AS accept_timeout_seconds
FROM zsjos_lead_assignment_rule WHERE tenant_id=@tenant_id AND deleted=0 AND code='default';

SELECT status, SUBSTRING_INDEX(last_error, ':', 1) AS error_code,
       attempt_count, COUNT(*) AS records
FROM system_notify_business_outbox
WHERE tenant_id=@tenant_id AND target_rule_id=464 AND deleted=0
GROUP BY status, SUBSTRING_INDEX(last_error, ':', 1), attempt_count;

SELECT recipient.status, recipient.error_code, COUNT(*) AS recipients
FROM system_notify_business_outbox o
JOIN JSON_TABLE(o.payload, '$.recipients[*]' COLUMNS (
  status VARCHAR(32) PATH '$.status', error_code VARCHAR(100) PATH '$.errorCode'
)) recipient ON TRUE
WHERE o.tenant_id=@tenant_id AND o.target_rule_id=464 AND o.deleted=0
GROUP BY recipient.status, recipient.error_code;

SELECT o.id AS outbox_record, SUBSTRING_INDEX(o.last_error, ':', 1) AS error_code,
  CASE
    WHEN l.id IS NULL THEN 'lead_missing'
    WHEN l.source_type NOT IN ('sales_self_sourced','education_self_sourced') THEN 'not_self_sourced'
    WHEN COALESCE(l.source_provider_recorded,0)=0 THEN 'provider_not_recorded'
    WHEN l.source_provider_user_id IS NULL THEN 'provider_missing'
    WHEN COALESCE(l.provider_owner_type,'') <> 'system_user' THEN 'not_system_user_owner'
    WHEN NOT (l.source_provider_user_id <=> l.provider_owner_id) THEN 'provider_owner_mismatch'
    WHEN l.provider_owner_id = o.operator_user_id THEN 'operator_is_provider'
    ELSE 'eligible_by_current_record'
  END AS current_eligibility
FROM system_notify_business_outbox o
LEFT JOIN zsjos_lead l ON l.id=o.biz_id AND l.tenant_id=o.tenant_id AND l.deleted=0
WHERE o.tenant_id=@tenant_id AND o.target_rule_id=464 AND o.deleted=0
ORDER BY o.id DESC LIMIT 20;
```

当前业务记录可能晚于事件发生，资格查询只提供当前证据；历史成因须结合事件时点快照/审计，
不能用当前字段倒推历史。若属于不适用的 created 事件，无收件人与绑定失败是不同问题；
本轮仅记录诊断，不修改 464，也不补发其历史消息。

## 发布验收

- 核对部署制品对应提交、启用的 assigned/reassigned 规则与模板、实际接单窗口。
- 明确授权的测试接收人完成自动首次派单、拒单/超时重派、指定派单；记录派单、入队、
  逐人成功/跳过/失败、实际企微收件及点击接单的时间，禁止用总状态替代真实收件。
- 旧轮次卡片、已处理/换人/过期、无权限、跨租户请求不能接单；其他卡片及旧票据兼容。
- 若实际收件无法留出可用接单时间，阻断上线并确认业务时限，不擅自修改。
- 生产发布、服务重启、真实消息测试、关闭通知规则均需明确授权。无数据库迁移，无历史重放。

## 本地验证记录

后端编译通过；System 通知、ZSJOS 派单/时效/票据 55 个重点用例通过（两次针对性运行合计，不含既有失败用例）。完整 LeadNotifySceneProviderTest
存在既有场景数量断言失败（预期 44、当前 45），本次未增加场景且未改该断言。
Workbench 类型检查、生产构建、39 个相关 Vitest 用例通过。真实 Chromium 在 1280px/390px
使用隔离 API fixture 验证有效轮次、旧轮次、无权限、失败重试和接单参数，截图已检查。
这些是本地组件与单元验证，不替代真实数据库事务/并发、线上授权隔离、真实企微收件及延迟验收。


## 来源关联规则后续核对与本地修复（2026-09-24）

用户补充的生产统计：企微规则 464 为 129 条 `NOTIFY_RECIPIENT_MISSING` 失败、1 条成功；
站内规则 229 为 131 条失败、9 条成功。此为用户提供证据，本轮未连接生产复核。
不能据此认定规则从未生效或 isSelfSourced 白名单错误：提交契约明确普通新媒体、兼职、
自拓未选提供方不应发送来源关联通知。提供方是可选项，空值本身不证明前端漏填。

本轮修复将规则不适用与数据异常分开，新增终态 skipped 和稳定原因码，两个渠道共用
规则级扩展。仅默认来源关联角色组合适用，规则 ID 不写入代码；派单轮次检查保持独立。
原有 `NOTIFY_RECIPIENT_MISSING` 的可重试语义保持不变。当前仍 pending 的队列在后续消费时
采用新判断；既有 failed 记录不自动重新入队，不补发、不修改生产规则或业务数据。

生产已按用户要求暂缓：上线后需检查新 outbox 的 skipped/failed/succeeded 分布及实际收件。
历史 sourceProviderRecorded 为空仍是待溯源异常，不批量按当前推测修复。


本轮来源关联修复验证：64 个重点 Java 用例通过，覆盖双渠道适用性、正常空收件人继续重试、
具体数据异常、skipped 终态/查询、通知权限与租户查询、原派单和企微发送回归；Maven test
同时完成相关模块编译，局部 diff 检查通过。未进行真实数据库保留周期/调度集成验证，
未连接生产或发送真实企微消息；上线后的分布和实际收件仍待验收。

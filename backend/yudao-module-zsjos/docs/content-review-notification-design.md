# 内容审核通知系统设计文档

> 2026-09-20 实现校正：当前受支持契约见 [编导与运营通知](../../../docs/api/director-operator-notifications.md)。以下示例保留作历史设计参考；超时场景为 `zsjos.content_review.review_timeout_reminder`，由 `MediaNotificationReminderScheduler` 按 System 规则扫描真实 BPM 待办，不使用本文旧 Quartz Job。Workbench 统一入口为 `/zsjos/material-library/content-review?batchId=…`，Vue Admin 未支持的员工业务动作回退消息详情。


## 一、概述

本文档描述内容审核业务的通知系统设计，包括通知场景、收件人解析、跳转规则和实现细节。

## 二、核心组件

### 2.1 通知发布器（ContentReviewNotifyPublisher）

负责在审核流程的关键节点发送通知。

**位置**: `cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewNotifyPublisher`

**主要方法**:
- `publishBatchSubmitted()` - 批次提交通知
- `publishDirectorCompleted()` - 编导审核通过通知
- `publishDirectorRejected()` - 编导驳回通知
- `publishFinalCompleted()` - 终审通过通知
- `publishFinalRejected()` - 终审驳回通知
- `publishDirectorItemDecision()` - 编导逐条决策通知
- `publishFinalItemDecision()` - 终审逐条决策通知
- `publishReviewTimeout()` - 审核超时提醒

### 2.2 场景提供器（ContentReviewNotifySceneProvider）

定义通知场景和收件人解析规则。

**位置**: `cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewNotifySceneProvider`

**职责**:
- 注册通知场景定义
- 根据 payload 解析收件人列表
- 构建跳转链接参数

### 2.3 超时提醒任务（ContentReviewTimeoutNotifyJob）

定时检查超时未处理的审核批次并发送提醒。

**位置**: `cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewTimeoutNotifyJob`

**配置建议**:
- 执行频率：每小时一次
- Cron 表达式：`0 0 * * * ?`
- 超时阈值：24 小时（可配置）

## 三、通知场景详解

### 3.1 批次提交（batch_submitted）

**触发时机**: 运营提交审核批次后  
**收件人**: 编导审核人  
**优先级**: 高  
**跳转目标**: 待审核列表

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "itemCount": 素材数量,
  "operatorName": "提交人姓名",
  "directorUserIds": [编导用户ID列表]
}
```

**通知模板**:
- 标题: `【待审核】{batchTitle}`
- 内容: `{operatorName} 提交了 {itemCount} 个素材待您审核（批次 {batchNo}）`

---

### 3.2 编导审核通过（director_completed）

**触发时机**: 编导完成审核且全部通过  
**收件人**: 运营、终审人  
**优先级**: 普通  
**跳转目标**: 批次详情

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "directorName": "编导姓名",
  "operatorUserIds": [运营用户ID列表],
  "finalReviewerUserIds": [终审人用户ID列表]
}
```

---

### 3.3 编导驳回（director_rejected）

**触发时机**: 编导驳回批次  
**收件人**: 运营  
**优先级**: 中  
**跳转目标**: 批次详情（查看驳回原因）

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "directorName": "编导姓名",
  "reason": "驳回原因",
  "operatorUserIds": [运营用户ID列表]
}
```

---

### 3.4 终审通过（final_completed）

**触发时机**: 终审完成且全部通过  
**收件人**: 运营、编导  
**优先级**: 普通  
**跳转目标**: 批次详情

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "reviewerName": "终审人姓名",
  "operatorUserIds": [运营用户ID列表],
  "directorUserIds": [编导用户ID列表]
}
```

---

### 3.5 终审驳回（final_rejected）

**触发时机**: 终审驳回批次  
**收件人**: 运营、编导  
**优先级**: 中  
**跳转目标**: 批次详情（查看驳回原因）

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "reviewerName": "终审人姓名",
  "reason": "驳回原因",
  "operatorUserIds": [运营用户ID列表],
  "directorUserIds": [编导用户ID列表]
}
```

---

### 3.6 编导逐条决策（director_item_decision）

**触发时机**: 编导保存单条素材的审核意见  
**收件人**: 运营  
**优先级**: 普通  
**跳转目标**: 批次详情对应条目

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "itemTitle": "素材标题",
  "decision": "通过/退回",
  "comment": "审核意见",
  "directorName": "编导姓名",
  "operatorUserIds": [运营用户ID列表]
}
```

---

### 3.7 终审逐条决策（final_item_decision）

**触发时机**: 终审人保存单条素材的审核意见  
**收件人**: 运营  
**优先级**: 普通  
**跳转目标**: 批次详情对应条目

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "itemTitle": "素材标题",
  "decision": "通过/退回",
  "comment": "审核意见",
  "reviewerName": "终审人姓名",
  "operatorUserIds": [运营用户ID列表]
}
```

---

### 3.8 审核超时提醒（review_timeout）

**触发时机**: 审核超过设定时长未处理（定时任务触发）  
**收件人**: 当前审核人 + 主管  
**优先级**: 高  
**跳转目标**: 待办列表

**Payload 数据**:
```json
{
  "batchNo": "批次编号",
  "batchTitle": "批次标题",
  "timeoutHours": 超时小时数,
  "currentStage": "编导审核/终审",
  "directorUserIds": [编导用户ID列表（编导审核阶段）],
  "finalReviewerUserIds": [终审人用户ID列表（终审阶段）]
}
```

---

## 四、收件人解析规则

### 4.1 运营（operatorUserIds）

**来源**: `ContentReviewBatchDO.operatorUserId`

批次的运营负责人，即创建批次并提交审核的用户。

### 4.2 编导（directorUserIds）

**来源**: `ContentReviewBatchDO.directorUserId`

批次分配的编导审核人。在提交审核时根据关系表（`zsjos_user_relation`）自动分配。

### 4.3 终审人（finalReviewerUserIds）

**来源**: BPM 流程定义中终审节点的候选人

从流程定义中获取终审节点的候选用户或角色，动态解析为实际用户 ID 列表。

### 4.4 主管（supervisorUserIds）

**来源**: `zsjos_user_relation` 表中的上级关系

在超时提醒等场景中，可以查询审核人的上级主管并加入收件人列表。

---

## 五、前端跳转规则

### 5.1 路由映射

| 目标页面 | 路由路径 | 参数 |
|---------|---------|------|
| 待审核列表 | `/content-review/todo` | - |
| 批次详情 | `/content-review/batch/{batchId}` | `batchId` |
| 批次条目 | `/content-review/batch/{batchId}?itemId={itemId}` | `batchId`, `itemId` |

### 5.2 跳转参数构建

通知系统提供以下字段供前端构建跳转链接：

- `bizType`: `content-review-batch`
- `bizId`: 批次 ID
- `action`: 操作类型（`view`, `review`, `item`）
- `itemId`: 条目 ID（可选，仅条目级通知）

**示例**:
```javascript
// 批次详情
const url = `/content-review/batch/${bizId}`;

// 批次条目
const url = `/content-review/batch/${bizId}?itemId=${itemId}`;
```

---

## 六、通知优先级与推送渠道

### 6.1 优先级定义

- **高优先级**: `batch_submitted`, `review_timeout`
- **中优先级**: `director_rejected`, `final_rejected`
- **普通优先级**: `director_completed`, `final_completed`, `director_item_decision`, `final_item_decision`

### 6.2 推送渠道

#### 站内消息
所有场景均发送站内消息。

#### 企业微信（可选）
- 高优先级场景：实时推送
- 中优先级场景：汇总推送（每小时一次）
- 普通优先级场景：仅站内消息

---

## 七、实现集成点

### 7.1 业务方法集成

在以下业务方法中调用通知发布器：

| 业务方法 | 通知方法 | 位置 |
|---------|---------|------|
| `submit()` | `publishBatchSubmitted()` | `ContentReviewBatchService:578` |
| `saveDirectorDecision()` | `publishDirectorItemDecision()` | `ContentReviewBatchService:610` |
| `saveFinalDecision()` | `publishFinalItemDecision()` | `ContentReviewBatchService:636` |
| `completeDirector()` | `publishDirectorCompleted()` 或 `publishDirectorRejected()` | `ContentReviewBatchService:660, 666` |
| `completeFinal()` | `publishFinalCompleted()` 或 `publishFinalRejected()` | `ContentReviewBatchService:691, 697` |

### 7.2 定时任务配置

在 XXL-Job 管理后台配置超时提醒任务：

- **任务名称**: `contentReviewTimeoutNotifyJob`
- **执行器**: 默认执行器
- **Cron 表达式**: `0 0 * * * ?`（每小时执行一次）
- **运行模式**: BEAN
- **JobHandler**: `contentReviewTimeoutNotifyJob`

---

## 八、扩展性设计

### 8.1 新增通知场景

1. 在 `ContentReviewNotifyPublisher` 中添加发布方法
2. 在 `ContentReviewNotifySceneProvider` 中注册场景定义
3. 在业务方法中调用发布方法
4. 更新通知模板文档

### 8.2 自定义收件人逻辑

在 `ContentReviewNotifySceneProvider.getRecipientUserIds()` 中添加自定义解析逻辑：

```java
if ("custom_scene".equals(sceneCode)) {
    // 自定义收件人解析
    return customRecipientResolver.resolve(payload);
}
```

### 8.3 多渠道推送

通知系统支持扩展到多个推送渠道：

- 站内消息（默认）
- 企业微信
- 钉钉
- 邮件
- 短信

通过配置 `NotifyChannelConfig` 可以为不同场景指定不同渠道。

---

## 九、测试建议

### 9.1 单元测试

测试通知发布器的各个方法：

```java
@Test
void testPublishBatchSubmitted() {
    Long batchId = 1L;
    Long userId = 100L;
    LocalDateTime now = LocalDateTime.now();
    
    notifyPublisher.publishBatchSubmitted(batchId, userId, now);
    
    // 验证通知事件已发布
    verify(notifyBusinessEventApi).publish(any(NotifyBusinessEvent.class));
}
```

### 9.2 集成测试

测试完整的审核流程中通知的触发：

```java
@Test
void testReviewFlowWithNotifications() {
    // 1. 创建批次
    Long batchId = createBatch();
    
    // 2. 提交审核
    batchService.submit(batchId, request, operatorUserId);
    // 验证编导收到通知
    
    // 3. 编导审核
    batchService.completeDirector(batchId, request, directorUserId);
    // 验证运营和终审人收到通知
    
    // 4. 终审
    batchService.completeFinal(batchId, request, finalUserId);
    // 验证运营和编导收到通知
}
```

### 9.3 超时任务测试

测试超时提醒任务的执行：

```java
@Test
void testTimeoutNotifyJob() {
    // 创建一个超时的批次
    Long batchId = createTimeoutBatch();
    
    // 执行定时任务
    timeoutNotifyJob.execute();
    
    // 验证超时通知已发送
    verify(notifyPublisher).publishReviewTimeout(eq(batchId), anyString(), anyInt());
}
```

---

## 十、监控与告警

### 10.1 通知发送成功率

监控通知发送的成功率，及时发现通知系统异常。

**指标**:
- `content_review.notify.success_rate`
- `content_review.notify.failure_count`

### 10.2 超时批次数量

监控超时未处理的批次数量，评估审核效率。

**指标**:
- `content_review.timeout.director_count`
- `content_review.timeout.final_count`

### 10.3 通知延迟

监控通知从事件发生到用户收到的延迟时间。

**指标**:
- `content_review.notify.latency_p50`
- `content_review.notify.latency_p99`

---

## 十一、常见问题

### Q1: 为什么收不到通知？

**排查步骤**:
1. 检查通知发布器是否被正确调用（查看日志）
2. 检查收件人解析逻辑是否正确（查看 payload）
3. 检查通知服务是否正常（查看通知服务日志）
4. 检查用户通知设置是否关闭了该类型通知

### Q2: 如何避免重复通知？

通知系统使用 `sourceEventKey` 作为幂等键，相同的事件不会重复发送。

**示例**:
```java
String sourceEventKey = "batch:" + batchId + ":submitted";
```

### Q3: 如何自定义通知模板？

通知模板由系统通知服务管理，可以在管理后台配置模板内容：

1. 进入系统管理 > 通知管理 > 模板管理
2. 找到对应场景的模板
3. 编辑标题和内容模板
4. 保存并发布

---

## 十二、总结

内容审核通知系统提供了完整的通知能力，覆盖审核流程的各个关键节点：

✅ **8 个通知场景**，覆盖提交、审核、驳回、超时等关键节点  
✅ **精确的收件人解析**，运营、编导、终审人、主管各司其职  
✅ **灵活的跳转规则**，通知直达操作区  
✅ **超时提醒机制**，防止审核任务遗漏  
✅ **扩展性设计**，支持新增场景和多渠道推送

通过本通知系统，可以大幅提升审核流程的协作效率和用户体验。

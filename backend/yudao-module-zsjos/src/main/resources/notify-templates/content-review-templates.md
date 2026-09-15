# 内容审核通知模板配置

本文件定义内容审核业务的通知场景和模板，供系统通知服务使用。

## 场景列表

### 1. batch_submitted - 批次已提交
**触发时机**: 运营提交审核批次后
**收件人**: 编导审核人
**跳转目标**: 待审核列表

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `itemCount`: 素材数量
- `operatorName`: 提交人姓名

**标题模板**: `【待审核】{batchTitle}`
**内容模板**: 
```
{operatorName} 提交了 {itemCount} 个素材待您审核（批次 {batchNo}）
```

---

### 2. director_completed - 编导审核通过
**触发时机**: 编导完成审核且全部通过
**收件人**: 运营、终审人
**跳转目标**: 批次详情

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `directorName`: 编导姓名

**标题模板**: `【已通过】{batchTitle}`
**内容模板**: 
```
{directorName} 已审核通过批次 {batchNo}，进入终审环节
```

---

### 3. director_rejected - 编导审核驳回
**触发时机**: 编导驳回批次
**收件人**: 运营
**跳转目标**: 批次详情（查看驳回原因）

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `directorName`: 编导姓名
- `reason`: 驳回原因

**标题模板**: `【已驳回】{batchTitle}`
**内容模板**: 
```
{directorName} 驳回了批次 {batchNo}
驳回原因：{reason}
```

---

### 4. final_completed - 终审通过
**触发时机**: 终审完成且全部通过
**收件人**: 运营、编导
**跳转目标**: 批次详情

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `reviewerName`: 终审人姓名

**标题模板**: `【终审通过】{batchTitle}`
**内容模板**: 
```
{reviewerName} 已完成终审，批次 {batchNo} 全部通过
```

---

### 5. final_rejected - 终审驳回
**触发时机**: 终审驳回批次
**收件人**: 运营、编导
**跳转目标**: 批次详情（查看驳回原因）

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `reviewerName`: 终审人姓名
- `reason`: 驳回原因

**标题模板**: `【终审驳回】{batchNo}`
**内容模板**: 
```
{reviewerName} 驳回了批次 {batchNo}
驳回原因：{reason}
```

---

### 6. director_item_decision - 编导逐条决策
**触发时机**: 编导保存逐条审核意见
**收件人**: 运营
**跳转目标**: 批次详情对应条目

**模板变量**:
- `batchNo`: 批次编号
- `itemTitle`: 素材标题
- `decision`: 决策（通过/退回）
- `comment`: 审核意见
- `directorName`: 编导姓名

**标题模板**: `【审核意见】{itemTitle}`
**内容模板**: 
```
{directorName} 对素材 "{itemTitle}" 的审核意见：{decision}
意见：{comment}
```

---

### 7. final_item_decision - 终审逐条决策
**触发时机**: 终审人保存逐条审核意见
**收件人**: 运营
**跳转目标**: 批次详情对应条目

**模板变量**:
- `batchNo`: 批次编号
- `itemTitle`: 素材标题
- `decision`: 决策（通过/退回）
- `comment`: 审核意见
- `reviewerName`: 终审人姓名

**标题模板**: `【终审意见】{itemTitle}`
**内容模板**: 
```
{reviewerName} 对素材 "{itemTitle}" 的终审意见：{decision}
意见：{comment}
```

---

### 8. review_timeout - 审核超时提醒
**触发时机**: 审核超过设定时长未处理
**收件人**: 当前审核人 + 主管
**跳转目标**: 待办列表

**模板变量**:
- `batchNo`: 批次编号
- `batchTitle`: 批次标题
- `timeoutHours`: 超时小时数
- `currentStage`: 当前环节（编导审核/终审）

**标题模板**: `【超时提醒】{batchTitle}`
**内容模板**: 
```
批次 {batchNo} 在 {currentStage} 环节已超时 {timeoutHours} 小时，请尽快处理
```

---

## 跳转路径配置

### 前端路由映射
```
待审核列表: /content-review/todo
批次详情: /content-review/batch/{batchId}
批次条目: /content-review/batch/{batchId}?itemId={itemId}
```

### 路径参数
- `batchId`: 批次 ID
- `itemId`: 条目 ID（可选）

---

## 通知优先级

- **高优先级**: batch_submitted, review_timeout
- **中优先级**: director_rejected, final_rejected
- **普通优先级**: director_completed, final_completed, director_item_decision, final_item_decision

---

## 推送渠道

### 站内消息
所有场景均发送站内消息

### 企业微信（可选）
- 高优先级场景：实时推送
- 中优先级场景：汇总推送（每小时一次）
- 普通优先级场景：仅站内消息

---

## 实现说明

1. **场景定义**: 在 `ContentReviewNotifySceneProvider` 中实现
2. **模板渲染**: 由系统通知服务根据 payload 数据渲染
3. **收件人解析**: 根据批次数据动态解析
4. **跳转链接**: 前端根据 bizType + bizId + action 构建

---

## 扩展场景（待实现）

- 批次撤回通知
- 批次发布成功通知
- 素材入库成功通知
- 定期待办汇总（每日/每周）

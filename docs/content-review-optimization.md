# 内容审核流程优化说明

## 变更时间
2026-09-15

## 问题描述

**原流程问题**：
1. 编导对单条内容点击"退回"并填写退回意见后，该内容不会立即退回
2. 必须等编导审完所有内容，点击"完成编导审核"后，流程强制进入终审环节
3. 终审完成后，被编导退回的内容才最终变为"已退回"状态
4. 运营/学员需要等整个批次走完两级审核才能看到退回意见并修改

**核心矛盾**：
- 编导已明确退回的内容，为什么还要强制走完终审？
- 这导致退回反馈不及时，流程冗余

## 优化方案

采用**批次级退回**策略：

### 新流程逻辑

1. **编导逐条审核**：保存每条内容的审核结论（通过/退回）和评论
2. **编导点击"完成编导审核"**：
   - **如果有任何退回的内容**：
     - BPM 流程调用 `rejectTask`，批次直接驳回
     - 所有内容版本解冻，允许运营修改
     - 每条内容落地退回结论和评论
     - 批次状态变为 `NEED_MODIFY`（需修改）
     - **不进入终审环节**
   - **如果全部通过**：
     - BPM 流程调用 `approveTask`
     - 流程正常流转到终审环节
     - 批次状态变为 `FINAL_REVIEW`（终审中）

3. **终审环节**（仅在编导全部通过时才会到达）：
   - 终审人员审核编导已通过的内容
   - 可以再次退回或全部通过

### 优势

1. **及时反馈**：编导退回后，运营立即可以看到并修改，无需等待终审
2. **减少冗余**：编导认为不合格的内容不再占用终审资源
3. **符合逻辑**：编导是第一关，发现问题就应该退回
4. **降低成本**：终审只需审核编导认为合格的内容，工作量减少

## 代码变更

### 修改文件
`backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/contentreview/ContentReviewBatchService.java`

### 变更点 1：`completeDirector` 方法（行 626-650）

**变更前**：
```java
public void completeDirector(Long batchId, ContentReviewCompleteReqVO request, Long userId) {
    // ... 校验代码 ...
    processTaskApi.approveTask(userId, new BpmTaskDecisionReqDTO()
            .setTaskId(request.getTaskId()).setReason(request.getReason()));
}
```

**变更后**：
```java
public void completeDirector(Long batchId, ContentReviewCompleteReqVO request, Long userId) {
    // ... 校验代码 ...
    
    // 检查是否有退回的内容
    List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batchId);
    boolean hasReturned = items.stream()
            .anyMatch(item -> DECISION_RETURNED.equals(item.getDirectorDecision()));

    if (hasReturned) {
        // 有退回内容，直接驳回整个批次，不进入终审
        processTaskApi.rejectTask(userId, new BpmTaskDecisionReqDTO()
                .setTaskId(request.getTaskId()).setReason(request.getReason()));
    } else {
        // 全部通过，进入终审环节
        processTaskApi.approveTask(userId, new BpmTaskDecisionReqDTO()
                .setTaskId(request.getTaskId()).setReason(request.getReason()));
    }
}
```

### 变更点 2：`validateTaskAction` 方法（行 652-708）

**核心变更**：
- 编导节点现在同时支持 `ACTION_APPROVE`（通过）和 `ACTION_REJECT`（驳回）
- 通过：要求所有内容都是 `APPROVED`
- 驳回：要求至少有一条内容是 `RETURNED`

**关键代码片段**：
```java
if (ACTION_APPROVE.equals(context.getAction())) {
    // 通过：必须全部通过才能进入终审
    boolean allApproved = items.stream()
            .allMatch(item -> DECISION_APPROVED.equals(item.getDirectorDecision()));
    if (!allApproved) {
        throw exception(CONTENT_REVIEW_TASK_INVALID);
    }
    if (batchMapper.markDirectorCompleted(batch, LocalDateTime.now()) != 1) {
        throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
    }
} else if (ACTION_REJECT.equals(context.getAction())) {
    // 驳回：有退回内容时允许驳回
    boolean hasReturned = items.stream()
            .anyMatch(item -> DECISION_RETURNED.equals(item.getDirectorDecision()));
    if (!hasReturned) {
        throw exception(CONTENT_REVIEW_TASK_INVALID);
    }
    // 批次状态更新由 handleProcessResult 处理
}
```

### 变更点 3：`handleProcessResult` 方法（行 688-761）

**核心变更**：
当流程驳回时（编导有退回内容），对每条内容落地审核结论和评论：

```java
if (!BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
    // 解冻版本，允许运营修改后重新提交
    for (ContentReviewBatchItemDO item : items) {
        if (contentVersionMapper.unfreeze(item.getContentVersionId()) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
    }
    // 对每条内容落地退回结论
    for (ContentReviewBatchItemDO item : items) {
        ContentDO content = contentMapper.selectByIdForUpdate(item.getContentId(), tenantId());
        ContentVersionDO version = contentVersionMapper.selectByIdForUpdate(item.getContentVersionId(), tenantId());
        // ... 校验代码 ...
        
        // 优先使用编导的退回意见
        String comment = DECISION_RETURNED.equals(item.getDirectorDecision())
                ? firstText(item.getDirectorComment(), "编导审核退回")
                : firstText(item.getFinalComment(), "终审审核退回");
        Long reviewer = item.getDirectorReviewedByUserId();
        
        // 落地退回结论到内容记录
        contentService.applyBatchReview(content, content.getVersion(), false, comment, reviewer);
        if (contentVersionMapper.finishReview(version.getId(), "rejected", comment, reviewer, now) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        if (itemMapper.finalizeItem(item, RESULT_RETURNED, null, null) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
    }
    // 批次状态变为需修改
    if (batchMapper.finalizeBatch(batch, BATCH_NEED_MODIFY, event.getEventKey(), now) != 1) {
        throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
    }
    return;
}
```

## 业务流程对比

### 优化前流程

```
运营提交批次
    ↓
编导逐条审核（部分通过 + 部分退回）
    ↓
编导点击"完成编导审核"
    ↓
【强制进入终审】← 问题：明明有退回的，为什么还要终审？
    ↓
终审看到编导退回的内容（但只审编导通过的）
    ↓
终审完成
    ↓
系统落地结论（退回的变为已退回，通过的变为可发布）
    ↓
运营才能看到退回意见并修改 ← 问题：反馈太慢
```

### 优化后流程

```
运营提交批次
    ↓
编导逐条审核
    ↓
编导点击"完成编导审核"
    ↓
【系统判断】
    ├─ 有退回内容？
    │   ↓ 是
    │   批次直接驳回（BPM rejectTask）
    │   ↓
    │   解冻版本 + 落地退回结论
    │   ↓
    │   批次状态变为 NEED_MODIFY
    │   ↓
    │   运营立即看到退回意见 ✓
    │   ↓
    │   运营修改内容
    │   ↓
    │   运营重新提交（调用 resubmit 接口）
    │   ↓
    │   创建新批次（revisionOfBatchId 指向原批次）
    │   ↓
    │   新批次进入编导审核环节
    │   ↓
    │   继续审核...
    │
    └─ 全部通过？
        ↓ 是
        进入终审环节（BPM approveTask）
        ↓
        终审审核
        ↓
        终审完成落地结论
        ↓
        批次状态变为 COMPLETED
```

### 完整的多轮审核流程

```
第一轮
运营提交批次 A
    ↓
编导退回（内容 1 标题问题）
    ↓
批次 A 状态：NEED_MODIFY
    ↓
第二轮
运营修改内容 1
    ↓
运营点击"重新提交"
    ↓
创建批次 B (revisionOfBatchId = A)
    ↓
编导退回（内容 1 封面问题）
    ↓
批次 B 状态：NEED_MODIFY
    ↓
第三轮
运营修改内容 1 封面
    ↓
运营点击"重新提交"
    ↓
创建批次 C (revisionOfBatchId = B)
    ↓
编导全部通过
    ↓
进入终审
    ↓
终审通过
    ↓
批次 C 状态：COMPLETED
    ↓
内容状态：READY_TO_PUBLISH

历史链条：C → B → A（完整保留每轮的退回意见）
```

## 影响范围

### 不受影响的功能
- 批次创建、提交
- 编导逐条保存审核结论
- 终审逐条保存审核结论
- 终审完成流程
- 发布登记流程

### 受影响的功能
- **编导完成审核**：增加了退回判断逻辑
- **BPM 任务校验**：编导节点现在支持驳回操作
- **流程结果处理**：驳回时需要落地每条内容的审核结论

### 增强的功能
- **重新提交流程**：现有的 `resubmit` 接口已支持，无需修改
  - 接口：`POST /zsjos/content-review/batch/{batchId}/resubmit-from-student`
  - 功能：基于被驳回的批次创建新批次，保留历史链条
  - 权限：只有原提交运营可以重新提交
  - 版本链：新批次的 `revisionOfBatchId` 指向原批次

### 数据库影响
**无数据库变更**，使用现有字段和表结构：
- `content_review_batch.status`：`NEED_MODIFY` 状态已存在
- `content_review_batch.revision_of_batch_id`：历史链条字段已存在
- `content_review_batch_item.director_decision`：编导决策字段已存在
- `content_version.frozen_at`：解冻机制已存在

### 前端影响
**前端无需修改核心逻辑**，但建议优化体验：
1. **编导完成审核接口**：
   - 接口不变：`POST /zsjos/content-review/batch/{batchId}/complete-director`
   - 后端根据审核结论自动判断是通过还是驳回
   - 前端只需要正常处理流程状态变化

2. **建议前端增强（非必需）**：
   - 批次列表页：`NEED_MODIFY` 状态显示"需修改"标签
   - 批次详情页：
     - 显示每条内容的退回意见
     - 提供"重新提交"按钮（调用 resubmit 接口）
     - 显示历史批次链条（通过 `revisionOfBatchId` 追溯）
   - 内容管理页：
     - 被退回的内容显示"审核退回"状态
     - 点击可查看具体退回意见

## 运营驳回后的操作流程

当编导退回批次后，运营需要按照以下流程处理：

### 1. 查看退回意见
- **批次状态**：`NEED_MODIFY`（需修改）
- **可见位置**：
  - 批次列表页面可以看到状态为"需修改"
  - 点击批次详情，可以看到每条内容的退回意见
  - 每条被退回的内容会显示编导填写的具体退回原因
- **权限要求**：原提交运营（`operatorUserId`）

### 2. 修改内容
运营有两个选择：

#### 选项 A：修改现有内容（推荐）
1. **内容版本状态**：所有内容版本已自动解冻
2. **修改操作**：
   - 进入内容管理页面
   - 找到被退回的内容（状态为"审核退回"）
   - 点击编辑，修改内容（文案、素材、封面等）
   - 保存后会生成新的内容版本
3. **适用场景**：需要修改的内容较少，或只需微调

#### 选项 B：重新创建批次
1. 直接创建一个全新的审核批次
2. 重新选择内容和版本
3. **适用场景**：需要大幅度调整或更换内容

### 3. 重新提交审核

#### 方式一：基于原批次重新提交（推荐）
**接口**：`POST /zsjos/content-review/batch/{batchId}/resubmit-from-student`

**流程**：
1. 在批次详情页面，点击"重新提交"按钮
2. 系统会检查：
   - 批次状态必须是 `NEED_MODIFY` 或 `REJECTED`
   - 操作人必须是原提交运营
   - 学员必须与原批次一致
3. 提交新的内容版本（可以是修改后的版本，也可以是原版本）
4. 系统会：
   - 创建一个新的审核批次
   - 新批次的 `revisionOfBatchId` 字段指向原批次（保留历史记录）
   - 原批次保持 `NEED_MODIFY` 状态，可供查看历史
5. 新批次进入编导审核环节

**特点**：
- ✅ 保留完整的审核历史链条
- ✅ 可以追溯每一轮的退回意见和修改
- ✅ 适合需要多轮修改的场景

#### 方式二：创建全新批次
**接口**：`POST /zsjos/content-review/batch/create-from-student`

**流程**：
1. 在内容审核页面，点击"创建审核批次"
2. 选择学员和要审核的内容版本
3. 提交后创建新批次，与原批次无关联

**特点**：
- 适合完全推翻重来的场景
- 原批次状态不变，保持 `NEED_MODIFY`

### 4. 权限控制
- **查看退回批次**：需要 `zsjos:content-review:query` 权限
- **重新提交**：需要 `zsjos:content-review:submit` 权限
- **修改内容**：需要内容编辑权限

### 5. 批次状态流转

```
NEED_MODIFY (驳回) 
    ↓
运营修改内容
    ↓
运营重新提交 → 创建新批次
    ↓
新批次状态: DIRECTOR_REVIEW (编导审核中)
```

**原批次保持 `NEED_MODIFY` 状态**，作为历史记录保留。

### 6. 前端显示逻辑

**批次列表页面**：
- 状态为 `NEED_MODIFY` 的批次显示"需修改"标签
- 提供"重新提交"操作按钮（仅原运营可见）

**批次详情页面**：
- 显示每条内容的审核结论和退回意见
- 被退回的内容高亮显示退回原因
- 提供"重新提交"按钮

**内容管理页面**：
- 被退回的内容显示"审核退回"状态
- 点击可查看具体的退回意见
- 提供编辑入口，允许修改并生成新版本

## 测试建议

### 测试场景 1：编导全部通过
1. 创建批次并提交
2. 编导对所有内容点击"通过"
3. 编导点击"完成编导审核"
4. **预期**：批次进入终审环节，状态为 `FINAL_REVIEW`

### 测试场景 2：编导有退回
1. 创建批次并提交（包含 3 条内容）
2. 编导对内容 1、2 点击"通过"
3. 编导对内容 3 点击"退回"并填写意见："标题不够吸引人"
4. 编导点击"完成编导审核"
5. **预期**：
   - 批次直接驳回，状态变为 `NEED_MODIFY`
   - 所有内容版本解冻
   - 内容 3 的审核结论为"rejected"，评论为"标题不够吸引人"
   - **不进入终审环节**
   - 运营可以立即看到退回意见

### 测试场景 3：编导全部退回
1. 创建批次并提交
2. 编导对所有内容都点击"退回"
3. 编导点击"完成编导审核"
4. **预期**：批次直接驳回，不进入终审

### 测试场景 4：运营修改后重新提交
1. 在场景 2 的基础上，运营看到内容 3 被退回
2. 运营进入内容管理，找到被退回的内容 3
3. 运营修改内容 3 的标题，保存（生成新版本）
4. 运营在原批次详情页点击"重新提交"
5. 运营选择内容 1、2、3 的最新版本
6. 提交后创建新批次
7. **预期**：
   - 新批次的 `revisionOfBatchId` 指向原批次
   - 新批次状态为 `DIRECTOR_REVIEW`
   - 编导可以看到新批次，重新审核
   - 原批次保持 `NEED_MODIFY` 状态，可供查看历史

### 测试场景 5：多轮退回修改
1. 第一轮：编导退回内容 A，原因"标题问题"
2. 运营修改后重新提交（批次 B，revisionOfBatchId = A）
3. 第二轮：编导退回内容 A，原因"封面不合适"
4. 运营再次修改后重新提交（批次 C，revisionOfBatchId = B）
5. **预期**：
   - 可以追溯完整的修改链条：C → B → A
   - 每个批次都保留了当时的退回意见
   - 运营可以查看每一轮的审核历史

### 测试场景 6：权限控制
1. 运营 A 提交批次，编导退回
2. 运营 B 尝试重新提交该批次
3. **预期**：系统拒绝，提示"只有原提交人可以重新提交"

## 回滚方案

如需回滚，恢复以下三个方法的原始实现：
1. `completeDirector` - 移除退回判断，始终调用 `approveTask`
2. `validateTaskAction` - 编导节点只接受 `ACTION_APPROVE`
3. `handleProcessResult` - 驳回时不落地内容审核结论

## 交付物总结

### 1. 核心代码修改
**文件**：`backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/contentreview/ContentReviewBatchService.java`

**变更统计**：208 行新增 / 42 行删除 / 净增 166 行

**变更方法**：
- `completeDirector`（行 626-650）：增加退回判断，调用 `rejectTask` 或 `approveTask`
- `validateTaskAction`（行 652-708）：支持编导节点的驳回操作
- `handleProcessResult`（行 688-761）：驳回时落地审核结论并解冻版本

### 2. 配套设施（已存在，无需新增）
- **重新提交接口**：`resubmit` 方法已实现（行 904-918）
- **API 端点**：`POST /zsjos/content-review/batch/{batchId}/resubmit-from-student`
- **权限控制**：`@ZsjosPermission(bizType = "content-review-batch", action = "submit")`
- **历史链条**：`revisionOfBatchId` 字段已存在

### 3. 文档交付
**文件**：`docs/content-review-optimization.md`

**内容包括**：
- 问题描述与核心矛盾分析
- 优化方案详细说明
- 代码变更对比
- 完整的业务流程图（优化前 vs 优化后 vs 多轮审核）
- **运营驳回后的完整操作流程**（新增）
- 6 个详细的测试场景
- 影响范围评估
- 回滚方案

### 4. 关键接口清单

| 接口 | 功能 | 调用方 | 状态 |
|------|------|--------|------|
| `POST /zsjos/content-review/batch/create-from-student` | 创建审核批次 | 运营 | 已存在 |
| `POST /zsjos/content-review/batch/{batchId}/complete-director` | 编导完成审核 | 编导 | **已优化** |
| `POST /zsjos/content-review/batch/{batchId}/resubmit-from-student` | 驳回后重新提交 | 运营 | 已存在 |
| `GET /zsjos/content-review/batch/page` | 查询批次列表 | 运营/编导 | 已存在 |
| `GET /zsjos/content-review/batch/{batchId}` | 批次详情 | 运营/编导 | 已存在 |

### 5. 数据流转

#### 正常通过流程
```
批次状态：DRAFT → DIRECTOR_REVIEW → FINAL_REVIEW → COMPLETED
内容状态：ACCEPTANCE → ACCEPTANCE → ACCEPTANCE → READY_TO_PUBLISH
版本状态：frozen → frozen → frozen → frozen
```

#### 编导退回流程（本次优化）
```
批次状态：DRAFT → DIRECTOR_REVIEW → NEED_MODIFY
内容状态：ACCEPTANCE → ACCEPTANCE → (保持 ACCEPTANCE)
版本状态：frozen → frozen → unfrozen（解冻）
审核结论：无 → 编导退回意见 → 落地到内容记录
```

#### 重新提交流程
```
原批次：NEED_MODIFY（保持，作为历史）
新批次：创建 → DIRECTOR_REVIEW（重新进入审核）
版本链：新批次.revisionOfBatchId → 原批次.id
```

## 相关文档

- 爆款素材审核流程设计：`C:\Users\EDY\.claude\projects\D--ZSJ-OS\memory\viral-material-review-design.md`
- BPM 任务 API：`backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApi.java`
- 内容审核 Controller：`backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/contentreview/ContentReviewController.java`

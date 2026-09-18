# 审批中心业务内容展示（方案 B）

## 目标

审批中心（workbench）不再显示英文流程变量键名，改为展示业务内容：列表显示"业务单据标题 + 关键字段摘要"，详情显示"业务详情卡"。数据来自业务表实时读取，不落冗余快照。

## 已确认的边界

1. **契约放公共位置** — 原计划是"纯 zsjos 增量"，后因 EAM 也要接入，契约上移到
   `yudao-module-bpm` 的 `api/approvalcontent` 包（zsjos 与 eam 都已依赖 bpm）。
   各业务域在**自己的模块**里实现 Provider，共用一套注册表与端点。
2. **只改 workbench** — admin 侧 Vue 页面本次不动。
3. **逐任务校验** — 每个任务独立走权限校验，不因为批量而放宽。

## 根因（现状）

- `FlowableUtils.getSummary()`（`bpm/framework/flowable/core/util/FlowableUtils.java:249-255`）只在 `formType=NORMAL` 时返回摘要；zsjos 全部流程是 `CUSTOM`，所以摘要恒为 null。
- workbench 是 React，无法加载业务表单指向的 Vue 组件（`formCustomViewPath`），只好退化到 `Object.entries(formVariables)`，直接把变量 key 当 label 渲染。
- 业务金额/状态/人名**根本不在流程变量里**（12 个域中只有提现带了 `applicationAmount`），必须按 businessKey 回查业务表。

## 架构

```
workbench 审批中心
  └─ POST /bpm/approval-content/business-summary-batch   { taskIds[], view }
       └─ BpmApprovalContentService                      (yudao-module-bpm)
            ├─ 逐 taskId 调 BpmProcessTaskApi.getTodoTask/getDoneTask  → 校验归属 + 拿 businessKey
            ├─ BpmApprovalContentRegistry 按前缀选 Provider
            └─ provider.brief(bizId, viewerId)  → 中文标题 + 字段列表
  └─ GET  /bpm/approval-content/business-detail?taskId=&view=
       └─ provider.detail(bizId, viewerId)  → 结构化详情卡（分组/字段/跳转）
```

**Provider 契约**（`bpm/api/approvalcontent/BpmApprovalContentProvider.java`）：

```java
public interface BpmApprovalContentProvider {
    String bizType();                 // "withdrawal"
    String businessKeyPrefix();       // "withdrawal:"
    String parseBusinessId(String businessKey);   // 各域格式不同，自己解析
    BpmApprovalBriefVO brief(String businessId, Long viewerId);
    BpmApprovalDetailVO detail(String businessId, Long viewerId);
}
```

注册表构造器收集所有模块的 Provider（Spring 扫描 `**.module` 全包），按**前缀长度倒序**匹配——因为 `student-contact-extension:` 和 `student-delivery-defer:` 前缀相似、`media-rebind:x:v1` 是三段式，必须最长前缀优先。前缀重复会让应用**启动失败**。

## 业务域清单

| 业务域 | businessKey 前缀 | 流程 key | Provider 所在模块 |
|---|---|---|---|
| 提现 | `withdrawal:` | `zsjos_partner_withdrawal` | zsjos |
| 线索申诉 | `lead-appeal:` | `zsjos_lead_appeal_review` | zsjos |
| 销售订单 | `sales-order:` | `zsjos_sales_order_dual_approval` | zsjos |
| 素材评审 | `material-version:` | 动态（按素材类型） | zsjos |
| 内容审核 | `content-review-batch:` | `zsjos_production_content_review` | zsjos |
| 转班 | `class-transfer:` | `zsjos_class_transfer` | zsjos |
| 线索转移 | `lead-transfer:` | `zsjos_lead_transfer_request` | zsjos |
| 学员交付延期 | `student-delivery-defer:` | `zsjos_student_contact_extension` ⚠️ | zsjos |
| 学员联系延期 | `student-contact-extension:` | `zsjos_student_contact_extension` ⚠️ | zsjos |
| 媒体账号重绑 | `media-rebind:`（三段） | `zsjos_media_rebind` | zsjos |
| 反馈需求 | `feedback:`（四段） | `zsjos_feedback_requirement_approval` | zsjos |
| **EAM 资产转移** | `asset-transfer:`（四段，id 在**第 2 段**） | `eam_asset_transfer` | **eam** |
| 定位卡 IP | —（**有意退役**） | `zsjos_media_positioning_ip` | — |

⚠️ 交付延期与联系延期**共用同一个流程 key**，只能靠 businessKey 前缀区分。注册表按最长前缀分发，天然避开这个坑。

**定位卡/IP 是有意退役的分支，不是待修缺口。** 提交时不再因 `professionalRisk` 进入 `ip_review`；
监听器、BPMN 资产（`script/bpm/zsjos_media_positioning_ip/`）、DO 字段都保留，只为让**在途实例**
跑完。决策记录见 `handoff/main.md`。因此不实现 Provider，也不要在 `submitReview` 里接线。

## 要改的文件（已落地）

**yudao-module-bpm（契约与编排，公共）**
- `api/approvalcontent/BpmApprovalContentProvider.java` — 契约
- `api/approvalcontent/BpmApprovalContentRegistry.java` — 最长前缀分发，前缀重复即启动失败
- `api/approvalcontent/BpmApprovalContentService.java` + `Impl` — 编排 + 批量
- `api/approvalcontent/BpmApprovalBusinessKey.java` / `BpmApprovalFormat.java` — 解析与格式化工具
- `api/approvalcontent/vo/BpmApprovalBriefVO.java` / `BpmApprovalDetailVO.java` / `BpmApprovalFieldVO.java`
- `controller/admin/task/BpmApprovalContentController.java` — 两个端点（`/bpm/approval-content/*`）

**yudao-module-zsjos / yudao-module-eam（各域实现）**
- `service/bpm/content/provider/*Provider.java` — zsjos 的 11 个实现
- `eam/framework/approval/content/EamTransferContentProvider.java` — EAM 实现

**前端（workbench）**
- `services/api.ts` — `bpmApprovalBusinessSummaryBatch` / `bpmApprovalBusinessDetail`（走 `/bpm/approval-content`）
- `pages/BpmApprovalCenterPage.tsx` — 摘要列、移动端卡片、批量拉取
- `components/bpm/BpmApprovalDetail.tsx` — 插入业务详情卡；兜底英文键名做中文化
- `components/bpm/BusinessContentCard.tsx` — 用 `DetailFieldGrid` 渲染（**不得用 antd `Descriptions`**）

## 必须遵守的守卫测试

- `components/bpm/bpm-approval-actions.guard.test.ts` — `detail` 不得含 `formCustomViewPath`；必须含 `FORM_TYPE_NORMAL`、`fallbackItems`、`businessEntry`；四个 tab 名不变
- `pages/inbox-table-visible-fields.guard.test.ts` — `BpmApprovalCenterPage.tsx` 必须含 `流程名称 / 流程摘要 / 当前处理人 / 流程发起时间 / 处理耗时 / 审批意见`、`columnsState`、`详细`
- `pages/inbox-descriptions.guard.test.ts` — 新组件不用 antd `Descriptions`
- `pages/desktop-detail-drawer.guard.test.ts` — 保留 `ResizableDetailDrawer`

## 新增业务域的做法

1. 在自己的模块里实现 `BpmApprovalContentProvider`，标注 `@Component`
2. `parseBusinessId` 按**该域自己的** businessKey 格式解析（别照抄别域的段位假设）
3. 把新 Provider 加进 `ZsjosApprovalContentWiringTest.allProviders()`（若属 zsjos）或对应的接线测试
4. 前端无需改动

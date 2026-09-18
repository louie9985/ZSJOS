package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionFileRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewBatchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产内容审核（批审）审批的业务内容。
 *
 * <p>businessKey 形如 {@code content-review-batch:12}
 * （{@code ContentReviewBatchService.BUSINESS_KEY_PREFIX}）。
 *
 * <p>这是少数几个签名天然契合 Provider 的域：{@code get(batchId, userId)} 的入参
 * 正好是 businessId + viewerId，且内部已经做了 read 权限校验、把关系快照里的姓名
 * 全部解好，因此直接复用，不另起一套查询。
 *
 * <p>注意这个流程是**两级串联**（编导 → 终审），摘要必须带当前阶段，
 * 否则同一条批审在两位审批人的待办里长得一模一样。
 */
@Component
public class ContentReviewContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = ContentReviewBatchService.BUSINESS_KEY_PREFIX;

    @Resource
    private ContentReviewBatchService batchService;
    @Resource
    private ContentReviewBatchMapper batchMapper;

    @Override
    public String bizType() {
        return "content_review";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, businessKey));
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        ContentReviewBatchRespVO batch = load(businessId, viewerId);
        if (batch == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(batch.getBatchNo());
        brief.setSubtitle(stageText(batch.getCurrentStage()) + " · " + statusText(batch.getStatus()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("当前阶段", stageText(batch.getCurrentStage())),
                BpmApprovalFieldVO.of("状态", statusText(batch.getStatus())),
                BpmApprovalFieldVO.of("内容条数", contentCount(batch) + " 条"),
                BpmApprovalFieldVO.of("编导", batch.getDirectorName())));
        // 内容审核页当前不接收 query 参数，只能定位到页面、无法高亮具体批次。
        brief.setRoute("/zsjos/material-library/content-review");
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        ContentReviewBatchRespVO batch = load(businessId, viewerId);
        if (batch == null) {
            // 区分"无权"与"不存在"：前者要申请权限，后者要排查数据，处理方式不同。
            return exists(businessId)
                    ? BpmApprovalDetailVO.noAccess(bizType(), "内容审核批次")
                    : BpmApprovalDetailVO.notFound(bizType(), "内容审核批次");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(batch.getBatchNo());
        card.setStatusText(statusText(batch.getStatus()));
        // 内容审核页当前不接收 query 参数，只能定位到页面、无法高亮具体批次。
        card.setRoute("/zsjos/material-library/content-review");

        card.getGroups().add(group("批次信息", List.of(
                BpmApprovalFieldVO.of("批次号", batch.getBatchNo()),
                BpmApprovalFieldVO.of("当前阶段", stageText(batch.getCurrentStage())),
                BpmApprovalFieldVO.of("状态", statusText(batch.getStatus())),
                BpmApprovalFieldVO.of("内容条数", contentCount(batch) + " 条"),
                BpmApprovalFieldVO.of("运营", batch.getOperatorName()),
                BpmApprovalFieldVO.of("编导", batch.getDirectorName()))));

        card.getGroups().add(group("时间", List.of(
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(batch.getSubmittedAt())),
                BpmApprovalFieldVO.of("编导完成", BpmApprovalFormat.dateTime(batch.getDirectorCompletedAt())),
                BpmApprovalFieldVO.of("终审完成", BpmApprovalFormat.dateTime(batch.getFinalCompletedAt())),
                BpmApprovalFieldVO.of("定稿时间", BpmApprovalFormat.dateTime(batch.getFinalizedAt())))));

        // 逐条内容的附件：终审要看的就是这些，只有批次级信息无法判断。
        addItemAttachments(card, batch);

        if (batch.getRevisionOfBatchId() != null) {
            card.getGroups().add(group("修订", List.of(
                    BpmApprovalFieldVO.of("修订自批次", String.valueOf(batch.getRevisionOfBatchId())))));
        }
        return card;
    }

    /**
     * 按内容条目挂附件。每条内容一个分组，组标题用序号 + 编导/终审结论，
     * 这样审批人能对上"哪条内容、谁审的、审成什么"。
     */
    private void addItemAttachments(BpmApprovalDetailVO card, ContentReviewBatchRespVO batch) {
        if (batch.getItems() == null || batch.getItems().isEmpty()) {
            return;
        }
        int index = 0;
        for (ContentReviewBatchItemRespVO item : batch.getItems()) {
            index++;
            List<BpmApprovalFieldVO.Attachment> attachments = toAttachments(item.getFiles());
            BpmApprovalFieldVO field = BpmApprovalFieldVO.attachments(
                    "内容 " + index + itemSuffix(item), attachments);
            if (field == null) {
                continue;
            }
            BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
            group.setTitle("内容 " + index + itemSuffix(item));
            group.setFields(new ArrayList<>(List.of(field)));
            group.setSpan(true);
            card.getGroups().add(group);
        }
    }

    /** 条目后缀：带上审阅结论，便于审批人快速分辨。 */
    private static String itemSuffix(ContentReviewBatchItemRespVO item) {
        List<String> parts = new ArrayList<>();
        if (item.getDirectorDecision() != null) {
            parts.add("编导：" + decisionText(item.getDirectorDecision()));
        }
        if (item.getFinalDecision() != null) {
            parts.add("终审：" + decisionText(item.getFinalDecision()));
        }
        return parts.isEmpty() ? "" : "（" + String.join("，", parts) + "）";
    }

    /** 文件清单转附件；预览地址由业务侧签好，这一域不需要再签名。 */
    private static List<BpmApprovalFieldVO.Attachment> toAttachments(List<ContentVersionFileRespVO> files) {
        List<BpmApprovalFieldVO.Attachment> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        for (ContentVersionFileRespVO file : files) {
            String url = file.getPreviewUrl() != null ? file.getPreviewUrl() : file.getFileUrlSnapshot();
            if (url == null) {
                continue;
            }
            result.add(BpmApprovalFieldVO.attachment(file.getOriginalName(), url,
                    file.getContentType(), file.getFileSize()));
        }
        return result;
    }

    /**
     * 批次是否真实存在（用于区分无权与不存在）。查不到时按"不存在"处理——
     * 宁可提示数据缺失，也不误报权限问题。
     */
    private boolean exists(String businessId) {
        try {
            return batchMapper.selectById(Long.valueOf(businessId)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String decisionText(String decision) {
        return switch (decision) {
            case ContentReviewConstants.DECISION_APPROVED -> "通过";
            case ContentReviewConstants.DECISION_RETURNED -> "退回";
            default -> decision;
        };
    }

    /**
     * 复用业务侧 get：内部已做 read 权限校验。
     * 无权或被拒时返回 null，让审批中心退回通用展示。
     */
    private ContentReviewBatchRespVO load(String businessId, Long viewerId) {
        if (viewerId == null) {
            return null;
        }
        try {
            return batchService.get(Long.valueOf(businessId), viewerId);
        } catch (Exception ex) {
            return null;
        }
    }

    private static int contentCount(ContentReviewBatchRespVO batch) {
        return batch.getItems() == null ? 0 : batch.getItems().size();
    }

    private static String stageText(String stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case ContentReviewConstants.STAGE_DRAFT -> "草稿";
            case ContentReviewConstants.STAGE_DIRECTOR -> "编导审核";
            case ContentReviewConstants.STAGE_FINAL -> "终审";
            case ContentReviewConstants.STAGE_DONE -> "已完成";
            default -> stage;
        };
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ContentReviewConstants.BATCH_DRAFT -> "草稿";
            case ContentReviewConstants.BATCH_DIRECTOR_REVIEW -> "编导审核中";
            case ContentReviewConstants.BATCH_FINAL_REVIEW -> "终审中";
            case ContentReviewConstants.BATCH_COMPLETED -> "已完成";
            case ContentReviewConstants.BATCH_REJECTED -> "已驳回";
            case ContentReviewConstants.BATCH_NEED_MODIFY -> "待修改";
            case ContentReviewConstants.BATCH_CANCELLED -> "已取消";
            default -> status;
        };
    }

    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(fields));
        return group;
    }
}

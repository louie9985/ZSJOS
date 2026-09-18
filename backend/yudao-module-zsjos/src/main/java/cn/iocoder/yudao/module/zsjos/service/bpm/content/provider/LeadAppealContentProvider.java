package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.ZsjosApprovalAttachmentSupport;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/**
 * 线索申诉审批的业务内容。
 *
 * <p>businessKey 形如 {@code lead-appeal:12}。这一域的特别之处是**轮次**：
 * 同一线索最多三轮申诉，每轮审批人不同，因此标题里必须带上轮次，否则审批中心
 * 会出现多条看起来一模一样的记录。
 *
 * <p>客户姓名复用业务侧 {@code getLeadAppeals} 的投影结果，走同一套脱敏规则；
 * 不能因为"审批人"身份就绕过脱敏，审批人可能是非同一部门的销售主管。
 */
@Component
public class LeadAppealContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = APPEAL_BUSINESS_KEY_PREFIX;

    @Resource
    private LeadAppealMapper appealMapper;
    @Resource
    private LeadAppealServiceImpl appealService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private ZsjosApprovalAttachmentSupport attachmentSupport;

    @Override
    public String bizType() {
        return "lead_appeal";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(PREFIX)) {
            return null;
        }
        String id = businessKey.substring(PREFIX.length());
        return id.isBlank() ? null : id;
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        LeadAppealDO appeal = loadAppeal(businessId);
        if (appeal == null || !canView(appeal, viewerId)) {
            return null;
        }
        LeadAppealRespVO item = loadProjected(appeal, viewerId);
        if (item == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(item.getLeadNo() + " · 第 " + item.getRoundNo() + " 次申诉");
        brief.setSubtitle(BpmApprovalFormat.truncate(item.getReason(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("轮次", "第 " + item.getRoundNo() + " 次"),
                BpmApprovalFieldVO.of("审批阶段", stageText(item.getReviewStage())),
                BpmApprovalFieldVO.of("学员", item.getLeadName()),
                BpmApprovalFieldVO.of("状态", statusText(item.getStatus()))));
        brief.setRoute("/zsjos/appeals");
        brief.setQuery(new java.util.LinkedHashMap<>());
        brief.getQuery().put("appealId", item.getId());
        brief.getQuery().put("leadId", item.getLeadId());
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        LeadAppealDO appeal = loadAppeal(businessId);
        if (appeal == null) {
            return null;
        }
        if (!canView(appeal, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "线索申诉");
        }
        LeadAppealRespVO item = loadProjected(appeal, viewerId);
        if (item == null) {
            return BpmApprovalDetailVO.noAccess(bizType(), "线索申诉");
        }

        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(item.getLeadNo() + " · " + item.getLeadName());
        card.setStatusText(statusText(item.getStatus()));
        card.setRoute("/zsjos/appeals");
        card.getQuery().put("appealId", item.getId());
        card.getQuery().put("leadId", item.getLeadId());

        List<BpmApprovalFieldVO> basic = new ArrayList<>();
        basic.add(BpmApprovalFieldVO.of("线索编号", item.getLeadNo()));
        basic.add(BpmApprovalFieldVO.of("学员", item.getLeadName()));
        basic.add(BpmApprovalFieldVO.of("轮次", "第 " + item.getRoundNo() + " 次"));
        basic.add(BpmApprovalFieldVO.of("审批阶段", stageText(item.getReviewStage())));
        basic.add(BpmApprovalFieldVO.of("状态", statusText(item.getStatus())));
        basic.add(BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(item.getSubmittedAt())));
        card.getGroups().add(group("申诉信息", basic));

        if (item.getReason() != null) {
            card.getGroups().add(wideGroup("申诉理由", item.getReason()));
        }
        String original = join(item.getInvalidReasonSnapshot(), item.getInvalidDescriptionSnapshot());
        if (original != null) {
            card.getGroups().add(wideGroup("原无效结论", original));
        }
        if (item.getDecisionReason() != null) {
            card.getGroups().add(wideGroup("裁决意见", item.getDecisionReason()));
        }
        card.getGroups().add(group("处理信息", List.of(
                BpmApprovalFieldVO.of("处理人", item.getReviewerUserName()),
                BpmApprovalFieldVO.of("处理时间", BpmApprovalFormat.dateTime(item.getDecidedAt())))));

        // 证据是申诉审批的关键材料，原先只显示"n 个文件"看不出内容，改为可预览/下载的附件。
        attachmentSupport.addGroup(card, "申诉证据", toAttachments(item.getEvidence()), true);
        attachmentSupport.addGroup(card, "审批证据", toAttachments(item.getDecisionEvidence()), true);
        attachmentSupport.addGroup(card, "原无效证据", toAttachments(item.getInvalidEvidenceSnapshot()), true);
        return card;
    }

    /**
     * 证据快照转附件。
     *
     * <p>业务侧（{@code getLeadAppeals}）已经做过预签名，这里的 fileUrl 可直接用；
     * 但历史数据可能存的是过期地址，因此签名失败时由业务侧回退到快照地址，
     * 这里只要过滤掉彻底没有地址的项即可。
     */
    private static List<BpmApprovalFieldVO.Attachment> toAttachments(List<LeadAppealRespVO.EvidenceVO> evidence) {
        List<BpmApprovalFieldVO.Attachment> result = new ArrayList<>();
        if (evidence == null) {
            return result;
        }
        for (LeadAppealRespVO.EvidenceVO file : evidence) {
            if (file == null || file.getFileUrl() == null || file.getFileUrl().isBlank()) {
                continue;
            }
            result.add(BpmApprovalFieldVO.attachment(file.getOriginalName(), file.getFileUrl(),
                    file.getContentType(), file.getFileSize()));
        }
        return result;
    }

    private LeadAppealDO loadAppeal(String businessId) {
        Long id;
        try {
            id = Long.valueOf(businessId);
        } catch (NumberFormatException ex) {
            return null;
        }
        return appealMapper.selectById(id);
    }

    /**
     * 复用业务侧的转换：一套身份脱敏 + 附件预签名逻辑，避免审批中心另起一套。
     * 业务侧对无权查看会抛异常，这里统一降级为 null。
     */
    private LeadAppealRespVO loadProjected(LeadAppealDO appeal, Long viewerId) {
        try {
            return appealService.getLeadAppeals(appeal.getLeadId(), viewerId).stream()
                    .filter(record -> Objects.equals(record.getId(), appeal.getId()))
                    .findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 与 {@code LeadAppealServiceImpl.canReadAppealRecords} 同一判定：
     * 发起人本人可以看自己的申诉；其他人需要线索详情/申诉审批权限。
     */
    private boolean canView(LeadAppealDO appeal, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (Objects.equals(appeal.getApplicantUserId(), viewerId)) {
            return true;
        }
        return permissionApi.hasAnyPermissions(viewerId, PERMISSION_DETAIL_APPEAL_READ,
                PERMISSION_APPEAL_REVIEW_SALES_MANAGER, PERMISSION_APPEAL_REVIEW_QUALITY,
                PERMISSION_APPEAL_REVIEW_CHAIRMAN);
    }

    private static String join(String a, String b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? "" : a) + (b == null ? "" : (a == null ? "" : "：") + b);
    }

    private static String stageText(String stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case APPEAL_STAGE_SALES_MANAGER -> "销售主管";
            case APPEAL_STAGE_QUALITY -> "质检";
            case APPEAL_STAGE_CHAIRMAN -> "董事长";
            default -> stage;
        };
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case APPEAL_STATUS_SALES_MANAGER_REVIEWING -> "销售主管审批中";
            case APPEAL_STATUS_QUALITY_REVIEWING -> "质检审批中";
            case APPEAL_STATUS_CHAIRMAN_REVIEWING -> "董事长审批中";
            case APPEAL_STATUS_OVERTURNED -> "已改判有效";
            case APPEAL_STATUS_UPHELD -> "已维持无效";
            default -> status;
        };
    }


    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(fields));
        return group;
    }

    private static BpmApprovalDetailVO.Group wideGroup(String title, String value) {
        BpmApprovalDetailVO.Group group = group(title, List.of(BpmApprovalFieldVO.of(title, value)));
        group.setSpan(true);
        return group;
    }
}

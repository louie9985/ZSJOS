package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 线索转移审批的业务内容。
 *
 * <p>businessKey 形如 {@code lead-transfer:12}。
 *
 * <p>这一域没有任何可复用的详情方法（Service 只有 create / handleProcessResult），
 * 也没有 RespVO，因此只能直接查 DO。姓名与线索编号都不在 DO 上，需要额外查用户和线索。
 *
 * <p>注意前缀与 {@code lead-appeal:} 不同——两者都是"线索"域但属于不同流程，
 * 注册表按最长前缀匹配，不会互相吞掉。
 */
@Component
public class LeadTransferContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "lead-transfer:";

    @Resource
    private LeadTransferRequestMapper transferMapper;
    @Resource
    private LeadMapper leadMapper;
    @Resource
    private LeadAgingPoolService agingPoolService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;

    @Override
    public String bizType() {
        return "lead_transfer";
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
        LeadTransferRequestDO row = load(businessId);
        if (row == null || !canView(row, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(leadNo(row) + " · 转移负责人");
        brief.setSubtitle(BpmApprovalFormat.truncate(row.getReason(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("线索编号", leadNo(row)),
                BpmApprovalFieldVO.of("原负责人", userName(row.getFromOwnerUserId())),
                BpmApprovalFieldVO.of("目标负责人", userName(row.getRequestedOwnerUserId())),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        LeadTransferRequestDO row = load(businessId);
        if (row == null) {
            return null;
        }
        if (!canView(row, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "线索转移申请");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(leadNo(row) + " · 转移负责人");
        card.setStatusText(statusText(row.getStatus()));

        card.getGroups().add(group("转移信息", List.of(
                BpmApprovalFieldVO.of("线索编号", leadNo(row)),
                BpmApprovalFieldVO.of("原负责人", userName(row.getFromOwnerUserId())),
                BpmApprovalFieldVO.of("目标负责人", userName(row.getRequestedOwnerUserId())),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(row.getSubmittedAt())),
                BpmApprovalFieldVO.of("审批人", userName(row.getTransferReviewerUserId())))));

        if (row.getReason() != null) {
            card.getGroups().add(wideGroup("申请理由", row.getReason()));
        }
        if (row.getResolutionReason() != null) {
            card.getGroups().add(wideGroup("审批意见", row.getResolutionReason()));
        }
        if (row.getResolvedAt() != null) {
            card.getGroups().add(group("处理信息", List.of(
                    BpmApprovalFieldVO.of("处理时间", BpmApprovalFormat.dateTime(row.getResolvedAt())))));
        }
        return card;
    }

    private LeadTransferRequestDO load(String businessId) {
        try {
            return transferMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 与线索公海池同一套读权限：能读该线索的人（含主管、公海管理）才能看转移申请。
     * 权限服务抛异常时视为无权，退回通用展示。
     */
    private boolean canView(LeadTransferRequestDO row, Long viewerId) {
        if (viewerId == null || row.getLeadId() == null) {
            return false;
        }
        try {
            if (permissionApi.hasTenantReadAllAccess(viewerId) && leadMapper.selectById(row.getLeadId()) != null) return true;
            return agingPoolService.canRead(row.getLeadId(), viewerId);
        } catch (Exception ex) {
            return false;
        }
    }

    private String leadNo(LeadTransferRequestDO row) {
        if (row.getLeadId() == null) {
            return "线索转移申请";
        }
        LeadDO lead = leadMapper.selectById(row.getLeadId());
        return lead == null || lead.getLeadNo() == null ? "线索转移申请" : lead.getLeadNo();
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "pending" -> "审批中";
            case "approved" -> "已通过";
            case "rejected" -> "已拒绝";
            case "cancelled" -> "已取消";
            case "invalidated" -> "已失效";
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

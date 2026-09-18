package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.ClassTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.ClassTransferRequestMapper;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 转班审批的业务内容。
 *
 * <p>businessKey 形如 {@code class-transfer:12}。
 *
 * <p>这一域的所有班级名、班主任名都在 DO 上存了快照（{@code xxxSnapshot}），
 * 因此不必回查班级表——反而**必须**用快照：调班完成后原班级信息可能已经变了，
 * 审批人要看到的是申请当时的样子。
 *
 * <p>刻意不复用 {@code ClassTransferService.get(id, userId)}：它内部要求
 * {@code applicantUserId == userId}，审批人会被直接拒绝，正是本功能要绕开的问题。
 */
@Component
public class ClassTransferContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "class-transfer:";

    @Resource
    private ClassTransferRequestMapper transferMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "class_transfer";
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
        ClassTransferRequestDO row = load(businessId);
        if (row == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(row.getFromClassNameSnapshot() + " → " + row.getTargetClassNameSnapshot());
        brief.setSubtitle(BpmApprovalFormat.truncate(row.getReason(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("原班级", row.getFromClassNameSnapshot()),
                BpmApprovalFieldVO.of("目标班级", row.getTargetClassNameSnapshot()),
                BpmApprovalFieldVO.of("申请人", userName(row.getApplicantUserId())),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        ClassTransferRequestDO row = load(businessId);
        if (row == null) {
            return null;
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(row.getFromClassNameSnapshot() + " → " + row.getTargetClassNameSnapshot());
        card.setStatusText(statusText(row.getStatus()));

        card.getGroups().add(group("调班信息", List.of(
                BpmApprovalFieldVO.of("原班级", classText(row.getFromClassNoSnapshot(), row.getFromClassNameSnapshot())),
                BpmApprovalFieldVO.of("目标班级", classText(row.getTargetClassNoSnapshot(), row.getTargetClassNameSnapshot())),
                BpmApprovalFieldVO.of("原班主任", row.getFromHomeroomUserNameSnapshot()),
                BpmApprovalFieldVO.of("目标班主任", row.getTargetHomeroomUserNameSnapshot()),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(row.getSubmittedAt())))));

        card.getGroups().add(group("申请人", List.of(
                BpmApprovalFieldVO.of("申请人", userName(row.getApplicantUserId())),
                BpmApprovalFieldVO.of("审批人", userName(row.getReviewerUserId())))));

        if (row.getReason() != null) {
            card.getGroups().add(wideGroup("调班原因", row.getReason()));
        }
        if (row.getResolutionReason() != null) {
            card.getGroups().add(wideGroup("处理意见", row.getResolutionReason()));
        }
        if (row.getFinishedAt() != null) {
            card.getGroups().add(group("处理信息", List.of(
                    BpmApprovalFieldVO.of("处理时间", BpmApprovalFormat.dateTime(row.getFinishedAt())))));
        }
        return card;
    }

    private ClassTransferRequestDO load(String businessId) {
        try {
            return transferMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    private static String classText(String no, String name) {
        if (name == null) {
            return no;
        }
        return no == null || Objects.equals(no, name) ? name : name + "（" + no + "）";
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

package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactExtensionMapper;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactExtensionObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 学员联系延期审批的业务内容。
 *
 * <p>businessKey 形如 {@code student-contact-extension:12}。
 *
 * <p>⚠️ 与"学员交付延期"共用流程定义 key {@code zsjos_student_contact_extension}，
 * 靠前缀区分；本域状态是**小写**（pending/approved/rejected/cancelled/withdrawn）。
 *
 * <p>权限直接复用 {@link StudentContactExtensionObjectPermissionProvider}：
 * 它对 {@code read} 的判据是"申请人是本人，或审批人是本人"——审批人正是要看内容的人。
 */
@Component
public class StudentContactExtensionContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "student-contact-extension:";
    private static final String ACTION_READ = "read";

    @Resource
    private StudentContactExtensionMapper extensionMapper;
    @Resource
    private StudentContactExtensionObjectPermissionProvider permissionProvider;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "student_contact_extension";
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
        StudentContactExtensionDO row = load(businessId);
        if (row == null || !canView(row, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(extensionTitle(row));
        brief.setSubtitle(BpmApprovalFormat.truncate(row.getDescription(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("延期原因", row.getReasonLabelSnapshot()),
                BpmApprovalFieldVO.of("原到期", BpmApprovalFormat.dateTime(row.getOriginalDueAt())),
                BpmApprovalFieldVO.of("申请到期", BpmApprovalFormat.dateTime(row.getRequestedDueAt())),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        StudentContactExtensionDO row = load(businessId);
        if (row == null) {
            return null;
        }
        if (!canView(row, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "学员联系延期申请");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(extensionTitle(row));
        card.setStatusText(statusText(row.getStatus()));

        card.getGroups().add(group("延期信息", List.of(
                BpmApprovalFieldVO.of("延期原因", row.getReasonLabelSnapshot()),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus())),
                BpmApprovalFieldVO.of("原到期时间", BpmApprovalFormat.dateTime(row.getOriginalDueAt())),
                BpmApprovalFieldVO.of("申请到期时间", BpmApprovalFormat.dateTime(row.getRequestedDueAt())))));

        card.getGroups().add(group("申请人", List.of(
                BpmApprovalFieldVO.of("申请人", userName(row.getApplicantUserId())),
                BpmApprovalFieldVO.of("审批人", userName(row.getReviewerUserId())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(row.getSubmittedAt())))));

        if (row.getDescription() != null) {
            card.getGroups().add(wideGroup("延期说明", row.getDescription()));
        }
        if (row.getDecisionReason() != null) {
            card.getGroups().add(wideGroup("审批意见", row.getDecisionReason()));
        }
        if (row.getResolvedAt() != null) {
            card.getGroups().add(group("处理信息", List.of(
                    BpmApprovalFieldVO.of("处理时间", BpmApprovalFormat.dateTime(row.getResolvedAt())))));
        }
        return card;
    }

    private StudentContactExtensionDO load(String businessId) {
        try {
            return extensionMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean canView(StudentContactExtensionDO row, Long viewerId) {
        try {
            return permissionProvider.hasPermission(row.getId(), ACTION_READ, viewerId);
        } catch (Exception ex) {
            return false;
        }
    }

    private String extensionTitle(StudentContactExtensionDO row) {
        if (row.getReasonLabelSnapshot() != null) {
            return "学员联系延期 · " + row.getReasonLabelSnapshot();
        }
        return "学员联系延期申请";
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
            case "pending" -> "待审批";
            case "approved" -> "已通过";
            case "rejected" -> "已驳回";
            case "cancelled" -> "已取消";
            case "withdrawn" -> "已撤回";
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

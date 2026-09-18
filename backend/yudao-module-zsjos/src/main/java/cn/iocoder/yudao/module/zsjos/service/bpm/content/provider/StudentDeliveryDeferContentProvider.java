package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryDeferMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 学员交付延期审批的业务内容。
 *
 * <p>businessKey 形如 {@code student-delivery-defer:12}。
 *
 * <p>⚠️ 本域与"学员联系延期"**共用同一个流程定义 key**
 * {@code zsjos_student_contact_extension}，只能靠 businessKey 前缀区分。
 * 注册表按前缀分发，因此这里必须返回本域自己的前缀，不能图省事复用联系延期的。
 *
 * <p>状态值为**全大写**（PENDING/APPROVED/REJECTED/CANCELLED），
 * 与联系延期的小写不一致——这是既成事实，转换时不要想当然。
 */
@Component
public class StudentDeliveryDeferContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "student-delivery-defer:";
    private static final String PERMISSION_QUERY = "zsjos:student-delivery:query";

    @Resource
    private StudentDeliveryDeferMapper deferMapper;
    @Resource
    private StudentDeliveryStageMapper stageMapper;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "student_delivery_defer";
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
        StudentDeliveryDeferDO row = load(businessId);
        if (row == null || !canView(row, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(stageTitle(row) + " · 延期 " + row.getRequestedDays() + " 天");
        brief.setSubtitle(BpmApprovalFormat.truncate(row.getReason(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("延期天数", row.getRequestedDays() + " 天"),
                BpmApprovalFieldVO.of("原截止", BpmApprovalFormat.dateTime(row.getOriginalDueAt())),
                BpmApprovalFieldVO.of("申请人", userName(row.getRequestedBy())),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        StudentDeliveryDeferDO row = load(businessId);
        if (row == null) {
            return null;
        }
        if (!canView(row, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "交付延期申请");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(stageTitle(row) + " · 延期 " + row.getRequestedDays() + " 天");
        card.setStatusText(statusText(row.getStatus()));

        card.getGroups().add(group("延期信息", List.of(
                BpmApprovalFieldVO.of("交付阶段", stageCode(row)),
                BpmApprovalFieldVO.of("延期天数", row.getRequestedDays() + " 天"),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus())),
                BpmApprovalFieldVO.of("原截止时间", BpmApprovalFormat.dateTime(row.getOriginalDueAt())))));

        card.getGroups().add(group("申请人", List.of(
                BpmApprovalFieldVO.of("申请人", userName(row.getRequestedBy())),
                BpmApprovalFieldVO.of("审批人", userName(row.getSupervisorUserId())))));

        if (row.getReason() != null) {
            card.getGroups().add(wideGroup("延期原因", row.getReason()));
        }
        if (row.getDecisionReason() != null) {
            card.getGroups().add(wideGroup("审批意见", row.getDecisionReason()));
        }
        if (row.getDecidedAt() != null) {
            card.getGroups().add(group("处理信息", List.of(
                    BpmApprovalFieldVO.of("处理时间", BpmApprovalFormat.dateTime(row.getDecidedAt())))));
        }
        return card;
    }

    private StudentDeliveryDeferDO load(String businessId) {
        try {
            return deferMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 交付阶段本身没有独立的 ObjectPermissionProvider，因此按 HTTP 层同一个权限字面量判定，
     * 并额外放行申请人本人（自己的申请自己要看得到）。
     */
    private boolean canView(StudentDeliveryDeferDO row, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (Objects.equals(row.getRequestedBy(), viewerId)) {
            return true;
        }
        try {
            return permissionApi.hasAnyPermissions(viewerId, PERMISSION_QUERY);
        } catch (Exception ex) {
            return false;
        }
    }

    /** 阶段 DO 不保证存在（历史数据），缺失时退回通用标题。 */
    private String stageTitle(StudentDeliveryDeferDO row) {
        StudentDeliveryStageDO stage = loadStage(row.getStageId());
        return stage == null ? "交付延期申请" : "交付阶段 " + stage.getStageCode();
    }

    private String stageCode(StudentDeliveryDeferDO row) {
        StudentDeliveryStageDO stage = loadStage(row.getStageId());
        return stage == null ? null : stage.getStageCode();
    }

    private StudentDeliveryStageDO loadStage(Long stageId) {
        if (stageId == null) {
            return null;
        }
        try {
            return stageMapper.selectById(stageId);
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

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELLED" -> "已撤销";
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

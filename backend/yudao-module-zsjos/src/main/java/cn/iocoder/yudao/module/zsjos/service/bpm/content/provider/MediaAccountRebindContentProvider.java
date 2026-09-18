package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体账号重绑审批的业务内容。
 *
 * <p>businessKey 是**三段式** {@code media-rebind:{accountId}:v{version}}
 * （{@code MediaAccountService.requestRebind} 内联拼接，没有抽成常量）。
 * 第三段是发起时的账号版本号，用于并发校验，**不是**业务 id——
 * 解析时必须只取第一段，写成 {@code split(":")[1]} 会把版本号当 id。
 *
 * <p>重绑状态直接挂在账号 DO 上（没有独立的 rebind 表）。
 * 这一域的 DO 自带 {@code *LabelSnapshot} 中文快照，因此不必查字典。
 */
@Component
public class MediaAccountRebindContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "media-rebind:";
    private static final String ACTION_READ = "read";

    @Resource
    private MediaAccountMapper accountMapper;
    @Resource
    private MediaAccountObjectPermissionProvider permissionProvider;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "media_rebind";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        // 取第一段（账号 id），忽略 ":v{version}"。
        return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, businessKey));
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        MediaAccountDO account = load(businessId);
        if (account == null || !canView(account, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(account.getNickname() + " · " + account.getAccountNo());
        brief.setSubtitle("重绑学员账号 · " + rebindStatusText(account.getRebindStatus()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("账号", account.getNickname()),
                BpmApprovalFieldVO.of("平台", account.getPlatformLabelSnapshot()),
                BpmApprovalFieldVO.of("目标学员", personText(account.getRebindTargetStudentPersonId())),
                BpmApprovalFieldVO.of("状态", rebindStatusText(account.getRebindStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        MediaAccountDO account = load(businessId);
        if (account == null) {
            return null;
        }
        if (!canView(account, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "媒体账号重绑");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(account.getNickname() + " · " + account.getAccountNo());
        card.setStatusText(rebindStatusText(account.getRebindStatus()));

        card.getGroups().add(group("账号信息", List.of(
                BpmApprovalFieldVO.of("账号编号", account.getAccountNo()),
                BpmApprovalFieldVO.of("账号昵称", account.getNickname()),
                BpmApprovalFieldVO.of("平台", account.getPlatformLabelSnapshot()),
                BpmApprovalFieldVO.of("平台账号", account.getPlatformAccountId()),
                BpmApprovalFieldVO.of("当前状态", account.getCurrentStatusLabelSnapshot()),
                BpmApprovalFieldVO.of("S 阶段", account.getSStageLabelSnapshot()))));

        card.getGroups().add(group("重绑信息", List.of(
                BpmApprovalFieldVO.of("重绑状态", rebindStatusText(account.getRebindStatus())),
                BpmApprovalFieldVO.of("原学员账号", personText(account.getStudentPersonId())),
                BpmApprovalFieldVO.of("目标学员账号", personText(account.getRebindTargetStudentPersonId())),
                BpmApprovalFieldVO.of("申请人", userName(account.getRebindRequestedByUserId())),
                BpmApprovalFieldVO.of("审批人", userName(account.getRebindReviewerUserId())))));

        if (account.getRebindResultReason() != null) {
            card.getGroups().add(wideGroup("审批意见", account.getRebindResultReason()));
        }
        return card;
    }

    private MediaAccountDO load(String businessId) {
        try {
            return accountMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean canView(MediaAccountDO account, Long viewerId) {
        try {
            return permissionProvider.hasPermission(account.getId(), ACTION_READ, viewerId);
        } catch (Exception ex) {
            return false;
        }
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    /** personId 是内部学员标识，审批中心只能展示数字，不暴露为姓名（没有可靠的反查路径）。 */
    private static String personText(Long personId) {
        return personId == null ? null : "学员 #" + personId;
    }

    private static String rebindStatusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "pending" -> "审批中";
            case "approved" -> "已通过";
            case "rejected" -> "已驳回";
            case "none" -> "未重绑";
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

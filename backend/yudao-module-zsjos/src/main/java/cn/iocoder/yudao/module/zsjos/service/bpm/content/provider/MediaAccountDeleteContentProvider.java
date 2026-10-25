package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDeleteRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountDeleteRequestMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MediaAccountDeleteContentProvider implements BpmApprovalContentProvider {
    private static final String PREFIX = "media-account-delete:";
    @Resource private MediaAccountDeleteRequestMapper requests;
    @Resource private MediaAccountObjectPermissionProvider perms;
    @Resource private AdminUserApi users;

    public String bizType() { return "media_account_delete"; }
    public String businessKeyPrefix() { return PREFIX; }
    public String parseBusinessId(String key) { return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, key)); }
    public BpmApprovalBriefVO brief(String id, Long viewer) { return brief(id, viewer, null); }
    public BpmApprovalDetailVO detail(String id, Long viewer) { return detail(id, viewer, null); }

    @Override
    public BpmApprovalBriefVO brief(String id, Long viewer, String processInstanceId) {
        var request = load(id, processInstanceId);
        if (request == null || !canView(request, viewer)) return null;
        var snapshot = snapshot(request);
        var result = new BpmApprovalBriefVO();
        result.setBizType(bizType()); result.setTitle(title(snapshot));
        result.setSubtitle("账号删除审批 · " + status(request.getStatus()));
        result.setFields(List.of(BpmApprovalFieldVO.of("账号编号", text(snapshot.get("accountNo"), "未记录账号编号")),
                BpmApprovalFieldVO.of("申请人", userName(snapshot.get("requestedByName"), request.getRequestedByUserId())),
                BpmApprovalFieldVO.of("删除原因", request.getReason())));
        return result;
    }

    @Override
    public BpmApprovalDetailVO detail(String id, Long viewer, String processInstanceId) {
        var result = new BpmApprovalDetailVO(); result.setBizType(bizType());
        var request = load(id, processInstanceId);
        if (request == null) { result.setMessage("未找到本次删除申请"); return result; }
        if (!canView(request, viewer)) { result.setMessage("暂无查看本次删除申请的权限"); return result; }
        var snapshot = snapshot(request);
        result.setTitle(title(snapshot)); result.setStatusText(status(request.getStatus()));
        var group = new BpmApprovalDetailVO.Group(); group.setTitle("删除申请快照");
        group.setFields(new ArrayList<>(List.of(
                BpmApprovalFieldVO.of("账号名称", text(snapshot.get("nickname"), "未填写账号名称")),
                BpmApprovalFieldVO.of("账号编号", text(snapshot.get("accountNo"), "未记录账号编号")),
                BpmApprovalFieldVO.of("平台", text(snapshot.get("platform"), "未记录平台")),
                BpmApprovalFieldVO.of("申请原因", request.getReason()),
                BpmApprovalFieldVO.of("申请人", userName(snapshot.get("requestedByName"), request.getRequestedByUserId())),
                BpmApprovalFieldVO.of("所属编导（审批人）", userName(snapshot.get("reviewerName"), request.getReviewerUserId())))));
        result.getGroups().add(group);
        return result;
    }

    private MediaAccountDeleteRequestDO load(String id, String processInstanceId) {
        Long accountId;
        try { accountId = Long.valueOf(id); } catch (NumberFormatException | NullPointerException ex) { return null; }
        // Task authorization supplies the instance; older rounds must never borrow the newest request.
        var request = processInstanceId == null ? requests.byAccount(accountId) : requests.byProcess(processInstanceId);
        return request != null && Objects.equals(request.getAccountId(), accountId) ? request : null;
    }

    private boolean canView(MediaAccountDeleteRequestDO request, Long viewer) {
        return viewer != null && (Objects.equals(viewer, request.getRequestedByUserId())
                || Objects.equals(viewer, request.getReviewerUserId())
                || perms.hasPermission(request.getAccountId(), "read", viewer));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(MediaAccountDeleteRequestDO request) {
        if (request.getAccountSnapshotJson() == null) return Map.of();
        return JsonUtils.parseObject(request.getAccountSnapshotJson(), Map.class);
    }

    private String userName(Object snapshotName, Long id) {
        if (snapshotName instanceof String value && !value.isBlank()) return value;
        var user = id == null ? null : users.getUser(id);
        return user == null ? "人员信息不可用" : text(user.getNickname(), "未填写姓名");
    }

    private static String title(Map<String, Object> snapshot) {
        return "删除账号 · " + text(snapshot.get("nickname"), text(snapshot.get("accountNo"), "未填写账号名称"));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String status(String value) {
        if (value == null) return "状态未记录";
        return switch (value) {
            case "pending" -> "审批中";
            case "completed" -> "已删除";
            case "approved_pending" -> "审批已通过，删除处理待重试";
            case "rejected" -> "已驳回";
            case "withdrawn" -> "已撤回";
            default -> "状态未记录";
        };
    }
}

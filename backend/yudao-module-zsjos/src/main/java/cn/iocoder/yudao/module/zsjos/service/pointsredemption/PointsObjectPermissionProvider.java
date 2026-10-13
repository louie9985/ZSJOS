package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component public class PointsObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private PointsRequestMapper requests;
    @Resource private BpmProcessProgressApi progress;
    @Resource private PermissionApi permissions;
    public String getBizType() { return "points-redemption"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        var row = requests.selectById(id);
        if (row == null || userId == null) return false;
        boolean owner = Objects.equals(row.getApplicantUserId(), userId);
        return switch(action) {
            case "reveal", "cancel", "urge" -> owner;
            case "manage" -> permissions.hasAnyPermissions(userId, PointsRequestService.PERM + "manage");
            case "read", "refresh" -> owner || permissions.hasAnyPermissions(userId, PointsRequestService.PERM + "manage")
                    || progress.isParticipant(row.getProcessInstanceId(), userId);
            default -> false;
        };
    }
    public void check(Long id, String action, Long userId) { if (!hasPermission(id, action, userId)) throw PointsErrors.denied(); }
}

package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Objects;

@Component
public class TeachingServiceCloseNotifyPublisher {
    @Resource private NotifyBusinessEventApi notifications;
    @Resource private AdminUserApi users;
    @Resource private DeptApi departments;
    @Resource private PermissionApi permissions;
    @Resource private StudentServiceObjectPermissionProvider servicePermissions;

    public void publish(ServiceRelationDO relation, Long actor, String reason) {
        var planner = users.getUser(relation.getOwnerUserId());
        var department = planner == null || planner.getDeptId() == null ? null : departments.getDept(planner.getDeptId());
        Long leaderId = department == null ? null : department.getLeaderUserId();
        var leader = leaderId == null ? null : users.getUser(leaderId);
        boolean eligible = leader != null && leaderId > 0 && !Objects.equals(leaderId,relation.getOwnerUserId())
                && Objects.equals(CommonStatusEnum.ENABLE.getStatus(),leader.getStatus())
                && permissions.hasAnyPermissions(leaderId,"zsjos:student:query-my")
                && servicePermissions.hasPermission(relation.getId(),"read",leaderId);
        var product = LeadProductSnapshot.readHistoricalQuietly(relation.getServiceSnapshot());
        var payload = new LinkedHashMap<String,Object>();
        // These values belong to this close occurrence; asynchronous delivery must not re-read mutable ownership.
        payload.put("supervisorUserId",eligible ? leaderId : null);
        payload.put("student.name",Objects.requireNonNullElse(relation.getStudentNameSnapshot(),""));
        payload.put("course.name",product == null || product.name() == null ? "" : product.name());
        payload.put("planner.name",planner == null ? "" : Objects.requireNonNullElse(planner.getNickname(),""));
        payload.put("close.reason",reason);
        payload.put("close.time",relation.getTeachingClosedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        notifications.publish(NotifyBusinessEvent.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(TeachingServiceCloseNotifySceneProvider.SCENE).bizType("student_service").bizId(relation.getId())
                .sourceEventKey("teaching-service-closed:"+relation.getId()+":v"+relation.getVersion())
                .operatorUserId(actor).occurredAt(relation.getTeachingClosedAt()).payload(payload).build());
    }
}

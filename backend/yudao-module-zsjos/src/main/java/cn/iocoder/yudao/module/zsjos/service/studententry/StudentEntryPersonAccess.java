package cn.iocoder.yudao.module.zsjos.service.studententry;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.*;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Component
public class StudentEntryPersonAccess implements ZsjosObjectPermissionProvider {
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentServiceObjectPermissionProvider reader;
    @Resource private PermissionApi permissions;
    @Resource private DeliveryClassObjectPermissionProvider classReader;
    @Resource private LeadMapper leads;
    @Resource private LeadObjectPermissionService leadReader;
    @Override public String getBizType() { return "student-entry-person"; }
    @Override public boolean hasPermission(Long personId, String action, Long actor) {
        if (!"reuse".equals(action) || !permissions.hasAnyPermissions(actor, StudentEntryService.PERMISSION)
                || !permissions.hasAnyPermissions(actor, "zsjos:student:query-my", "zsjos:media-student:query-my",
                "zsjos:delivery-class:query", "zsjos:delivery-class:query-managed", "zsjos:delivery-class:query-my", "zsjos:lead:query")) return false;
        if (relations.selectList(new LambdaQueryWrapperX<ServiceRelationDO>().eq(ServiceRelationDO::getPersonId, personId))
                .stream().anyMatch(r -> canReadService(r, actor))) return true;
        // Existing lead-only people can enroll without duplicating their identity or changing their lead.
        return permissions.hasAnyPermissions(actor, "zsjos:lead:query")
                && leads.selectList(new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getPersonId, personId)).stream()
                    .anyMatch(lead -> leadReader.canReadDetail(lead, actor) && leadReader.canViewUnmaskedIdentity(actor, lead));
    }
    public boolean canReadService(ServiceRelationDO relation, Long actor) {
        boolean studentFeature = permissions.hasAnyPermissions(actor, "zsjos:student:query-my", "zsjos:media-student:query-my");
        boolean classFeature = permissions.hasAnyPermissions(actor, "zsjos:delivery-class:query", "zsjos:delivery-class:query-managed", "zsjos:delivery-class:query-my");
        // A class-only reader must already be able to open this class, not merely own an unrelated media role.
        return (studentFeature || classFeature && relation.getClassId() != null
                && classReader.hasPermission(relation.getClassId(), "read", actor))
                && reader.hasPermission(relation.getId(), "read", actor);
    }
    @Override public void check(Long id, String action, Long actor) {
        if (!hasPermission(id, action, actor)) throw exception(STUDENT_PERMISSION_DENIED);
    }
    @ZsjosPermission(bizType="student-entry-person", bizId="#personId", action="reuse")
    public void checkReuse(Long personId, Long actor) { check(personId, "reuse", actor); }
}

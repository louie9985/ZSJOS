package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Component
public class ExamCollectionPlannerPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ExamCollectionTaskMapper collections;
    @Resource private BusinessTaskMapper tasks;
    @Resource private PermissionApi permissions;
    public String getBizType() { return "exam-collection-planner"; }
    public boolean hasPermission(Long id, String action, Long userId) {
        Long tenant=TenantContextHolder.getTenantId();
        if(id==null||userId==null||tenant==null||!"query".equals(action)
                ||!permissions.hasAnyPermissions(userId,"zsjos:business-task:query"))return false;
        var collection=collections.selectById(id);
        if(collection==null||!Objects.equals(collection.getTenantId(),tenant)||Boolean.TRUE.equals(collection.getDeleted())||!OPEN.equals(collection.getStatus()))return false;
        return tasks.selectMyPendingByBusiness(userId,BIZ_TYPE,id).stream().anyMatch(t->
                TASK_TYPE.equals(t.getTaskType())&&BIZ_TYPE.equals(t.getBizType())&&"pending".equals(t.getStatus())&&!Boolean.TRUE.equals(t.getDeleted())
                &&Objects.equals(t.getTenantId(),tenant)&&Objects.equals(t.getBizId(),id)&&Objects.equals(t.getAssigneeId(),userId));
    }
    public void check(Long id,String action,Long userId){if(!hasPermission(id,action,userId))throw exception(DENIED);}
}

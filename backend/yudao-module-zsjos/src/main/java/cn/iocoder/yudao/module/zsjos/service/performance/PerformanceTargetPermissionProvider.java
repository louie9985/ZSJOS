package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceTargetMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
@Component public class PerformanceTargetPermissionProvider implements ZsjosObjectPermissionProvider {
 @Resource private PerformanceTargetMapper mapper; @Resource private PerformanceAccess access;
 public String getBizType(){return "sales-performance-target";}
 public boolean hasPermission(Long id,String action,Long userId){try{var row=mapper.selectById(id);if(row==null)return false;access.targetObject(row.getScopeType(),row.getScopeId());if(!access.departmentAllowed(row.getDeptId()))return false;return "read".equals(action)||"update".equals(action)&&access.has("zsjos:sales-performance-target:update");}catch(cn.iocoder.yudao.framework.common.exception.ServiceException e){return false;}}
 public void check(Long id,String action,Long userId){if(!hasPermission(id,action,userId))throw PerformanceAccess.denied();}
}

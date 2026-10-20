package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.DeliveryPerformanceTargetMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
@Component public class DeliveryPerformanceTargetPermissionProvider implements ZsjosObjectPermissionProvider {
 @Resource private DeliveryPerformanceTargetMapper mapper; @Resource private DeliveryPerformanceAccess access;
 public String getBizType(){return "delivery-performance-target";}
 public boolean hasPermission(Long id,String action,Long userId){try{var row=mapper.selectById(id);if(row==null||!"delivery".equals(row.getBusinessDomain()))return false;if("read".equals(action)){access.targetReadObject(row);return true;}access.targetWriteObject(row.getScopeType(),row.getScopeId());if(!access.commandDepartmentAllowed(row.getDeptId()))return false;return "read".equals(action)||"update".equals(action)&&access.has("zsjos:delivery-performance-target:update");}catch(cn.iocoder.yudao.framework.common.exception.ServiceException e){return false;}}
 public void check(Long id,String action,Long userId){if(!hasPermission(id,action,userId))throw DeliveryPerformanceAccess.denied();}
}

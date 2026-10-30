package cn.iocoder.yudao.module.zsjos.service.task;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Component
public class BusinessTaskCancellationPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private BusinessTaskMapper taskMapper;
    @Override public String getBizType() { return BusinessTaskCancellationService.BIZ_TYPE; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        return "cancel".equals(action) && BusinessTaskCancellationService.owns(taskMapper.selectById(id), userId);
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(BusinessTaskCancellationService.FORBIDDEN);
    }
}

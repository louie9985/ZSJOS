package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Service
public class MediaStudentServicePeriodService {
    public static final String PERMISSION = "zsjos:media-student:update-service-period";
    @Resource private PersonMapper personMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private StudentObjectPermissionProvider studentPermissions;

    public boolean canUpdate(Long userId, Long personId) {
        return permissionApi.hasAnyPermissions(userId, PERMISSION)
                && studentPermissions.hasPermission(personId, "update-service-period", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "update-service-period")
    public boolean update(Long userId, Long personId, boolean inServicePeriod) {
        if (!canUpdate(userId, personId)) throw exception(STUDENT_PERMISSION_DENIED);
        if (personMapper.selectCount(new LambdaQueryWrapperX<PersonDO>().eq(PersonDO::getId, personId)) == 0) {
            throw exception(STUDENT_NOT_EXISTS);
        }
        // Update only the list flag: unrelated person edits and course states must remain intact.
        personMapper.update(new PersonDO(), new LambdaUpdateWrapper<PersonDO>()
                .eq(PersonDO::getId, personId).set(PersonDO::getInServicePeriod, inServicePeriod));
        PersonDO saved = personMapper.selectById(personId);
        if (saved == null) throw exception(STUDENT_NOT_EXISTS);
        return Boolean.TRUE.equals(saved.getInServicePeriod());
    }
}

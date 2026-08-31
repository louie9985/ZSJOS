package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactExtensionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Component
public class StudentContactExtensionObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private StudentContactExtensionMapper extensionMapper;

    @Override public String getBizType() { return "student-contact-extension"; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        StudentContactExtensionDO extension = extensionMapper.selectById(bizId);
        if (extension == null) return false;
        if ("withdraw".equals(action)) return Objects.equals(extension.getApplicantUserId(), userId);
        return "read".equals(action) && (Objects.equals(extension.getApplicantUserId(), userId)
                || Objects.equals(extension.getReviewerUserId(), userId));
    }

    @Override public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}

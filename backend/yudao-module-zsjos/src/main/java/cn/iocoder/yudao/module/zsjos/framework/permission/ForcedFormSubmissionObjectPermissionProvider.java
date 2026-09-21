package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormSubmissionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Submission IDs belong to a different namespace from form IDs. */
@Component
public class ForcedFormSubmissionObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ForcedFormMapper formMapper;
    @Resource private ForcedFormSubmissionMapper submissionMapper;
    @Resource private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return "forced-form-submission";
    }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        if (bizId == null || userId == null || !"read".equals(action)) return false;
        var submission = submissionMapper.selectById(bizId);
        if (submission == null) return false;
        var form = formMapper.selectById(submission.getFormId());
        if (form == null) return false;
        return Objects.equals(submission.getUserId(), userId)
                || Objects.equals(form.getCreator(), String.valueOf(userId))
                || permissionApi.hasTenantReadAllAccess(userId);
    }
}

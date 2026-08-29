package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormRecipientDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormRecipientMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormSubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ForcedFormObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    private static final String BIZ_TYPE = "forced-form";

    @Resource private ForcedFormMapper formMapper;
    @Resource private ForcedFormRecipientMapper recipientMapper;
    @Resource private ForcedFormSubmissionMapper submissionMapper;

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        if (bizId == null || userId == null) {
            return false;
        }
        ForcedFormDO form = formMapper.selectById(bizId);
        if (form != null) {
            if (matchesCreator(form, userId)) {
                return true;
            }
            if ("runtime".equals(action) || "submit".equals(action) || "attachment-upload".equals(action)) {
                return recipientMapper.selectOne(Wrappers.<ForcedFormRecipientDO>lambdaQuery()
                        .eq(ForcedFormRecipientDO::getFormId, form.getId())
                        .eq(ForcedFormRecipientDO::getUserId, userId)
                        .eq(ForcedFormRecipientDO::getStatus, "PENDING")) != null;
            }
            return "read".equals(action) || "copy".equals(action);
        }
        ForcedFormSubmissionDO submission = submissionMapper.selectById(bizId);
        if (submission == null) {
            return false;
        }
        if (Objects.equals(submission.getUserId(), userId)) {
            return true;
        }
        ForcedFormDO submittedForm = formMapper.selectById(submission.getFormId());
        return submittedForm != null && matchesCreator(submittedForm, userId);
    }

    private boolean matchesCreator(ForcedFormDO form, Long userId) {
        return form.getCreator() != null && Objects.equals(form.getCreator(), String.valueOf(userId));
    }
}

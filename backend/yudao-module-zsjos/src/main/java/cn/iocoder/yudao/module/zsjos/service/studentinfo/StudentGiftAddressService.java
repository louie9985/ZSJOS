package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class StudentGiftAddressService {
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper revisions;

    public String latestAddress(Long personId) {
        if (personId == null) return null;
        // Latest submitted archive wins; an empty address must not revive an older address.
        var form = forms.selectOne(Wrappers.lambdaQuery(StudentInfoFormDO.class)
                .eq(StudentInfoFormDO::getPersonId, personId)
                .eq(StudentInfoFormDO::getStatus, "SUBMITTED")
                .orderByDesc(StudentInfoFormDO::getSubmittedAt).orderByDesc(StudentInfoFormDO::getId)
                .last("LIMIT 1"));
        if (form == null) return null;
        var revision = revisions.latest(form.getId());
        var values = revision == null ? answers.byForm(form.getId())
                : JsonUtils.parseArray(revision.getAnswersJson(), StudentInfoFormValueDO.class);
        return values.stream().filter(v -> "gift_shipping_address".equals(v.getFieldKey()))
                .map(StudentInfoFormValueDO::getValueText).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
}

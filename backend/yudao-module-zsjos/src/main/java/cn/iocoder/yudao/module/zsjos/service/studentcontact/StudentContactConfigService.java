package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;

public interface StudentContactConfigService {
    StudentContactConfigRespVO get();
    Long copyDraft(Long publishedId, Integer publishedVersion, String idempotencyKey);
    void updateDraft(StudentContactConfigSaveReqVO request);
    void publish(Long id, Integer version, String idempotencyKey);
    StudentContactConfigVersionDO requirePublished();
}

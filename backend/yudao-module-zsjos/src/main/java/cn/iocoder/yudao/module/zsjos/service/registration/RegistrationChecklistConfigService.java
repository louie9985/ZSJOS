package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistDraftSaveReqVO;

public interface RegistrationChecklistConfigService {
    RegistrationChecklistConfigRespVO getConfig();
    Long copyPublishedToDraft(Integer templateVersion);
    void saveDraft(RegistrationChecklistDraftSaveReqVO reqVO);
    void publish(Integer templateVersion);
}

package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateUpdateReqVO;

public interface PersonnelStateService {
    PersonnelStateRespVO get(Long userId);
    void update(Long userId, PersonnelStateUpdateReqVO reqVO, Long operatorUserId);
    boolean isEnabled(Long userId);
    java.util.Set<Long> getDisabledUserIds(java.util.Collection<Long> userIds);
}

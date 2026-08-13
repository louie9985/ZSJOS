package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PersonnelStateUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PersonnelStateDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PersonnelStateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PersonnelStateServiceImpl implements PersonnelStateService {
    private static final Set<String> STATES = Set.of(STATE_ENABLED, STATE_DISABLED, STATE_DEPARTED);
    @Resource private PersonnelStateMapper mapper;
    @Resource private AdminUserApi adminUserApi;

    @Override
    public PersonnelStateRespVO get(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null) throw exception(PERSONNEL_USER_NOT_EXISTS);
        PersonnelStateDO state = mapper.selectByUserId(userId);
        PersonnelStateRespVO result = new PersonnelStateRespVO();
        result.setUserId(userId);
        result.setState(state == null ? STATE_ENABLED : state.getBusinessState());
        if (state != null) {
            result.setReason(state.getChangeReason()); result.setChangedAt(state.getChangedAt());
            result.setChangedByUserId(state.getChangedByUserId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, PersonnelStateUpdateReqVO reqVO, Long operatorUserId) {
        if (!STATES.contains(reqVO.getState())) throw exception(PERSONNEL_STATE_INVALID);
        if (adminUserApi.getUser(userId) == null) throw exception(PERSONNEL_USER_NOT_EXISTS);
        PersonnelStateDO state = mapper.selectByUserId(userId);
        if (state == null) {
            state = new PersonnelStateDO(); state.setSystemUserId(userId); state.setVersion(0);
        }
        state.setBusinessState(reqVO.getState()); state.setChangeReason(reqVO.getReason().trim());
        state.setChangedByUserId(operatorUserId); state.setChangedAt(LocalDateTime.now());
        if (state.getId() == null) mapper.insert(state); else mapper.updateById(state);
        adminUserApi.updateUserStatus(userId, STATE_ENABLED.equals(reqVO.getState())
                ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus(), reqVO.getReason());
    }

    @Override
    public boolean isEnabled(Long userId) {
        PersonnelStateDO state = mapper.selectByUserId(userId);
        return state == null || STATE_ENABLED.equals(state.getBusinessState());
    }
}

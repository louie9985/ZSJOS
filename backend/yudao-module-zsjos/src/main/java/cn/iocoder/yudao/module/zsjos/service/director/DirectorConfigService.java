package cn.iocoder.yudao.module.zsjos.service.director;

import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.director.DirectorConfigMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DIRECTOR_CONFIG_VERSION_CONFLICT;

@Service
public class DirectorConfigService {
    @Resource private DirectorConfigMapper mapper;

    public DirectorConfigVO.Resp get() { return convert(require()); }
    public int interviewAppointmentHours() { return require().getInterviewAppointmentHours(); }
    public int trialDays() { return require().getTrialDays(); }

    @Transactional(rollbackFor = Exception.class)
    public void update(DirectorConfigVO.UpdateReq request) {
        DirectorConfigDO current = require();
        int changed = mapper.update(null, new LambdaUpdateWrapper<DirectorConfigDO>()
                .eq(DirectorConfigDO::getId, current.getId()).eq(DirectorConfigDO::getVersion, request.getVersion())
                .set(DirectorConfigDO::getInterviewAppointmentHours, request.getInterviewAppointmentHours())
                .set(DirectorConfigDO::getPositioningDueHours, request.getPositioningDueHours())
                .set(DirectorConfigDO::getTrialDays, request.getTrialDays())
                .set(DirectorConfigDO::getVersion, request.getVersion() + 1));
        if (changed != 1) throw exception(DIRECTOR_CONFIG_VERSION_CONFLICT);
    }

    private DirectorConfigDO require() {
        DirectorConfigDO value = mapper.selectCurrent();
        if (value == null) throw exception(DIRECTOR_CONFIG_VERSION_CONFLICT);
        return value;
    }
    private DirectorConfigVO.Resp convert(DirectorConfigDO value) {
        DirectorConfigVO.Resp result = new DirectorConfigVO.Resp();
        result.setId(value.getId()); result.setInterviewAppointmentHours(value.getInterviewAppointmentHours());
        result.setPositioningDueHours(value.getPositioningDueHours()); result.setTrialDays(value.getTrialDays());
        result.setVersion(value.getVersion()); return result;
    }
}

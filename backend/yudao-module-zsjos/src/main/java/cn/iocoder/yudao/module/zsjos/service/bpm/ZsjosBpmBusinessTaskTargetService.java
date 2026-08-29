package cn.iocoder.yudao.module.zsjos.service.bpm;

import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;

public interface ZsjosBpmBusinessTaskTargetService {

    ZsjosBpmBusinessTaskTargetRespVO getTarget(String taskId, String view, Long userId);
}

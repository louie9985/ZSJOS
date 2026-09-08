package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;

public interface ClassTransferService {
    Long create(Long relationId, ClassTransferCreateReqVO req, Long userId);
    PageResult<ClassTransferRespVO> getMyPage(ClassTransferPageReqVO req, Long userId);
    ClassTransferRespVO get(Long id, Long userId);
    void handleProcessResult(String processInstanceId, Integer processStatus, String reason);
}

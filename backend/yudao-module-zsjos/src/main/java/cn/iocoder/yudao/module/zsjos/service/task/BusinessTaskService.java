package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskSummaryRespVO;

public interface BusinessTaskService {
    BusinessTaskSummaryRespVO getMySummary(Long userId);
    PageResult<BusinessTaskRespVO> getMyPage(Long userId, String bucket, int pageNo, int pageSize);
    PageResult<BusinessTaskRespVO> getMyPage(Long userId, BusinessTaskPageReqVO reqVO);
}

package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO;

public interface SalesDispatchStatusService {
    SalesDispatchStatusRespVO getMyStatus(Long userId);
    SalesDispatchStatusRespVO heartbeat(Long userId);
    SalesDispatchStatusRespVO updateMode(Long userId, boolean accepting);
    SalesDispatchStatusRespVO getStatus(Long userId);
    SalesDispatchStatusRespVO updateModeByManager(Long userId, boolean accepting);
    SalesDispatchStatusRespVO offline(Long userId);
}

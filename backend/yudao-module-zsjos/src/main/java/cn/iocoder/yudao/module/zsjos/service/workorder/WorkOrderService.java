package cn.iocoder.yudao.module.zsjos.service.workorder;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
public interface WorkOrderService {
    Long createScene(WorkOrderSceneCreateReqVO req, Long userId);
    void updateScene(WorkOrderSceneUpdateReqVO req, Long userId);
    PageResult<WorkOrderSceneRespVO> scenePage(int pageNo, int pageSize);
    WorkOrderSceneRespVO getScene(String code);
    Long create(WorkOrderCreateReqVO req, Long userId);
    void claim(Long id, WorkOrderActionReqVO req, Long userId);
    void complete(Long id, WorkOrderActionReqVO req, Long userId);
    void accept(Long id, WorkOrderActionReqVO req, Long userId);
    void returnForRework(Long id, WorkOrderActionReqVO req, Long userId);
    PageResult<WorkOrderRespVO> myPage(String status, int pageNo, int pageSize, Long userId);
    PageResult<WorkOrderRespVO> pool(String sceneCode, int pageNo, int pageSize, Long userId);
    WorkOrderRespVO get(Long id, Long userId);
}

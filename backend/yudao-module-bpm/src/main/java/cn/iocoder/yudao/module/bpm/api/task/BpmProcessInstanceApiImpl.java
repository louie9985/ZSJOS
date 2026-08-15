package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Flowable 流程实例 Api 实现类
 *
 * @author 芋道源码
 * @author jason
 */
@Service
@Validated
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    @Resource
    private BpmProcessInstanceService processInstanceService;

    @Override
    public String createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        return processInstanceService.createProcessInstance(userId, reqDTO);
    }

    @Override
    public void cancelProcessInstanceByStartUser(Long userId, String processInstanceId, String reason) {
        BpmProcessInstanceCancelReqVO request = new BpmProcessInstanceCancelReqVO();
        request.setId(processInstanceId);
        request.setReason(reason);
        processInstanceService.cancelProcessInstanceByStartUser(userId, request);
    }

    @Override
    public void terminateProcessInstanceByBusiness(Long operatorUserId, String processInstanceId,
                                                   String authorizationType, String reason) {
        if (operatorUserId == null || processInstanceId == null || processInstanceId.isBlank()
                || authorizationType == null || authorizationType.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Business process termination parameters must not be blank");
        }
        BpmProcessInstanceCancelReqVO request = new BpmProcessInstanceCancelReqVO();
        request.setId(processInstanceId);
        request.setReason("业务授权[" + authorizationType.trim() + "]：" + reason.trim());
        processInstanceService.cancelProcessInstanceByAdmin(operatorUserId, request);
    }

}

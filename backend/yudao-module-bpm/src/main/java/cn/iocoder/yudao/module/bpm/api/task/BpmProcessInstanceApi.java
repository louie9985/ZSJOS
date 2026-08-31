package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import jakarta.validation.Valid;

/**
 * 流程实例 Api 接口
 *
 * @author 芋道源码
 */
public interface BpmProcessInstanceApi {

    /**
     * 创建流程实例（提供给内部）
     *
     * @param userId 用户编号
     * @param reqDTO 创建信息
     * @return 实例的编号
     */
    String createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO);

    String createProcessInstance(BpmStartSubjectDTO subject, @Valid BpmProcessInstanceCreateReqDTO reqDTO);

    void cancelProcessInstanceByStartSubject(BpmStartSubjectDTO subject, String processInstanceId, String reason);

    void cancelProcessInstanceByStartUser(Long userId, String processInstanceId, String reason);

    /**
     * Terminates a process after the calling business domain has authorized the operator.
     * The BPM history records the real operator and the supplied authorization type.
     */
    void terminateProcessInstanceByBusiness(Long operatorUserId, String processInstanceId,
                                            String authorizationType, String reason);

    void terminateProcessInstanceByBusiness(BpmStartSubjectDTO operator, String processInstanceId,
                                            String authorizationType, String reason);

}

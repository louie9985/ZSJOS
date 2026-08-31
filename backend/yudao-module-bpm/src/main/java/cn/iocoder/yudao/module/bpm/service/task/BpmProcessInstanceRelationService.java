package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRelationCandidatePageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRelationRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceRelationDO;
import org.flowable.engine.repository.ProcessDefinition;

import java.util.List;
import java.util.Map;

public interface BpmProcessInstanceRelationService {

    List<PreparedRelation> prepare(Long startUserId, ProcessDefinition definition,
                                   BpmProcessDefinitionInfoDO definitionInfo, Map<String, Object> variables);

    void save(String sourceProcessInstanceId, List<PreparedRelation> relations);

    boolean hasRelationField(BpmProcessDefinitionInfoDO definitionInfo);

    List<String> getRelationFields(List<String> formFields);

    PageResult<BpmProcessInstanceRelationRespVO> getCandidatePage(
            Long userId, BpmProcessInstanceRelationCandidatePageReqVO reqVO);

    List<BpmProcessInstanceRelationRespVO> getRelationList(Long userId, String sourceProcessInstanceId);

    BpmProcessInstanceRelationDO getRelationAndCheckRead(Long userId, Long relationId);

    record PreparedRelation(String formField, int sort, BpmProcessInstanceRelationDO relation) {}
}

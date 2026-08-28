package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRelationCandidatePageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRelationRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceRelationDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceRelationMapper;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

@Service
public class BpmProcessInstanceRelationServiceImpl implements BpmProcessInstanceRelationService {

    private static final String COMPONENT_TYPE = "ProcessInstanceSelect";
    private static final int MAX_RELATIONS = 20;

    @Resource private BpmProcessInstanceRelationMapper relationMapper;
    @Resource private BpmProcessInstanceCopyMapper copyMapper;
    @Resource private HistoryService historyService;
    @Resource private BpmProcessDefinitionService processDefinitionService;
    @Resource private AdminUserApi adminUserApi;

    @Override
    public List<PreparedRelation> prepare(Long startUserId, ProcessDefinition definition,
                                          BpmProcessDefinitionInfoDO definitionInfo,
                                          Map<String, Object> variables) {
        List<String> fields = findRelationFields(definitionInfo);
        if (fields.isEmpty()) {
            return List.of();
        }
        List<PreparedRelation> result = new ArrayList<>();
        for (String field : fields) {
            Object raw = variables == null ? null : variables.get(field);
            if (raw == null) {
                continue; // 必填规则仍由 FormCreate 通用校验负责
            }
            if (!(raw instanceof List<?> values)) {
                throw exception(PROCESS_INSTANCE_RELATION_VALUE_INVALID, field);
            }
            if (values.size() > MAX_RELATIONS) {
                throw exception(PROCESS_INSTANCE_RELATION_TOO_MANY, MAX_RELATIONS);
            }
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < values.size(); i++) {
                if (!(values.get(i) instanceof String targetId) || StrUtil.isBlank(targetId)) {
                    throw exception(PROCESS_INSTANCE_RELATION_VALUE_INVALID, field);
                }
                if (!unique.add(targetId)) {
                    throw exception(PROCESS_INSTANCE_RELATION_DUPLICATE, targetId);
                }
                HistoricProcessInstance target = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceTenantId(FlowableUtils.getTenantId()).processInstanceId(targetId)
                        .includeProcessVariables().singleResult();
                if (target == null || !Objects.equals(target.getStartUserId(), String.valueOf(startUserId))) {
                    throw exception(PROCESS_INSTANCE_RELATION_TARGET_FORBIDDEN, targetId);
                }
                ProcessDefinition targetDefinition = processDefinitionService
                        .getProcessDefinition(target.getProcessDefinitionId());
                BpmProcessDefinitionInfoDO targetInfo = processDefinitionService
                        .getProcessDefinitionInfo(target.getProcessDefinitionId());
                Long targetStartUserId = BpmStartSubjectDTO.getAdminUserId(target.getStartUserId());
                AdminUserRespDTO startUser = targetStartUserId == null ? null : adminUserApi.getUser(targetStartUserId);
                BpmProcessInstanceRelationDO relation = new BpmProcessInstanceRelationDO()
                        .setTargetProcessInstanceId(targetId)
                        .setFormField(field).setSort(i)
                        .setTargetNameSnapshot(target.getName())
                        .setTargetProcessDefinitionIdSnapshot(target.getProcessDefinitionId())
                        .setTargetProcessDefinitionNameSnapshot(targetDefinition == null ? null : targetDefinition.getName())
                        .setTargetDisplayNoSnapshot(targetInfo != null && targetInfo.getProcessIdRule() != null
                                && Boolean.TRUE.equals(targetInfo.getProcessIdRule().getEnable()) ? targetId : null)
                        .setTargetBusinessKeySnapshot(target.getBusinessKey())
                        .setTargetStartUserNameSnapshot(startUser == null ? null : startUser.getNickname())
                        .setTargetStartTimeSnapshot(DateUtils.of(target.getStartTime()));
                result.add(new PreparedRelation(field, i, relation));
            }
        }
        return result;
    }

    @Override
    public void save(String sourceProcessInstanceId, List<PreparedRelation> relations) {
        for (PreparedRelation prepared : relations) {
            relationMapper.insert(prepared.relation().setSourceProcessInstanceId(sourceProcessInstanceId));
        }
    }

    @Override
    public boolean hasRelationField(BpmProcessDefinitionInfoDO definitionInfo) {
        return !findRelationFields(definitionInfo).isEmpty();
    }

    @Override
    public List<String> getRelationFields(List<String> formFields) {
        if (CollUtil.isEmpty(formFields)) return List.of();
        return findRelationFields(new BpmProcessDefinitionInfoDO().setFormFields(formFields));
    }

    @Override
    public PageResult<BpmProcessInstanceRelationRespVO> getCandidatePage(
            Long userId, BpmProcessInstanceRelationCandidatePageReqVO reqVO) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(FlowableUtils.getTenantId())
                .startedBy(String.valueOf(userId)).includeProcessVariables().orderByProcessInstanceStartTime().desc();
        if (StrUtil.isNotBlank(reqVO.getKeyword())) query.processInstanceNameLike("%" + reqVO.getKeyword() + "%");
        if (StrUtil.isNotBlank(reqVO.getProcessDefinitionKey())) {
            query.processDefinitionKey(reqVO.getProcessDefinitionKey());
        }
        if (reqVO.getStatus() != null) {
            query.variableValueEquals(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, reqVO.getStatus());
        }
        if (reqVO.getStartTime() != null && reqVO.getStartTime().length == 2) {
            query.startedAfter(DateUtils.of(reqVO.getStartTime()[0]));
            query.startedBefore(DateUtils.of(reqVO.getStartTime()[1]));
        }
        long total = query.count();
        List<HistoricProcessInstance> rows = query.listPage(PageUtils.getStart(reqVO), reqVO.getPageSize());
        return new PageResult<>(rows.stream().map(this::buildCandidate).toList(), total);
    }

    @Override
    public List<BpmProcessInstanceRelationRespVO> getRelationList(Long userId, String sourceProcessInstanceId) {
        checkParticipant(userId, sourceProcessInstanceId);
        return relationMapper.selectListBySource(sourceProcessInstanceId).stream().map(this::buildRelation).toList();
    }

    @Override
    public BpmProcessInstanceRelationDO getRelationAndCheckRead(Long userId, Long relationId) {
        BpmProcessInstanceRelationDO relation = relationMapper.selectById(relationId);
        if (relation == null) throw exception(PROCESS_INSTANCE_RELATION_NOT_EXISTS);
        checkParticipant(userId, relation.getSourceProcessInstanceId());
        return relation;
    }

    private void checkParticipant(Long userId, String sourceId) {
        HistoricProcessInstance source = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(FlowableUtils.getTenantId()).processInstanceId(sourceId).singleResult();
        if (source == null) throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        if (Objects.equals(source.getStartUserId(), String.valueOf(userId))) return;
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(sourceId).list();
        if (tasks.stream().anyMatch(task -> Objects.equals(task.getAssignee(), String.valueOf(userId))
                || Objects.equals(task.getOwner(), String.valueOf(userId)))) return;
        Long copyCount = copyMapper.selectCount(new LambdaQueryWrapperX<BpmProcessInstanceCopyDO>()
                .eq(BpmProcessInstanceCopyDO::getProcessInstanceId, sourceId)
                .eq(BpmProcessInstanceCopyDO::getUserId, userId));
        if (copyCount != null && copyCount > 0) return;
        throw exception(PROCESS_INSTANCE_RELATION_FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private List<String> findRelationFields(BpmProcessDefinitionInfoDO definitionInfo) {
        if (definitionInfo == null || CollUtil.isEmpty(definitionInfo.getFormFields())) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String json : definitionInfo.getFormFields()) {
            Object node = JsonUtils.parseObject(json, Object.class);
            collectFields(node, result);
        }
        return new ArrayList<>(result);
    }

    private void collectFields(Object node, Set<String> result) {
        if (node instanceof Map<?, ?> map) {
            if (COMPONENT_TYPE.equals(map.get("type")) && map.get("field") instanceof String field
                    && StrUtil.isNotBlank(field)) result.add(field);
            map.values().forEach(value -> collectFields(value, result));
        } else if (node instanceof Collection<?> collection) {
            collection.forEach(value -> collectFields(value, result));
        }
    }

    private BpmProcessInstanceRelationRespVO buildCandidate(HistoricProcessInstance target) {
        ProcessDefinition definition = processDefinitionService.getProcessDefinition(target.getProcessDefinitionId());
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(target.getProcessDefinitionId());
        Long startUserId = BpmStartSubjectDTO.getAdminUserId(target.getStartUserId());
        AdminUserRespDTO user = startUserId == null ? null : adminUserApi.getUser(startUserId);
        return new BpmProcessInstanceRelationRespVO().setTargetProcessInstanceId(target.getId())
                .setName(target.getName()).setProcessDefinitionId(target.getProcessDefinitionId())
                .setProcessDefinitionName(definition == null ? null : definition.getName())
                .setProcessDefinitionKey(definition == null ? null : definition.getKey())
                .setDisplayNo(info != null && info.getProcessIdRule() != null
                        && Boolean.TRUE.equals(info.getProcessIdRule().getEnable()) ? target.getId() : null)
                .setBusinessKey(target.getBusinessKey()).setStartUserName(user == null ? null : user.getNickname())
                .setStartTime(DateUtils.of(target.getStartTime())).setStatus(FlowableUtils.getProcessInstanceStatus(target))
                .setDetailAvailable(true);
    }

    private BpmProcessInstanceRelationRespVO buildRelation(BpmProcessInstanceRelationDO relation) {
        HistoricProcessInstance target = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(FlowableUtils.getTenantId())
                .processInstanceId(relation.getTargetProcessInstanceId()).includeProcessVariables().singleResult();
        ProcessDefinition definition = target == null ? null
                : processDefinitionService.getProcessDefinition(target.getProcessDefinitionId());
        return new BpmProcessInstanceRelationRespVO().setId(relation.getId())
                .setFormField(relation.getFormField()).setSort(relation.getSort())
                .setTargetProcessInstanceId(relation.getTargetProcessInstanceId())
                .setName(relation.getTargetNameSnapshot())
                .setProcessDefinitionId(relation.getTargetProcessDefinitionIdSnapshot())
                .setProcessDefinitionName(relation.getTargetProcessDefinitionNameSnapshot())
                .setProcessDefinitionKey(definition == null ? null : definition.getKey())
                .setDisplayNo(relation.getTargetDisplayNoSnapshot())
                .setBusinessKey(relation.getTargetBusinessKeySnapshot())
                .setStartUserName(relation.getTargetStartUserNameSnapshot())
                .setStartTime(relation.getTargetStartTimeSnapshot())
                .setStatus(target == null ? null : FlowableUtils.getProcessInstanceStatus(target))
                .setDetailAvailable(target != null);
    }
}

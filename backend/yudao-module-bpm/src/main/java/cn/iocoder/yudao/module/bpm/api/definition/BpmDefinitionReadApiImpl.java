package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import jakarta.annotation.Resource;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BpmDefinitionReadApiImpl implements BpmDefinitionReadApi {

    @Resource
    private BpmFormService formService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Override
    public BpmFormMetadataRespDTO getForm(Long id) {
        BpmFormDO form = id == null ? null : formService.getForm(id);
        return form == null ? null : BeanUtils.toBean(form, BpmFormMetadataRespDTO.class);
    }

    @Override
    public List<BpmFormMetadataRespDTO> getForms() {
        return BeanUtils.toBean(formService.getFormList(), BpmFormMetadataRespDTO.class);
    }

    @Override
    public BpmProcessDefinitionMetadataRespDTO getPublishedProcessDefinition(String key) {
        ProcessDefinition definition = key == null ? null : processDefinitionService.getActiveProcessDefinition(key);
        return definition == null ? null : toMetadata(definition);
    }

    @Override
    public List<BpmProcessDefinitionMetadataRespDTO> getPublishedProcessDefinitions() {
        List<ProcessDefinition> definitions = processDefinitionService.getProcessDefinitionListBySuspensionState(
                SuspensionState.ACTIVE.getStateCode());
        Map<String, ProcessDefinition> latestByKey = new LinkedHashMap<>();
        definitions.stream()
                .sorted(Comparator.comparing(ProcessDefinition::getVersion).reversed())
                .forEach(definition -> latestByKey.putIfAbsent(definition.getKey(), definition));
        return latestByKey.values().stream().map(this::toMetadata).toList();
    }

    private BpmProcessDefinitionMetadataRespDTO toMetadata(ProcessDefinition definition) {
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(definition.getId());
        BpmProcessDefinitionMetadataRespDTO result = new BpmProcessDefinitionMetadataRespDTO();
        result.setId(definition.getId());
        result.setKey(definition.getKey());
        result.setName(definition.getName());
        result.setVersion(definition.getVersion());
        result.setDeploymentId(definition.getDeploymentId());
        result.setSuspended(definition.isSuspended());
        result.setFormId(info == null ? null : info.getFormId());
        result.setDescription(info == null ? null : info.getDescription());
        return result;
    }
}

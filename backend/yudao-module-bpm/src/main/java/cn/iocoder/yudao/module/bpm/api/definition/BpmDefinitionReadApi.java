package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;

import java.util.List;

/** Public read-only BPM metadata boundary for business modules. */
public interface BpmDefinitionReadApi {

    BpmFormMetadataRespDTO getForm(Long id);

    List<BpmFormMetadataRespDTO> getForms();

    BpmProcessDefinitionMetadataRespDTO getPublishedProcessDefinition(String key);

    List<BpmProcessDefinitionMetadataRespDTO> getPublishedProcessDefinitions();
}

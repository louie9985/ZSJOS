package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSchemaSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplatePublishReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialProcessDefinitionRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;

import java.util.List;

public interface MaterialTypeService {

    List<MaterialTypeRespVO> getTypeList();

    List<MaterialProcessDefinitionRespVO> getPublishedProcessDefinitions();

    MaterialTypeRespVO getType(Long id);

    Long createType(MaterialTypeSaveReqVO request);

    void updateType(Long id, MaterialTypeSaveReqVO request);

    List<MaterialTemplateRespVO> getSchemaVersions(Long materialTypeId);

    Long saveSchemaDraft(Long materialTypeId, MaterialSchemaSaveReqVO request);

    void publishSchema(Long materialTypeId, MaterialTemplatePublishReqVO request, Long userId);

    MaterialTypeDO requireType(Long id);

    MaterialSchemaVersionDO requirePublishedSchema(MaterialTypeDO type);

    void ensureDefaultTypes();
}

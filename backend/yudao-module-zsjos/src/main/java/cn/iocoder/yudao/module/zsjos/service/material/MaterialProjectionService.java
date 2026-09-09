package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDimensionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFieldIndexDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFileDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialDimensionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFieldIndexMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MaterialProjectionService {

    @Resource
    private MaterialDimensionMapper dimensionMapper;
    @Resource
    private MaterialFieldIndexMapper fieldIndexMapper;
    @Resource
    private MaterialFileMapper materialFileMapper;

    public void replaceDraftProjections(Long materialVersionId,
                                        MaterialSchemaService.NormalizedMaterial normalized) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        dimensionMapper.deletePhysicalByVersionId(materialVersionId, tenantId);
        fieldIndexMapper.deletePhysicalByVersionId(materialVersionId, tenantId);
        materialFileMapper.deletePhysicalByVersionId(materialVersionId, tenantId);
        insertProjections(materialVersionId, normalized);
    }

    public void insertProjections(Long materialVersionId,
                                  MaterialSchemaService.NormalizedMaterial normalized) {
        List<MaterialDimensionDO> dimensions = normalized.dimensions().stream().map(value -> {
            MaterialDimensionDO row = new MaterialDimensionDO();
            row.setMaterialVersionId(materialVersionId);
            row.setDimensionKey(value.dimensionKey());
            row.setDimensionValue(value.value());
            row.setLabelSnapshot(value.label());
            row.setUnlimited(value.unlimited());
            return row;
        }).toList();
        if (!dimensions.isEmpty()) {
            dimensionMapper.insertBatch(dimensions);
        }
        List<MaterialFieldIndexDO> indexes = normalized.indexes().stream().map(value -> {
            MaterialFieldIndexDO row = new MaterialFieldIndexDO();
            row.setMaterialVersionId(materialVersionId);
            row.setFieldKey(value.fieldKey());
            row.setGroupIndex(value.groupIndex());
            row.setValueCode(value.valueCode());
            row.setLabelSnapshot(value.labelSnapshot());
            row.setTextValue(value.textValue());
            row.setNumberValue(value.numberValue());
            row.setDateValue(value.dateValue());
            row.setDatetimeValue(value.datetimeValue());
            return row;
        }).toList();
        if (!indexes.isEmpty()) {
            fieldIndexMapper.insertBatch(indexes);
        }
        List<MaterialFileDO> files = normalized.files().stream().map(value -> {
            MaterialFileDO row = new MaterialFileDO();
            row.setMaterialVersionId(materialVersionId);
            row.setFieldKey(value.fieldKey());
            row.setGroupIndex(value.groupIndex());
            row.setInfraFileId(value.infraFileId());
            row.setFileUrlSnapshot(value.url());
            row.setOriginalName(value.originalName());
            row.setContentType(value.contentType());
            row.setFileSize(value.fileSize());
            row.setUploadedByUserId(value.uploadedByUserId());
            return row;
        }).toList();
        if (!files.isEmpty()) {
            materialFileMapper.insertBatch(files);
        }
    }
}

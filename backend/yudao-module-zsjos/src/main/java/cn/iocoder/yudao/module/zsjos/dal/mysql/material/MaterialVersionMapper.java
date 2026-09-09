package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MaterialVersionMapper extends BaseMapperX<MaterialVersionDO> {
    default List<MaterialVersionDO> selectByMaterialId(Long materialId) {
        return selectList(new LambdaQueryWrapperX<MaterialVersionDO>()
                .eq(MaterialVersionDO::getMaterialId, materialId)
                .orderByDesc(MaterialVersionDO::getVersionNo));
    }

    @Select("SELECT * FROM zsjos_material_version WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialVersionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_material_version WHERE material_id=#{materialId} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' ORDER BY version_no DESC LIMIT 1 FOR UPDATE")
    MaterialVersionDO selectLatestForUpdate(@Param("materialId") Long materialId,
                                            @Param("tenantId") Long tenantId);

    default int updateDraft(MaterialVersionDO row, Integer expectedVersion) {
        return update(null, new LambdaUpdateWrapper<MaterialVersionDO>()
                .eq(MaterialVersionDO::getId, row.getId())
                .eq(MaterialVersionDO::getVersion, expectedVersion)
                .eq(MaterialVersionDO::getStatus, "DRAFT")
                .set(MaterialVersionDO::getSchemaVersionId, row.getSchemaVersionId())
                .set(MaterialVersionDO::getTitle, row.getTitle())
                .set(MaterialVersionDO::getCoverSnapshotJson, row.getCoverSnapshotJson())
                .set(MaterialVersionDO::getSummary, row.getSummary())
                .set(MaterialVersionDO::getValuesJson, row.getValuesJson())
                .set(MaterialVersionDO::getFieldSnapshotJson, row.getFieldSnapshotJson())
                .set(MaterialVersionDO::getDictSnapshotJson, row.getDictSnapshotJson())
                .set(MaterialVersionDO::getFileSnapshotJson, row.getFileSnapshotJson())
                .set(MaterialVersionDO::getSearchText, row.getSearchText())
                .set(MaterialVersionDO::getContentHash, row.getContentHash())
                .set(MaterialVersionDO::getVersion, expectedVersion + 1));
    }

    default int submit(Long id, Integer expectedVersion, String processInstanceId, String processDefinitionId,
                       String processDefinitionKey, Integer processDefinitionVersion, String businessKey,
                       Long userId, LocalDateTime submittedAt) {
        return update(null, new LambdaUpdateWrapper<MaterialVersionDO>()
                .eq(MaterialVersionDO::getId, id).eq(MaterialVersionDO::getVersion, expectedVersion)
                .eq(MaterialVersionDO::getStatus, "DRAFT")
                .set(MaterialVersionDO::getStatus, "IN_APPROVAL")
                .set(MaterialVersionDO::getProcessInstanceId, processInstanceId)
                .set(MaterialVersionDO::getProcessDefinitionId, processDefinitionId)
                .set(MaterialVersionDO::getProcessDefinitionKey, processDefinitionKey)
                .set(MaterialVersionDO::getProcessDefinitionVersion, processDefinitionVersion)
                .set(MaterialVersionDO::getBusinessKey, businessKey)
                .set(MaterialVersionDO::getSubmittedByUserId, userId)
                .set(MaterialVersionDO::getSubmittedAt, submittedAt)
                .set(MaterialVersionDO::getVersion, expectedVersion + 1));
    }

    default int transition(Long id, String expectedStatus, String targetStatus, LocalDateTime effectiveAt,
                           LocalDateTime rejectedAt, String rejectionReason) {
        return update(null, new LambdaUpdateWrapper<MaterialVersionDO>()
                .eq(MaterialVersionDO::getId, id).eq(MaterialVersionDO::getStatus, expectedStatus)
                .set(MaterialVersionDO::getStatus, targetStatus)
                .set(effectiveAt != null, MaterialVersionDO::getEffectiveAt, effectiveAt)
                .set(rejectedAt != null, MaterialVersionDO::getRejectedAt, rejectedAt)
                .set(MaterialVersionDO::getRejectionReason, rejectionReason)
                .setSql("version = version + 1"));
    }
}

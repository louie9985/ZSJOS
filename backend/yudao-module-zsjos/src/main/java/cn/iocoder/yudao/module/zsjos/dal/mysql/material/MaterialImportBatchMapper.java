package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportBatchDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface MaterialImportBatchMapper extends BaseMapperX<MaterialImportBatchDO> {

    default PageResult<MaterialImportBatchDO> selectPage(MaterialImportPageReqVO request, Long userId) {
        return selectPage(request, new LambdaQueryWrapperX<MaterialImportBatchDO>()
                .eqIfPresent(MaterialImportBatchDO::getMaterialTypeId, request.getMaterialTypeId())
                .eqIfPresent(MaterialImportBatchDO::getStatus, request.getStatus())
                .eq(MaterialImportBatchDO::getCreatedByUserId, userId)
                .orderByDesc(MaterialImportBatchDO::getId));
    }

    default MaterialImportBatchDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(MaterialImportBatchDO::getIdempotencyKey, idempotencyKey);
    }

    @Select("SELECT * FROM zsjos_material_import_batch WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' FOR UPDATE")
    MaterialImportBatchDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int markCommitted(MaterialImportBatchDO batch, Integer expectedVersion, Long userId,
                              LocalDateTime confirmedAt) {
        return update(null, new LambdaUpdateWrapper<MaterialImportBatchDO>()
                .eq(MaterialImportBatchDO::getId, batch.getId())
                .eq(MaterialImportBatchDO::getStatus, "PREVIEWED")
                .eq(MaterialImportBatchDO::getVersion, expectedVersion)
                .set(MaterialImportBatchDO::getStatus, "COMMITTED")
                .set(MaterialImportBatchDO::getConfirmedByUserId, userId)
                .set(MaterialImportBatchDO::getConfirmedAt, confirmedAt)
                .set(MaterialImportBatchDO::getVersion, expectedVersion + 1));
    }
}

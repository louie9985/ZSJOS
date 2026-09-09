package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialReferenceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaterialReferenceMapper extends BaseMapperX<MaterialReferenceDO> {
    default MaterialReferenceDO selectByTarget(Long materialVersionId, Long targetContentVersionId) {
        return selectOne(new LambdaQueryWrapperX<MaterialReferenceDO>()
                .eq(MaterialReferenceDO::getMaterialVersionId, materialVersionId)
                .eq(MaterialReferenceDO::getTargetContentVersionId, targetContentVersionId));
    }

    default MaterialReferenceDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<MaterialReferenceDO>()
                .eq(MaterialReferenceDO::getIdempotencyKey, idempotencyKey));
    }
}

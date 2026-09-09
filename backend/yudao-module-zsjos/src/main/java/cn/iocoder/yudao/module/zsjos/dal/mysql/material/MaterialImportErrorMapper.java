package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialImportErrorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaterialImportErrorMapper extends BaseMapperX<MaterialImportErrorDO> {

    default List<MaterialImportErrorDO> selectByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<MaterialImportErrorDO>()
                .eq(MaterialImportErrorDO::getBatchId, batchId)
                .orderByAsc(MaterialImportErrorDO::getRowNo)
                .orderByAsc(MaterialImportErrorDO::getId));
    }
}

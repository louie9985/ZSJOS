package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetImportRowDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper
public interface EamAssetImportRowMapper extends BaseMapperX<EamAssetImportRowDO> {

    default EamAssetImportRowDO selectBySource(String fileHash, String sheetName, Integer rowNum) {
        return selectOne(new LambdaQueryWrapperX<EamAssetImportRowDO>()
                .eq(EamAssetImportRowDO::getFileHash, fileHash)
                .eq(EamAssetImportRowDO::getSheetName, sheetName)
                .eq(EamAssetImportRowDO::getRowNum, rowNum));
    }

    default Map<Integer, EamAssetImportRowDO> selectMapByFile(String fileHash, String sheetName) {
        return selectList(new LambdaQueryWrapperX<EamAssetImportRowDO>()
                .eq(EamAssetImportRowDO::getFileHash, fileHash)
                .eq(EamAssetImportRowDO::getSheetName, sheetName)).stream()
                .collect(Collectors.toMap(EamAssetImportRowDO::getRowNum, Function.identity(), (a, b) -> a));
    }

}

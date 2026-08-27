package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.RETURNED_TO_SUPPLIER;
import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.SCRAPPED;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.EXPIRY_FIELD_KEY;

@Mapper
public interface EamAssetMapper extends BaseMapperX<EamAssetDO> {

    default PageResult<EamAssetDO> selectPage(EamAssetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamAssetDO>()
                .likeIfPresent(EamAssetDO::getName, reqVO.getName())
                .likeIfPresent(EamAssetDO::getAssetCode, reqVO.getAssetCode())
                .eqIfPresent(EamAssetDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(EamAssetDO::getStatus, reqVO.getStatus())
                .eqIfPresent(EamAssetDO::getUseDeptId, reqVO.getUseDeptId())
                .eqIfPresent(EamAssetDO::getUseEmployeeId, reqVO.getUseEmployeeId())
                .apply(reqVO.getExtFieldKey() != null && reqVO.getExtFieldValue() != null,
                        "JSON_UNQUOTE(JSON_EXTRACT(ext_fields, {0})) = {1}",
                        "$." + reqVO.getExtFieldKey(), reqVO.getExtFieldValue())
                .orderByDesc(EamAssetDO::getId));
    }

    default EamAssetDO selectByAssetCode(String assetCode) {
        return selectOne(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getAssetCode, assetCode));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getCategoryId, categoryId));
    }

    default List<EamAssetDO> selectListByAssetCodes(Collection<String> assetCodes) {
        if (assetCodes == null || assetCodes.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<EamAssetDO>()
                .in(EamAssetDO::getAssetCode, assetCodes));
    }

    default EamAssetDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getId, id).last("FOR UPDATE"));
    }

    default List<EamAssetDO> selectIdleListByCategoryId(Long categoryId) {
        return selectList(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getCategoryId, categoryId)
                .eq(EamAssetDO::getStatus, 0)
                .orderByAsc(EamAssetDO::getId));
    }

    default List<EamAssetDO> selectListByUseEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getUseEmployeeId, employeeId).orderByDesc(EamAssetDO::getId));
    }

    default EamAssetDO selectBySnAndCategoryId(String sn, Long categoryId) {
        return selectOne(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getSn, sn).eq(EamAssetDO::getCategoryId, categoryId));
    }

    default EamAssetDO selectByIdentityAndCategoryId(String identity, Long categoryId) {
        return selectOne(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getCategoryId, categoryId)
                .and(wrapper -> wrapper.eq(EamAssetDO::getSn, identity)
                        .or().eq(EamAssetDO::getAssetCode, identity)));
    }

    default List<EamAssetDO> selectExpiringList(LocalDate deadline) {
        return selectList(new LambdaQueryWrapperX<EamAssetDO>()
                .notIn(EamAssetDO::getStatus, SCRAPPED.getStatus(), RETURNED_TO_SUPPLIER.getStatus())
                .apply("JSON_UNQUOTE(JSON_EXTRACT(ext_fields, {0})) <= {1}",
                        "$." + EXPIRY_FIELD_KEY, deadline.toString()));
    }

}

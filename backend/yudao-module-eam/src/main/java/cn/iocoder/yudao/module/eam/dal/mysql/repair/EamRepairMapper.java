package cn.iocoder.yudao.module.eam.dal.mysql.repair;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.repair.EamRepairDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamRepairMapper extends BaseMapperX<EamRepairDO> {

    default PageResult<EamRepairDO> selectPage(EamRepairPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamRepairDO>()
                .eqIfPresent(EamRepairDO::getAssetId, reqVO.getAssetId())
                .orderByDesc(EamRepairDO::getId));
    }

    default List<EamRepairDO> selectListByAssetId(Long assetId) {
        return selectList(new LambdaQueryWrapperX<EamRepairDO>()
                .eq(EamRepairDO::getAssetId, assetId)
                .orderByDesc(EamRepairDO::getStartTime));
    }

}

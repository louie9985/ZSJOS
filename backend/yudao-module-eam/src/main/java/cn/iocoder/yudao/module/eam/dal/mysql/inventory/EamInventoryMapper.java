package cn.iocoder.yudao.module.eam.dal.mysql.inventory;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamInventoryMapper extends BaseMapperX<EamInventoryDO> {

    default PageResult<EamInventoryDO> selectPage(EamInventoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamInventoryDO>()
                .likeIfPresent(EamInventoryDO::getName, reqVO.getName())
                .eqIfPresent(EamInventoryDO::getStatus, reqVO.getStatus())
                .orderByDesc(EamInventoryDO::getId));
    }

}

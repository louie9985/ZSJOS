package cn.iocoder.yudao.module.eam.dal.mysql.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamTransferMapper extends BaseMapperX<EamTransferDO> {

    default PageResult<EamTransferDO> selectPage(EamTransferPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamTransferDO>()
                .eqIfPresent(EamTransferDO::getType, reqVO.getType())
                .eqIfPresent(EamTransferDO::getAssetId, reqVO.getAssetId())
                .eqIfPresent(EamTransferDO::getStatus, reqVO.getStatus())
                .likeIfPresent(EamTransferDO::getNo, reqVO.getNo())
                .orderByDesc(EamTransferDO::getId));
    }

}

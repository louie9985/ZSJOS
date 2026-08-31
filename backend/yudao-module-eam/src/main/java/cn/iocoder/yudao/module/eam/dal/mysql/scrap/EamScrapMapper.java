package cn.iocoder.yudao.module.eam.dal.mysql.scrap;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.scrap.EamScrapDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamScrapMapper extends BaseMapperX<EamScrapDO> {

    default PageResult<EamScrapDO> selectPage(EamScrapPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamScrapDO>()
                .eqIfPresent(EamScrapDO::getAssetId, reqVO.getAssetId())
                .eqIfPresent(EamScrapDO::getStatus, reqVO.getStatus())
                .likeIfPresent(EamScrapDO::getNo, reqVO.getNo())
                .orderByDesc(EamScrapDO::getId));
    }

}

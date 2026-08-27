package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper extends BaseMapperX<NoticeDO> {

    default PageResult<NoticeDO> selectPage(NoticePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NoticeDO>()
                .likeIfPresent(NoticeDO::getTitle, reqVO.getTitle())
                .eqIfPresent(NoticeDO::getStatus, reqVO.getStatus())
                .eqIfPresent(NoticeDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(NoticeDO::getId));
    }

    default PageResult<NoticeDO> selectPublishedPage(cn.iocoder.yudao.framework.common.pojo.PageParam reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NoticeDO>()
                .eq(NoticeDO::getPublishStatus, "PUBLISHED")
                .orderByDesc(NoticeDO::getPublishTime).orderByDesc(NoticeDO::getId));
    }

}

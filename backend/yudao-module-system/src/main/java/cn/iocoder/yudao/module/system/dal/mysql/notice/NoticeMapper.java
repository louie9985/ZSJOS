package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper extends BaseMapperX<NoticeDO> {

    default PageResult<NoticeDO> selectPage(NoticePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX<NoticeDO>()
                .likeIfPresent("title", reqVO.getTitle())
                .eqIfPresent("status", reqVO.getStatus())
                .eqIfPresent("publish_status", reqVO.getPublishStatus())
                .orderByDesc("id"));
    }

    default PageResult<NoticeDO> selectPublishedPage(cn.iocoder.yudao.framework.common.pojo.PageParam reqVO) {
        return selectPublishedPage(reqVO, null);
    }

    default PageResult<NoticeDO> selectPublishedPage(cn.iocoder.yudao.framework.common.pojo.PageParam reqVO, Long userId) {
        return selectPage(reqVO, new QueryWrapperX<NoticeDO>()
                .eq("publish_status", "PUBLISHED")
                .and(userId != null, w -> w.isNull("audience_type").or().eq("audience_type", "ALL")
                        .or().apply("EXISTS (SELECT 1 FROM system_notice_recipient r WHERE r.notice_id = system_notice.id AND r.user_id = {0} AND r.deleted = 0 AND r.tenant_id = system_notice.tenant_id)", userId))
                .orderByDesc("CASE WHEN highlight_until IS NOT NULL AND highlight_until > NOW() THEN 1 ELSE 0 END")
                .orderByDesc("publish_time").orderByDesc("id"));
    }

}

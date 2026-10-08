package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeMyPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapperX<NoticeDO> {

    default PageResult<NoticeDO> selectPublishedPage(PageParam reqVO) {
        NoticeMyPageReqVO request = new NoticeMyPageReqVO();
        request.setPageNo(reqVO.getPageNo());
        request.setPageSize(reqVO.getPageSize());
        return selectPublishedPage(request, null);
    }

    @Select("SELECT * FROM system_notice WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    NoticeDO selectByIdForUpdate(Long id);

    default PageResult<NoticeDO> selectPage(NoticePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX<NoticeDO>()
                .eqIfPresent("status", reqVO.getStatus())
                .eqIfPresent("publish_status", reqVO.getPublishStatus())
                .and(reqVO.getTitle() != null && !reqVO.getTitle().isBlank(), w -> w.like("title", reqVO.getTitle()).or().like("source_dept_name", reqVO.getTitle()).or().like("publisher_name", reqVO.getTitle()))
                .orderByDesc("id"));
    }

    default PageResult<NoticeDO> selectPublishedPage(NoticeMyPageReqVO reqVO, Long userId) {
        return selectPage(reqVO, new QueryWrapperX<NoticeDO>()
                .eq("publish_status", "PUBLISHED")
                .eqIfPresent("type", reqVO.getType())
                .betweenIfPresent("publish_time", reqVO.getPublishTime())
                .and(reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank(), w -> w.like("title", reqVO.getKeyword()).or().like("source_dept_name", reqVO.getKeyword()).or().like("publisher_name", reqVO.getKeyword()))
                .apply(Boolean.TRUE.equals(reqVO.getHighlighted()), "highlight_until IS NOT NULL AND highlight_until > NOW()")
                .apply(Boolean.FALSE.equals(reqVO.getHighlighted()), "(highlight_until IS NULL OR highlight_until <= NOW())")
                .and(userId != null, w -> w.isNull("audience_type").or().eq("audience_type", "ALL")
                        .or().apply("EXISTS (SELECT 1 FROM system_notice_recipient r WHERE r.notice_id = system_notice.id AND r.user_id = {0} AND r.deleted = 0 AND r.tenant_id = system_notice.tenant_id)", userId))
                .apply(reqVO.getReadStatus() != null && Boolean.TRUE.equals(reqVO.getReadStatus()), "EXISTS (SELECT 1 FROM system_notice_read rr WHERE rr.notice_id = system_notice.id AND rr.user_id = {0} AND rr.deleted = 0 AND rr.tenant_id = system_notice.tenant_id)", userId)
                .apply(reqVO.getReadStatus() != null && Boolean.FALSE.equals(reqVO.getReadStatus()), "NOT EXISTS (SELECT 1 FROM system_notice_read rr WHERE rr.notice_id = system_notice.id AND rr.user_id = {0} AND rr.deleted = 0 AND rr.tenant_id = system_notice.tenant_id)", userId)
                .orderByDesc("CASE WHEN highlight_until IS NOT NULL AND highlight_until > NOW() THEN 1 ELSE 0 END")
                .orderByDesc("publish_time").orderByDesc("id"));
    }

    default List<NoticeDO> selectPublishedCursor(Long userId, LocalDateTime snapshotTime,
                                                  Boolean cursorHighlighted, LocalDateTime cursorPublishTime,
                                                  Long cursorId, int limit, String keyword, Integer type,
                                                  Boolean highlighted, Boolean readStatus,
                                                  LocalDateTime[] publishTime) {
        QueryWrapper<NoticeDO> query = new QueryWrapperX<NoticeDO>()
                .eq("publish_status", "PUBLISHED")
                .eqIfPresent("type", type)
                .betweenIfPresent("publish_time", publishTime)
                .and(keyword != null && !keyword.isBlank(), w -> w.like("title", keyword).or().like("source_dept_name", keyword).or().like("publisher_name", keyword))
                .apply(Boolean.TRUE.equals(highlighted), "highlight_until IS NOT NULL AND highlight_until > {0}", snapshotTime)
                .apply(Boolean.FALSE.equals(highlighted), "(highlight_until IS NULL OR highlight_until <= {0})", snapshotTime)
                .and(w -> w.isNull("audience_type").or().eq("audience_type", "ALL")
                        .or().apply("EXISTS (SELECT 1 FROM system_notice_recipient r WHERE r.notice_id = system_notice.id AND r.user_id = {0} AND r.deleted = 0 AND r.tenant_id = system_notice.tenant_id)", userId));
        if (cursorPublishTime != null && cursorId != null) {
            String cursorHighlight = Boolean.TRUE.equals(cursorHighlighted) ? "1" : "0";
            query.and(w -> w.apply("(CASE WHEN highlight_until IS NOT NULL AND highlight_until > {0} THEN 1 ELSE 0 END) < {1}", snapshotTime, cursorHighlight)
                    .or(nested -> nested.apply("(CASE WHEN highlight_until IS NOT NULL AND highlight_until > {0} THEN 1 ELSE 0 END) = {1}", snapshotTime, cursorHighlight)
                            .and(x -> x.lt("publish_time", cursorPublishTime)
                                    .or(y -> y.eq("publish_time", cursorPublishTime).lt("id", cursorId)))));
        }
        if (readStatus != null) {
            query.apply(readStatus ? "EXISTS (SELECT 1 FROM system_notice_read rr WHERE rr.notice_id = system_notice.id AND rr.user_id = {0} AND rr.deleted = 0 AND rr.tenant_id = system_notice.tenant_id)"
                    : "NOT EXISTS (SELECT 1 FROM system_notice_read rr WHERE rr.notice_id = system_notice.id AND rr.user_id = {0} AND rr.deleted = 0 AND rr.tenant_id = system_notice.tenant_id)", userId);
        }
        return selectList(query.orderByDesc("CASE WHEN highlight_until IS NOT NULL AND highlight_until > " +
                        "'" + snapshotTime + "' THEN 1 ELSE 0 END")
                .orderByDesc("publish_time").orderByDesc("id").last("LIMIT " + limit));
    }

}

package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeRecipientDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface NoticeRecipientMapper extends BaseMapperX<NoticeRecipientDO> {
    default void deleteByNoticeIds(Collection<Long> noticeIds) {
        if (!noticeIds.isEmpty()) delete(new LambdaQueryWrapper<NoticeRecipientDO>().in(NoticeRecipientDO::getNoticeId, noticeIds));
    }
    default List<NoticeRecipientDO> selectListByNoticeId(Long noticeId) {
        return selectList(new LambdaQueryWrapper<NoticeRecipientDO>().eq(NoticeRecipientDO::getNoticeId, noticeId));
    }

    default boolean existsByNoticeIdAndUserId(Long noticeId, Long userId) {
        return selectCount(Wrappers.<NoticeRecipientDO>lambdaQuery()
                .eq(NoticeRecipientDO::getNoticeId, noticeId)
                .eq(NoticeRecipientDO::getUserId, userId)) > 0;
    }

    default long selectCountByNoticeId(Long noticeId) {
        return selectCount(Wrappers.<NoticeRecipientDO>lambdaQuery()
                .eq(NoticeRecipientDO::getNoticeId, noticeId));
    }

    default Map<Long, Long> selectCountMapByNoticeIds(Collection<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = selectMaps(Wrappers.<NoticeRecipientDO>query()
                .select("notice_id", "COUNT(*) AS recipient_count")
                .in("notice_id", noticeIds)
                .groupBy("notice_id"));
        Map<Long, Long> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(((Number) row.get("notice_id")).longValue(),
                ((Number) row.get("recipient_count")).longValue()));
        return result;
    }
}

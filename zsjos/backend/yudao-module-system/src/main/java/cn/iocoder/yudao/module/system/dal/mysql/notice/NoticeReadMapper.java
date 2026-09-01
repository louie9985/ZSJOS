package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeReadDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface NoticeReadMapper extends BaseMapperX<NoticeReadDO> {
    default NoticeReadDO selectByNoticeIdAndUserId(Long noticeId, Long userId) {
        return selectOne(new LambdaQueryWrapper<NoticeReadDO>()
                .eq(NoticeReadDO::getNoticeId, noticeId).eq(NoticeReadDO::getUserId, userId));
    }

    default List<NoticeReadDO> selectListByNoticeIdsAndUserId(Collection<Long> noticeIds, Long userId) {
        if (noticeIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<NoticeReadDO>()
                .in(NoticeReadDO::getNoticeId, noticeIds).eq(NoticeReadDO::getUserId, userId));
    }

    default void deleteByNoticeIds(Collection<Long> noticeIds) {
        delete(new LambdaQueryWrapper<NoticeReadDO>().in(NoticeReadDO::getNoticeId, noticeIds));
    }
}

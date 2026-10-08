package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeShareDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;

@Mapper
public interface NoticeShareMapper extends BaseMapperX<NoticeShareDO> {
    default NoticeShareDO selectByNoticeId(Long noticeId) {
        return selectOne(new LambdaQueryWrapper<NoticeShareDO>().eq(NoticeShareDO::getNoticeId, noticeId));
    }
    default NoticeShareDO selectByToken(String token) {
        return selectOne(new LambdaQueryWrapper<NoticeShareDO>().eq(NoticeShareDO::getToken, token));
    }
    default void reopen(NoticeShareDO share) {
        share.setClosedBy(null); share.setClosedAt(null);
        update(share, new LambdaUpdateWrapper<NoticeShareDO>().eq(NoticeShareDO::getId, share.getId())
                .set(NoticeShareDO::getClosedBy, null).set(NoticeShareDO::getClosedAt, null));
    }
    /** Caller holds the notice row lock, also used by open/close/offline. */
    default void closeActive(Long noticeId, Long userId) {
        update(null, new LambdaUpdateWrapper<NoticeShareDO>()
                .eq(NoticeShareDO::getNoticeId, noticeId).eq(NoticeShareDO::getActive, true)
                .set(NoticeShareDO::getActive, false).set(NoticeShareDO::getClosedBy, userId)
                .set(NoticeShareDO::getClosedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
    }
}

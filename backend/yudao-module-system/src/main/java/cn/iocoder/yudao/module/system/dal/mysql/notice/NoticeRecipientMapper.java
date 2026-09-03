package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeRecipientDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface NoticeRecipientMapper extends BaseMapperX<NoticeRecipientDO> {
    default void deleteByNoticeIds(Collection<Long> noticeIds) {
        if (!noticeIds.isEmpty()) delete(new LambdaQueryWrapper<NoticeRecipientDO>().in(NoticeRecipientDO::getNoticeId, noticeIds));
    }
    default List<NoticeRecipientDO> selectListByNoticeId(Long noticeId) {
        return selectList(new LambdaQueryWrapper<NoticeRecipientDO>().eq(NoticeRecipientDO::getNoticeId, noticeId));
    }
}

package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeAttachmentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface NoticeAttachmentMapper extends BaseMapperX<NoticeAttachmentDO> {
    default List<NoticeAttachmentDO> selectListByNoticeId(Long noticeId) {
        return selectList(new LambdaQueryWrapper<NoticeAttachmentDO>()
                .eq(NoticeAttachmentDO::getNoticeId, noticeId).orderByAsc(NoticeAttachmentDO::getSort));
    }

    default void deleteByNoticeIds(Collection<Long> noticeIds) {
        delete(new LambdaQueryWrapper<NoticeAttachmentDO>().in(NoticeAttachmentDO::getNoticeId, noticeIds));
    }
}

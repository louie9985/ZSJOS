package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeAttachmentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    // The unique file binding survives logical deletion; tenant interception still applies.
    @Select("SELECT * FROM system_notice_attachment WHERE notice_id = #{noticeId}")
    List<NoticeAttachmentDO> selectListIncludingDeletedByNoticeId(Long noticeId);

    default void deleteByNoticeIdExceptFileIds(Long noticeId, Collection<Long> retainedFileIds) {
        delete(new LambdaQueryWrapper<NoticeAttachmentDO>()
                .eq(NoticeAttachmentDO::getNoticeId, noticeId)
                .notIn(!retainedFileIds.isEmpty(), NoticeAttachmentDO::getInfraFileId, retainedFileIds));
    }

    // Only called after file authorization, within the locked draft transaction;
    // the following updateById refreshes metadata and audit fields.
    @Update("UPDATE system_notice_attachment SET deleted = 0 WHERE id = #{id}"
            + " AND tenant_id = #{tenantId} AND notice_id = #{noticeId}"
            + " AND infra_file_id = #{infraFileId} AND deleted = 1")
    int restoreDeleted(NoticeAttachmentDO attachment);
}

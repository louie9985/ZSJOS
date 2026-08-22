package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentMapper extends BaseMapperX<ContentDO> {
    default List<ContentDO> selectByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ContentDO>()
                .in(ContentDO::getAccountId, accountIds)
                .orderByDesc(ContentDO::getUpdateTime).orderByDesc(ContentDO::getId));
    }
    default int advanceCurrentVersion(Long id, Integer expectedVersion, Integer nextVersion) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, expectedVersion)
                .set(ContentDO::getCurrentVersionNo, nextVersion)
                .set(ContentDO::getVersion, expectedVersion + 1));
    }
    default PageResult<ContentDO> selectPage(ContentPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<ContentDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(ContentDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(x -> x.like(ContentDO::getContentNo, req.getKeyword())
                    .or().like(ContentDO::getTitle, req.getKeyword())
                    .or().like(ContentDO::getTopic, req.getKeyword()));
        }
        if (!all) query.and(x -> x.in(ContentDO::getOwnerOperatorUserId, userIds).or()
                .in(ContentDO::getFilmingEditorUserId, userIds));
        return selectPage(req, query.orderByDesc(ContentDO::getUpdateTime).orderByDesc(ContentDO::getId));
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, version).eq(ContentDO::getStatus, from)
                .set(ContentDO::getStatus, to).set(ContentDO::getVersion, version + 1));
    }
    default int rejectTransition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, version).eq(ContentDO::getStatus, from)
                .set(ContentDO::getStatus, to).set(ContentDO::getVersion, version + 1)
                .setSql("reject_count = reject_count + 1"));
    }
}

package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.Collection;
import java.util.List;

@Mapper
public interface PositioningCardMapper extends BaseMapperX<PositioningCardDO> {
    default List<PositioningCardDO> selectByDirectorAndStudent(Long directorUserId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getDirectorUserId, directorUserId)
                .eq(PositioningCardDO::getStudentPersonId, studentPersonId)
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    default List<PositioningCardDO> selectByStudentAndAccountIds(Long studentPersonId, Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getStudentPersonId, studentPersonId)
                .in(PositioningCardDO::getAccountId, accountIds)
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    @Select("SELECT * FROM zsjos_positioning_card WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PositioningCardDO selectByIdForUpdate(Long id, Long tenantId);
    default PositioningCardDO selectByIpProcessId(String id) { return selectOne(PositioningCardDO::getIpProcessInstanceId, id); }
    default int updateByVersion(PositioningCardDO card, Integer version, String fromStatus) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>()
                .eq(PositioningCardDO::getId, card.getId()).eq(PositioningCardDO::getVersion, version)
                .eq(PositioningCardDO::getStatus, fromStatus)
                .set(PositioningCardDO::getStatus, card.getStatus())
                .set(PositioningCardDO::getIpProcessInstanceId, card.getIpProcessInstanceId())
                .set(PositioningCardDO::getIpReviewerUserId, card.getIpReviewerUserId())
                .set(PositioningCardDO::getVersion, version + 1));
    }
    default PageResult<PositioningCardDO> selectPage(PositioningCardPageReqVO req, Collection<Long> userIds,
                                                     Collection<Long> accountIds, boolean all) {
        LambdaQueryWrapperX<PositioningCardDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(PositioningCardDO::getStatus, req.getStatus());
        if (!all && accountIds.isEmpty()) query.in(PositioningCardDO::getDirectorUserId, userIds);
        if (!all && !accountIds.isEmpty()) query.and(x -> x.in(PositioningCardDO::getDirectorUserId, userIds).or()
                .in(PositioningCardDO::getAccountId, accountIds));
        return selectPage(req, query.orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, version).eq(PositioningCardDO::getStatus, from)
                .set(PositioningCardDO::getStatus, to).set(PositioningCardDO::getVersion, version + 1));
    }
    default int advanceVersionNo(Long id, Integer expectedVersion, Integer expectedVersionNo, Integer nextVersionNo) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, expectedVersion)
                .eq(PositioningCardDO::getVersionNo, expectedVersionNo)
                .set(PositioningCardDO::getVersionNo, nextVersionNo)
                .set(PositioningCardDO::getVersion, expectedVersion + 1));
    }
}

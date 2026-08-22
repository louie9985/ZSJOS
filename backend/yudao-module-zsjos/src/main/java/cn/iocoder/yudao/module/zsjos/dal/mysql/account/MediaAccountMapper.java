package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MediaAccountMapper extends BaseMapperX<MediaAccountDO> {
    default PageResult<MediaAccountDO> selectPage(MediaAccountPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> q = new LambdaQueryWrapperX<>();
        q.eqIfPresent(MediaAccountDO::getSStage, req.getSStage());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) q.and(x -> x.like(MediaAccountDO::getAccountNo, req.getKeyword()).or().like(MediaAccountDO::getNickname, req.getKeyword()));
        if (!all) q.and(x -> x.in(MediaAccountDO::getOwnerOperatorUserId, userIds).or()
                .in(MediaAccountDO::getDirectorUserId, userIds));
        return selectPage(req, q.orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }
    default List<Long> selectVisibleIds(Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = new LambdaQueryWrapperX<>();
        query.select(MediaAccountDO::getId);
        if (!all) query.and(x -> x.in(MediaAccountDO::getOwnerOperatorUserId, userIds).or()
                .in(MediaAccountDO::getDirectorUserId, userIds));
        return selectList(query).stream().map(MediaAccountDO::getId).toList();
    }
    default MediaAccountDO selectByAccountNo(String accountNo) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountDO>().eq(MediaAccountDO::getAccountNo, accountNo));
    }

    default List<MediaAccountDO> selectByDirectorAndStudent(Long directorUserId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getDirectorUserId, directorUserId)
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default List<MediaAccountDO> selectByParticipantAndStudent(Long userId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId))
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default List<Long> selectParticipantStudentIds(Long userId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .select(MediaAccountDO::getStudentPersonId)
                .isNotNull(MediaAccountDO::getStudentPersonId)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId)))
                .stream().map(MediaAccountDO::getStudentPersonId).distinct().toList();
    }

    default int updateStage(Long id, Integer version, String fromStage, String toStage, String stageVersion,
                            Long judgedByUserId, java.time.LocalDateTime enteredAt) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, id).eq(MediaAccountDO::getVersion, version)
                .eq(MediaAccountDO::getSStage, fromStage)
                .set(MediaAccountDO::getSStage, toStage).set(MediaAccountDO::getSStageVersion, stageVersion)
                .set(MediaAccountDO::getSStageJudgedByUserId, judgedByUserId)
                .set(MediaAccountDO::getSStageEnteredAt, enteredAt).set(MediaAccountDO::getVersion, version + 1));
    }
    default int updateProfile(MediaAccountDO account, Integer version) {
        account.setVersion(version + 1);
        return update(account, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId, account.getId()).eq(MediaAccountDO::getVersion, version));
    }
    default int updateRescue(Long id, Integer version, String status) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version).set(MediaAccountDO::getRescueStatus,status).set(MediaAccountDO::getVersion,version+1));
    }
    default int setRebindProcess(Long id, Integer version, String processId) {
        return update(null,new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version).isNull(MediaAccountDO::getRebindProcessInstanceId).set(MediaAccountDO::getRebindProcessInstanceId,processId).set(MediaAccountDO::getVersion,version+1));
    }
    default int claimRebind(Long id, Integer version, Long targetStudentId, Long requesterUserId, Long reviewerUserId) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId, id)
                .eq(MediaAccountDO::getVersion, version)
                .and(row -> row.isNull(MediaAccountDO::getRebindStatus)
                        .or().ne(MediaAccountDO::getRebindStatus, "pending"))
                .set(MediaAccountDO::getRebindProcessInstanceId, "STARTING")
                .set(MediaAccountDO::getRebindTargetStudentPersonId, targetStudentId)
                .set(MediaAccountDO::getRebindRequestedByUserId, requesterUserId)
                .set(MediaAccountDO::getRebindReviewerUserId, reviewerUserId)
                .set(MediaAccountDO::getRebindStatus, "starting")
                .set(MediaAccountDO::getRebindResultReason, null)
                .set(MediaAccountDO::getVersion, version + 1));
    }
    default int finishRebind(Long id, Integer version, String processId) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id)
                .eq(MediaAccountDO::getVersion,version).eq(MediaAccountDO::getRebindProcessInstanceId,"STARTING")
                .set(MediaAccountDO::getRebindProcessInstanceId,processId)
                .set(MediaAccountDO::getRebindStatus,"pending").set(MediaAccountDO::getVersion,version+1));
    }
    default MediaAccountDO selectByRebindProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getRebindProcessInstanceId, processInstanceId));
    }
    default int completeRebind(Long id, Integer version, String processInstanceId, String status,
                               String reason, Long studentPersonId) {
        LambdaUpdateWrapper<MediaAccountDO> update = new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, id).eq(MediaAccountDO::getVersion, version)
                .eq(MediaAccountDO::getRebindProcessInstanceId, processInstanceId)
                .eq(MediaAccountDO::getRebindStatus, "pending")
                .set(MediaAccountDO::getRebindStatus, status)
                .set(MediaAccountDO::getRebindResultReason, reason)
                .set(MediaAccountDO::getVersion, version + 1);
        if (studentPersonId != null) {
            update.set(MediaAccountDO::getStudentPersonId, studentPersonId)
                    .set(MediaAccountDO::getOwnershipType, "student");
        }
        return update(null, update);
    }
}

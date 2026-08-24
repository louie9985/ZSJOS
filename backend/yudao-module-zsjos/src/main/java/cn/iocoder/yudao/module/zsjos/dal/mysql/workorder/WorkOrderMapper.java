package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
@Mapper public interface WorkOrderMapper extends BaseMapperX<WorkOrderDO> {
    default WorkOrderDO selectByIdForUpdate(Long id) { return selectOneForUpdate(WorkOrderDO::getId, id); }
    default WorkOrderDO selectByOrderNo(String orderNo) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getOrderNo, orderNo)); }
    default WorkOrderDO selectByIdempotencyKey(String key) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getIdempotencyKey, key)); }
    default PageResult<WorkOrderDO> selectPool(PageParam page, String sceneCode) { return selectPage(page, new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getSceneCode, sceneCode).eq(WorkOrderDO::getStatus, "POOL").orderByAsc(WorkOrderDO::getCreateTime)); }
    default PageResult<WorkOrderDO> selectMyPage(PageParam page, String status, Long userId) { return selectPage(page, new LambdaQueryWrapperX<WorkOrderDO>().eq(status != null, WorkOrderDO::getStatus, status).and(w -> w.eq(WorkOrderDO::getSourceUserId, userId).or().eq(WorkOrderDO::getTargetUserId, userId)).orderByDesc(WorkOrderDO::getCreateTime)); }
    default int claim(Long id, Long userId, String userName, Integer version) { return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getStatus, "POOL").eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "IN_PROGRESS").set(WorkOrderDO::getTargetUserId, userId).set(WorkOrderDO::getTargetNameSnapshot, userName).set(WorkOrderDO::getClaimedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getVersion, version + 1)); }
    default int transition(Long id, Integer version, String from, String to, String reason) { var update = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getStatus, from).eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, to).set(WorkOrderDO::getVersion, version + 1); if ("COMPLETED_PENDING_ACCEPTANCE".equals(to)) { update.set(WorkOrderDO::getCompletedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getReturnReason, null); } if ("ACCEPTED".equals(to)) update.set(WorkOrderDO::getAcceptedAt, java.time.LocalDateTime.now()); if ("RETURNED".equals(to)) update.set(WorkOrderDO::getReturnReason, reason); return update(null, update); }
}

package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
@Mapper public interface WorkOrderMapper extends BaseMapperX<WorkOrderDO> {
    default LambdaQueryWrapperX<WorkOrderDO> generic() { return new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getBusinessType, "GENERIC"); }
    default WorkOrderDO selectFeedbackByIdForUpdate(Long id) { return selectOneForUpdate(new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "FEEDBACK")); }
    default WorkOrderDO selectFeedbackByIdempotencyKey(String key) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>().eq(WorkOrderDO::getIdempotencyKey, key).eq(WorkOrderDO::getBusinessType, "FEEDBACK")); }
    default WorkOrderDO selectAnyByIdempotencyKey(String key) { return selectOne(WorkOrderDO::getIdempotencyKey, key); }
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(order_no, 14) AS UNSIGNED)), 0)
            FROM zsjos_work_order
            WHERE tenant_id=#{tenantId} AND business_type='FEEDBACK' AND scene_code=#{type}
              AND order_no REGEXP #{numberPattern}
            """)
    long selectMaxFeedbackNumber(@Param("tenantId") Long tenantId, @Param("type") String type,
                                 @Param("numberPattern") String numberPattern);
    default WorkOrderDO selectByIdForUpdate(Long id) { return selectOneForUpdate(generic().eq(WorkOrderDO::getId, id)); }
    default WorkOrderDO selectByOrderNo(String orderNo) { return selectOne(generic().eq(WorkOrderDO::getOrderNo, orderNo)); }
    default WorkOrderDO selectByIdempotencyKey(String key) { return selectOne(generic().eq(WorkOrderDO::getIdempotencyKey, key)); }
    default PageResult<WorkOrderDO> selectPool(PageParam page, String sceneCode) { return selectPage(page, generic().eq(sceneCode != null, WorkOrderDO::getSceneCode, sceneCode).in(WorkOrderDO::getStatus, "POOL", "AVAILABLE").orderByAsc(WorkOrderDO::getCreateTime)); }
    @Select("""
            SELECT wo.* FROM zsjos_work_order wo
            JOIN system_users u ON u.id=#{userId} AND u.deleted=0 AND u.status=0
            WHERE wo.business_type<>'FEEDBACK' AND wo.status IN ('POOL','AVAILABLE')
              AND (#{sceneCode} IS NULL OR wo.scene_code=#{sceneCode})
              AND (wo.target_dept_id IS NULL OR wo.target_dept_id=u.dept_id)
              AND (
                (wo.candidate_qualification_mode='ROLE' AND EXISTS (
                  SELECT 1 FROM JSON_TABLE(wo.candidate_role_scopes_json, '$[*]' COLUMNS(scope_id BIGINT PATH '$.id')) s
                  JOIN system_user_role ur ON ur.role_id=s.scope_id AND ur.user_id=u.id AND ur.deleted=0
                  JOIN system_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=0))
                OR (wo.candidate_qualification_mode='DEPARTMENT' AND EXISTS (
                  SELECT 1 FROM JSON_TABLE(wo.candidate_dept_scopes_json, '$[*]' COLUMNS(scope_id BIGINT PATH '$.id')) s WHERE s.scope_id=u.dept_id))
                OR (wo.candidate_qualification_mode='ROLE_AND_DEPARTMENT' AND EXISTS (
                  SELECT 1 FROM JSON_TABLE(wo.candidate_dept_scopes_json, '$[*]' COLUMNS(scope_id BIGINT PATH '$.id')) d WHERE d.scope_id=u.dept_id)
                  AND EXISTS (SELECT 1 FROM JSON_TABLE(wo.candidate_role_scopes_json, '$[*]' COLUMNS(scope_id BIGINT PATH '$.id')) s
                  JOIN system_user_role ur ON ur.role_id=s.scope_id AND ur.user_id=u.id AND ur.deleted=0
                  JOIN system_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=0))
              ) ORDER BY wo.create_time ASC
            """)
    IPage<WorkOrderDO> selectEligiblePool(IPage<WorkOrderDO> page, @Param("sceneCode") String sceneCode, @Param("userId") Long userId);
    default PageResult<WorkOrderDO> selectMyPage(PageParam page, String status, String view, Long userId) {
        var query = new LambdaQueryWrapperX<WorkOrderDO>().ne(WorkOrderDO::getBusinessType, "FEEDBACK")
                .eq(status != null, WorkOrderDO::getStatus, status);
        switch (view == null ? "ALL" : view) {
            case "PENDING_ACCEPT" -> query.eq(WorkOrderDO::getTargetUserId, userId).eq(WorkOrderDO::getStatus, "PENDING_ACCEPT");
            case "PROCESSING" -> query.eq(WorkOrderDO::getTargetUserId, userId).eq(WorkOrderDO::getStatus, "IN_PROGRESS");
            case "PENDING_REVIEW" -> query.eq(WorkOrderDO::getSourceUserId, userId).eq(WorkOrderDO::getStatus, "PENDING_REVIEW");
            case "CREATED" -> query.eq(WorkOrderDO::getSourceUserId, userId);
            case "CLOSED" -> query.and(w -> w.eq(WorkOrderDO::getSourceUserId, userId).or().eq(WorkOrderDO::getTargetUserId, userId))
                    .in(WorkOrderDO::getStatus, "COMPLETED", "REJECTED_INVALID", "WITHDRAWN", "TERMINATED_UNQUALIFIED");
            default -> query.and(w -> w.eq(WorkOrderDO::getSourceUserId, userId).or().eq(WorkOrderDO::getTargetUserId, userId));
        }
        return selectPage(page, query.orderByDesc(WorkOrderDO::getCreateTime));
    }
    default PageResult<WorkOrderDO> selectAuditPage(PageParam page, String status) { return selectPage(page,
            new LambdaQueryWrapperX<WorkOrderDO>().ne(WorkOrderDO::getBusinessType, "FEEDBACK")
                    .eq(status != null, WorkOrderDO::getStatus, status).orderByDesc(WorkOrderDO::getCreateTime)); }
    default WorkOrderDO selectAuditById(Long id) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>()
            .eq(WorkOrderDO::getId, id).ne(WorkOrderDO::getBusinessType, "FEEDBACK")); }
    default WorkOrderDO selectUnifiedById(Long id) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>()
            .eq(WorkOrderDO::getId, id).ne(WorkOrderDO::getBusinessType, "FEEDBACK")); }
    default WorkOrderDO selectByBusiness(String businessType, Long businessId) { return selectOne(new LambdaQueryWrapperX<WorkOrderDO>()
            .eq(WorkOrderDO::getBusinessType, businessType).eq(WorkOrderDO::getBusinessId, businessId)); }
    default int updateBusinessProjection(String businessType, Long businessId, String status, Long targetUserId, String targetName) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>()
                .eq(WorkOrderDO::getBusinessType, businessType).eq(WorkOrderDO::getBusinessId, businessId)
                .set(WorkOrderDO::getStatus, status).set(WorkOrderDO::getTargetUserId, targetUserId)
                .set(WorkOrderDO::getTargetNameSnapshot, targetName).setSql("version = version + 1"));
    }
    default int incrementProductionRound(Long businessId) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>()
                .eq(WorkOrderDO::getBusinessType, "PRODUCTION_TICKET").eq(WorkOrderDO::getBusinessId, businessId)
                .setSql("current_round = current_round + 1"));
    }
    default int claim(Long id, Long userId, String userName, Integer version) { return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "GENERIC").in(WorkOrderDO::getStatus, "POOL", "AVAILABLE").eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "IN_PROGRESS").set(WorkOrderDO::getTargetUserId, userId).set(WorkOrderDO::getTargetNameSnapshot, userName).set(WorkOrderDO::getClaimedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getVersion, version + 1)); }
    default int transition(Long id, Integer version, String from, String to, String reason) { var update = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "GENERIC").eq(WorkOrderDO::getStatus, from).eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, to).set(WorkOrderDO::getVersion, version + 1); if ("PENDING_REVIEW".equals(to) || "COMPLETED_PENDING_ACCEPTANCE".equals(to)) update.set(WorkOrderDO::getCompletedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getReturnReason, null); if ("COMPLETED".equals(to) || "ACCEPTED".equals(to)) update.set(WorkOrderDO::getAcceptedAt, java.time.LocalDateTime.now()); if ("IN_PROGRESS".equals(to) || "RETURNED".equals(to)) update.set(WorkOrderDO::getReturnReason, reason); return update(null, update); }
    default int take(Long id, Long userId, Integer version) { return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "GENERIC").eq(WorkOrderDO::getTargetUserId, userId).eq(WorkOrderDO::getStatus, "PENDING_ACCEPT").eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "IN_PROGRESS").set(WorkOrderDO::getClaimedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getVersion, version + 1)); }
    default int submitForReview(Long id, Integer version, String result, String attachments) { return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "GENERIC").in(WorkOrderDO::getStatus, "IN_PROGRESS", "RETURNED").eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "PENDING_REVIEW").set(WorkOrderDO::getCompletionRemark, result).set(WorkOrderDO::getCompletionAttachmentIdsJson, attachments).set(WorkOrderDO::getCompletedAt, java.time.LocalDateTime.now()).set(WorkOrderDO::getVersion, version + 1)); }
    default int returnForRework(Long id, Integer version, String reason) { return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id).eq(WorkOrderDO::getBusinessType, "GENERIC").eq(WorkOrderDO::getStatus, "PENDING_REVIEW").eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "IN_PROGRESS").set(WorkOrderDO::getReturnReason, reason).setSql("current_round = current_round + 1").set(WorkOrderDO::getVersion, version + 1)); }
    default int rejectToPool(Long id, Integer version, String mode, String roleScopes, String deptScopes) { return update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkOrderDO>().eq(WorkOrderDO::getId, id)
                    .eq(WorkOrderDO::getBusinessType, "GENERIC").eq(WorkOrderDO::getStatus, "PENDING_ACCEPT")
                    .eq(WorkOrderDO::getVersion, version).set(WorkOrderDO::getStatus, "AVAILABLE")
                    .set(WorkOrderDO::getTargetUserId, null).set(WorkOrderDO::getTargetNameSnapshot, null)
                    .set(WorkOrderDO::getTargetDeptId, null).set(WorkOrderDO::getCandidateQualificationMode, mode)
                    .set(WorkOrderDO::getCandidateRoleScopesJson, roleScopes).set(WorkOrderDO::getCandidateDeptScopesJson, deptScopes)
                    .set(WorkOrderDO::getVersion, version + 1)); }
}

package cn.iocoder.yudao.module.zsjos.dal.mysql.production;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface ProductionTicketMapper extends BaseMapperX<ProductionTicketDO> {
    // JSON_TABLE subqueries in UPDATE functions are not supported by the tenant SQL parser.
    // Lock only affected active rows, then retain tenant and version checks on ordinary updates.
    default int excludeDeletedAccount(Long accountId) {
        var rows = selectList(new LambdaQueryWrapperX<ProductionTicketDO>()
                .select(ProductionTicketDO::getId, ProductionTicketDO::getAccountIdsJson, ProductionTicketDO::getVersion)
                .notIn(ProductionTicketDO::getStatus, "completed", "cancelled")
                .apply("JSON_CONTAINS(account_ids_json, JSON_ARRAY({0}))", accountId).last("FOR UPDATE"));
        int changed = 0;
        for (var row : rows) {
            var values = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(row.getAccountIdsJson(), Object.class);
            var remaining = values.stream().filter(value -> !(value instanceof Number
                    && value.toString().equals(accountId.toString()))).toList();
            var update = new LambdaUpdateWrapper<ProductionTicketDO>().eq(ProductionTicketDO::getId, row.getId())
                    .eq(ProductionTicketDO::getVersion, row.getVersion())
                    .set(ProductionTicketDO::getAccountIdsJson, cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(remaining))
                    .set(ProductionTicketDO::getVersion, row.getVersion() + 1);
            if (remaining.isEmpty()) update.set(ProductionTicketDO::getStatus, "cancelled");
            if (update(null, update) != 1) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCTION_TICKET_VERSION_CONFLICT);
            changed++;
        }
        return changed;
    }
    default List<ProductionTicketDO> selectByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProductionTicketDO>()
                .and(query -> accountIds.forEach(accountId -> query.eq(ProductionTicketDO::getAccountId, accountId)
                        .or().apply("JSON_CONTAINS(account_ids_json, JSON_ARRAY({0}))", accountId)))
                .orderByDesc(ProductionTicketDO::getUpdateTime).orderByDesc(ProductionTicketDO::getId));
    }
    default List<ProductionTicketDO> selectRecentByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProductionTicketDO>().and(query -> accountIds.forEach(accountId -> query.eq(ProductionTicketDO::getAccountId, accountId)
                        .or().apply("JSON_CONTAINS(account_ids_json, JSON_ARRAY({0}))", accountId)))
                .orderByDesc(ProductionTicketDO::getUpdateTime).orderByDesc(ProductionTicketDO::getId)
                .last("LIMIT 100"));
    }
    // Old envelopes own the frozen deadline when the ticket column is absent.
    String EFFECTIVE_DEADLINE = "COALESCE(deadline_at, (SELECT CAST(REPLACE(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(wo.value_json, '$.deadline_at')), 'null'), NULLIF(JSON_UNQUOTE(JSON_EXTRACT(wo.value_json, '$.deadlineAt')), 'null')), 'T', ' ') AS DATETIME) FROM zsjos_work_order wo WHERE wo.business_type = 'PRODUCTION_TICKET' AND wo.business_id = zsjos_production_ticket.id AND wo.tenant_id = zsjos_production_ticket.tenant_id AND wo.deleted = 0 LIMIT 1))";
    default PageResult<ProductionTicketDO> selectPage(ProductionTicketPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<ProductionTicketDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(ProductionTicketDO::getStatus, req.getStatus());
        query.inIfPresent(ProductionTicketDO::getStatus, req.groupedStatuses())
                .apply(req.getDeadlineFrom() != null, EFFECTIVE_DEADLINE + " >= {0}", req.getDeadlineFrom())
                .apply(req.getDeadlineTo() != null, EFFECTIVE_DEADLINE + " <= {0}", req.getDeadlineTo());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(x -> x.like(ProductionTicketDO::getTicketNo, req.getKeyword())
                    .or().like(ProductionTicketDO::getScriptText, req.getKeyword()));
        }
        if (!all) query.and(x -> x.in(ProductionTicketDO::getOwnerOperatorUserId, userIds).or()
                .in(ProductionTicketDO::getAssigneeFilmingEditorUserId, userIds).or()
                .in(ProductionTicketDO::getReviewerUserId, userIds));
        return selectPage(req, query.orderByDesc(ProductionTicketDO::getUpdateTime).orderByDesc(ProductionTicketDO::getId));
    }
    default PageResult<ProductionTicketDO> selectPoolPage(ProductionTicketPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<ProductionTicketDO>()
                .eq(ProductionTicketDO::getStatus, "public_pool")
                .likeIfPresent(ProductionTicketDO::getTicketNo, req.getKeyword())
                .apply(req.getDeadlineFrom() != null, EFFECTIVE_DEADLINE + " >= {0}", req.getDeadlineFrom())
                .apply(req.getDeadlineTo() != null, EFFECTIVE_DEADLINE + " <= {0}", req.getDeadlineTo())
                .orderByAsc(ProductionTicketDO::getCreateTime).orderByAsc(ProductionTicketDO::getId));
    }
    default PageResult<ProductionTicketDO> selectPendingPage(ProductionTicketPageReqVO req, Long userId) {
        return selectPage(req, new LambdaQueryWrapperX<ProductionTicketDO>()
                .eq(ProductionTicketDO::getAssigneeFilmingEditorUserId, userId)
                .eq(ProductionTicketDO::getStatus, "pending_accept")
                .likeIfPresent(ProductionTicketDO::getTicketNo, req.getKeyword())
                .apply(req.getDeadlineFrom() != null, EFFECTIVE_DEADLINE + " >= {0}", req.getDeadlineFrom())
                .apply(req.getDeadlineTo() != null, EFFECTIVE_DEADLINE + " <= {0}", req.getDeadlineTo())
                .orderByAsc(ProductionTicketDO::getCreateTime).orderByAsc(ProductionTicketDO::getId));
    }
    default List<ProductionTicketDO> selectPendingByAssignee(Long userId) {
        return selectList(new LambdaQueryWrapperX<ProductionTicketDO>()
                .eq(ProductionTicketDO::getAssigneeFilmingEditorUserId, userId)
                .eq(ProductionTicketDO::getStatus, "pending_accept")
                .orderByAsc(ProductionTicketDO::getCreateTime).orderByAsc(ProductionTicketDO::getId));
    }
    default ProductionTicketDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<ProductionTicketDO>()
                .eq(ProductionTicketDO::getIdempotencyKey, key));
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>().eq(ProductionTicketDO::getId, id)
                .eq(ProductionTicketDO::getVersion, version).eq(ProductionTicketDO::getStatus, from)
                .set(ProductionTicketDO::getStatus, to).set(ProductionTicketDO::getVersion, version + 1));
    }
    default int rejectForRevision(Long id, Integer version, String expectedStatus, String reason) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>().eq(ProductionTicketDO::getId,id)
                .eq(ProductionTicketDO::getVersion,version).eq(ProductionTicketDO::getStatus, expectedStatus)
                .set(ProductionTicketDO::getStatus,"rejected").set(ProductionTicketDO::getReworkReasonType,reason)
                .setSql("revision_count = revision_count + 1").set(ProductionTicketDO::getVersion,version+1));
    }
    default int rejectAssignment(Long id, Integer version, String targetStatus) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>()
                .eq(ProductionTicketDO::getId, id).eq(ProductionTicketDO::getVersion, version)
                .eq(ProductionTicketDO::getStatus, "pending_accept")
                .set(ProductionTicketDO::getStatus, targetStatus)
                .set(ProductionTicketDO::getAssigneeFilmingEditorUserId, null)
                .set(ProductionTicketDO::getVersion, version + 1));
    }
    default int claim(Long id, Integer version, Long userId) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>()
                .eq(ProductionTicketDO::getId, id).eq(ProductionTicketDO::getVersion, version)
                .eq(ProductionTicketDO::getStatus, "public_pool")
                .isNull(ProductionTicketDO::getAssigneeFilmingEditorUserId)
                .set(ProductionTicketDO::getStatus, "accepted")
                .set(ProductionTicketDO::getAssigneeFilmingEditorUserId, userId)
                .set(ProductionTicketDO::getVersion, version + 1));
    }
}

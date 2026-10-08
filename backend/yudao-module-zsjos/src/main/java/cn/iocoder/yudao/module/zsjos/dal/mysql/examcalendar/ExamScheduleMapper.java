package cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamScheduleMapper extends BaseMapperX<ExamScheduleDO> {

    // MyBatis-Plus default methods propagate their interceptor strategy to nested mapper calls.
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    default PageResult<ExamScheduleDO> selectSearch(
            cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamCalendarSearchReqVO req,
            boolean manager, LocalDateTime now) {
        var query = base(new ExamSchedulePageReqVO(), manager, now);
        query.eqIfPresent(ExamScheduleDO::getRecordStatus, req.getRecordStatus());
        cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.keyword(query, req.getKeyword(), SEARCH_NAME_SQL, "remark");
        String start = "CASE WHEN schedule_type = 'EXACT' THEN exact_date ELSE start_date END";
        String end = "CASE WHEN schedule_type = 'EXACT' THEN exact_date ELSE end_date END";
        cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.dates(query, req, start, end, false);
        // JSON_TABLE in the historical title cannot be parsed by the framework SQL parser.
        // These two search statements use explicit tenant/deletion predicates instead; the same
        // base() visibility rule still controls drafts and the revocation window before counting.
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        long total = countSearchRows(query, tenantId);
        if (total == 0) return new PageResult<>(List.of(), 0L);
        query.last(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.order(req, start, end, "id", now.toLocalDate()));
        return new PageResult<>(selectSearchRows(query, tenantId, req.getPageSize(), ((long) req.getPageNo() - 1) * req.getPageSize()), total);
    }

    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("SELECT /*+ SET_VAR(group_concat_max_len=1048576) */ COUNT(*) FROM zsjos_exam_schedule WHERE tenant_id=#{tenantId} AND deleted=0 AND ${ew.sqlSegment}")
    long countSearchRows(@Param("ew") LambdaQueryWrapperX<ExamScheduleDO> query, @Param("tenantId") Long tenantId);

    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("SELECT /*+ SET_VAR(group_concat_max_len=1048576) */ * FROM zsjos_exam_schedule WHERE tenant_id=#{tenantId} AND deleted=0 AND ${ew.sqlSegment} LIMIT #{limit} OFFSET #{offset}")
    List<ExamScheduleDO> selectSearchRows(@Param("ew") LambdaQueryWrapperX<ExamScheduleDO> query,
                                        @Param("tenantId") Long tenantId, @Param("limit") int limit, @Param("offset") long offset);

    // Mirrors the legacy displayName snapshot, without consulting mutable product/dictionary labels.
    String SEARCH_NAME_SQL = """
        COALESCE(schedule_name, CONCAT(
          COALESCE(CASE WHEN product_id IS NULL THEN category_name_snapshot ELSE product_name_snapshot END, '未命名考期'),
          COALESCE((SELECT GROUP_CONCAT(CONCAT('，', COALESCE(spec.attr_name, 'null'), '：',
            COALESCE(spec.label, 'null'), IF(spec.label_missing, '（历史标签缺失）', '')) ORDER BY spec.ord SEPARATOR '')
            FROM JSON_TABLE(COALESCE(selected_specs_json, JSON_ARRAY()), '$[*]' COLUMNS(
              ord FOR ORDINALITY, attr_name TEXT PATH '$.attrName', label TEXT PATH '$.label',
              label_missing BOOLEAN PATH '$.labelMissing')) spec), '')))
        """;

    @Select("SELECT COUNT(*) FROM zsjos_exam_schedule WHERE tenant_id = #{tenantId} "
            + "AND deleted = 1 AND record_status = 'REVOKED' AND reedit_claimed_at IS NOT NULL "
            + "AND revoked_by = #{userId} AND JSON_CONTAINS(attachment_ids_json, CAST(#{fileId} AS JSON))")
    long countClaimedAttachment(@Param("fileId") Long fileId, @Param("tenantId") Long tenantId,
                               @Param("userId") Long userId);

    // Only reedit may read a claimed tombstone; both queries explicitly preserve tenant isolation.
    @Select("SELECT * FROM zsjos_exam_schedule WHERE id = #{id} AND tenant_id = #{tenantId}")
    ExamScheduleDO selectReeditRecord(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_exam_schedule WHERE id = #{id} AND tenant_id = #{tenantId} FOR UPDATE")
    ExamScheduleDO selectReeditRecordForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default ExamScheduleDO selectForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamScheduleDO>().eq(ExamScheduleDO::getId, id).last("FOR UPDATE"));
    }
    default List<ExamScheduleDO> selectExactList(ExamSchedulePageReqVO req, boolean includeUnpublished, LocalDateTime now) {
        return selectList(base(req, includeUnpublished, now)
                .eq(ExamScheduleDO::getScheduleType, "EXACT")
                .geIfPresent(ExamScheduleDO::getExactDate, req.getRangeStart())
                .leIfPresent(ExamScheduleDO::getExactDate, req.getRangeEnd())
                .orderByAsc(ExamScheduleDO::getExactDate)
                .orderByAsc(ExamScheduleDO::getId));
    }

    default PageResult<ExamScheduleDO> selectMultiDayPage(ExamSchedulePageReqVO req, boolean includeUnpublished, LocalDateTime now) {
        var query = base(req, includeUnpublished, now)
                .eq(ExamScheduleDO::getScheduleType, "MULTI_DAY")
                .leIfPresent(ExamScheduleDO::getStartDate, req.getRangeEnd())
                .geIfPresent(ExamScheduleDO::getEndDate, req.getRangeStart())
                .orderByAsc(ExamScheduleDO::getStartDate)
                .orderByAsc(ExamScheduleDO::getId);
        return selectPage(req, query);
    }

    private static LambdaQueryWrapperX<ExamScheduleDO> base(ExamSchedulePageReqVO req, boolean includeUnpublished, LocalDateTime now) {
        var query = new LambdaQueryWrapperX<ExamScheduleDO>()
                .eqIfPresent(ExamScheduleDO::getCategoryId, req.getCategoryId())
                .eq(!includeUnpublished, ExamScheduleDO::getRecordStatus, "PUBLISHED");
        query.and(includeUnpublished, q -> q.in(ExamScheduleDO::getRecordStatus, "DRAFT", "PUBLISHED")
                        .or(revoked -> revoked.eq(ExamScheduleDO::getRecordStatus, "REVOKED")
                                .gt(ExamScheduleDO::getRevokedAt, now.minusMinutes(5))
                                .isNull(ExamScheduleDO::getReeditClaimedAt)));
        return query;
    }
}

package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.Map;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO;

@Mapper
public interface DeliveryClassMapper extends BaseMapperX<DeliveryClassDO> {
    // Apply before pagination: exception archives must not displace ordinary teaching classes.
    String DEFAULT_ORDER = "ORDER BY CASE class_kind WHEN 'PENDING' THEN 0 WHEN 'UNSCHEDULED' THEN 1 WHEN 'EXCEPTION' THEN 3 ELSE 2 END ASC, "
            + "system_class DESC, create_time DESC, id DESC";
    default DeliveryClassDO selectPending() {
        return selectOne(new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getClassKind, "PENDING"));
    }
    @Select("SELECT * FROM zsjos_delivery_class WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    DeliveryClassDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_delivery_class WHERE class_kind=#{kind} AND homeroom_user_id=#{owner} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    DeliveryClassDO selectPlannerDefaultForUpdate(@Param("kind") String kind, @Param("owner") Long owner,
                                                @Param("tenantId") Long tenantId);

    default PageResult<DeliveryClassDO> selectDeliveryClassPage(DeliveryClassPageReqVO req, Set<Long> deptIds,
                                                    Long homeroomUserId, boolean allDepartments,
                                                    boolean includeSystem) {
        return selectPage(req, classQuery(req, deptIds, homeroomUserId, allDepartments, includeSystem));
    }

    default LambdaQueryWrapperX<DeliveryClassDO> classQuery(DeliveryClassPageReqVO req, Set<Long> deptIds,
            Long homeroomUserId, boolean allDepartments, boolean includeSystem) {
        LambdaQueryWrapperX<DeliveryClassDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(DeliveryClassDO::getStatus, req.getStatus())
                .eqIfPresent(DeliveryClassDO::getCategoryId, req.getCategoryId())
                .eqIfPresent(DeliveryClassDO::getExamScheduleId, req.getExamScheduleId())
                .eqIfPresent(DeliveryClassDO::getHomeroomUserId, req.getHomeroomUserId());
        if (req.getProjectCategoryId() != null) query.apply(DeliveryClassFilterSql.PROJECT_EXISTS, req.getProjectCategoryId());
        if (req.getExamDateStart() != null) query.apply(DeliveryClassFilterSql.DATE_END + " >= {0}", req.getExamDateStart().toString());
        if (req.getExamDateEnd() != null) query.apply(DeliveryClassFilterSql.DATE_START + " <= {0}", req.getExamDateEnd().toString());
        if (Boolean.TRUE.equals(req.getAssignableOnly())) query.in(DeliveryClassDO::getClassKind, List.of("NORMAL", "UNSCHEDULED"));
        if (Boolean.TRUE.equals(req.getExcludeSystemClass())) query.eq(DeliveryClassDO::getSystemClass, false);
        query.and(req.getKeyword() != null && !req.getKeyword().isBlank(), q -> q
                .like(DeliveryClassDO::getClassName, req.getKeyword().trim())
                .or().like(DeliveryClassDO::getClassNo, req.getKeyword().trim()));
        if (!allDepartments) {
            query.and(scope -> {
                // Identity and configured departments are independent grants; neither implies the other.
                scope.apply("1 = 0");
                if (homeroomUserId != null) scope.or().eq(DeliveryClassDO::getHomeroomUserId, homeroomUserId);
                if (deptIds != null && !deptIds.isEmpty()) scope.or().in(DeliveryClassDO::getDeptId, deptIds);
                if (includeSystem) scope.or().eq(DeliveryClassDO::getClassKind, "PENDING");
            });
        }
        if (!includeSystem) query.ne(DeliveryClassDO::getClassKind, "PENDING");
        return query.last(DEFAULT_ORDER);
    }

    @Select({"<script>", "SELECT DISTINCT sr.class_id AS classId, oi.product_snapshot AS snapshot",
            "FROM zsjos_service_relation sr JOIN zsjos_order_item oi ON oi.id=sr.order_item_id",
            "AND oi.tenant_id=sr.tenant_id AND oi.deleted=0",
            "WHERE sr.teaching_delivery=1 AND sr.tenant_id=#{tenantId} AND sr.deleted=0 AND sr.status IN ('active','paused','completed')",
            "AND sr.class_id IN", "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "ORDER BY sr.class_id, oi.product_snapshot", "</script>"})
    List<Map<String, Object>> selectProjectSnapshots(@Param("tenantId") Long tenantId,
            @Param("classIds") Collection<Long> classIds);
    @Select("SELECT COUNT(1) FROM zsjos_service_relation WHERE teaching_delivery=1 AND tenant_id=#{tenantId} AND class_id=#{classId} AND deleted=b'0' AND status IN ('active','paused','completed')")
    int countStudents(@Param("tenantId") Long tenantId, @Param("classId") Long classId);
    @Select("SELECT COUNT(1) FROM zsjos_service_relation WHERE tenant_id=#{tenantId} AND class_id=#{classId} AND deleted=b'0'")
    int countAllRelations(@Param("tenantId") Long tenantId, @Param("classId") Long classId);

    @Select({"<script>", "SELECT class_id AS classId, COUNT(1) AS studentCount FROM zsjos_service_relation",
            "WHERE teaching_delivery=1 AND tenant_id=#{tenantId} AND deleted=b'0' AND status IN ('active','paused','completed')",
            "AND class_id IN", "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY class_id", "</script>"})
    List<Map<String, Object>> countStudentsByClassIds(@Param("tenantId") Long tenantId,
                                                       @Param("classIds") Collection<Long> classIds);
    default List<DeliveryClassDO> selectVisible(Long homeroomUserId, String status, boolean includeSystem) {
        LambdaQueryWrapperX<DeliveryClassDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(DeliveryClassDO::getStatus, status);
        if (!includeSystem) query.eq(DeliveryClassDO::getHomeroomUserId, homeroomUserId);
        return selectList(query.last(DEFAULT_ORDER));
    }
}

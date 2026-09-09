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
    default DeliveryClassDO selectPending() {
        return selectOne(new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getSystemClass, true));
    }
    @Select("SELECT * FROM zsjos_delivery_class WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    DeliveryClassDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<DeliveryClassDO> selectPage(DeliveryClassPageReqVO req, Set<Long> deptIds,
                                                    Long homeroomUserId, boolean allDepartments,
                                                    boolean includeSystem) {
        LambdaQueryWrapperX<DeliveryClassDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(DeliveryClassDO::getStatus, req.getStatus())
                .eqIfPresent(DeliveryClassDO::getCategoryId, req.getCategoryId())
                .eqIfPresent(DeliveryClassDO::getExamScheduleId, req.getExamScheduleId())
                .eqIfPresent(DeliveryClassDO::getHomeroomUserId, req.getHomeroomUserId());
        query.and(req.getKeyword() != null && !req.getKeyword().isBlank(), q -> q
                .like(DeliveryClassDO::getClassName, req.getKeyword().trim())
                .or().like(DeliveryClassDO::getClassNo, req.getKeyword().trim()));
        if (homeroomUserId != null) query.eq(DeliveryClassDO::getHomeroomUserId, homeroomUserId);
        if (!allDepartments && homeroomUserId == null) {
            if (deptIds == null || deptIds.isEmpty()) {
                if (includeSystem) query.eq(DeliveryClassDO::getSystemClass, true);
                else query.apply("1 = 0");
            } else if (includeSystem) {
                query.and(scope -> scope.eq(DeliveryClassDO::getSystemClass, true)
                        .or().in(DeliveryClassDO::getDeptId, deptIds));
            } else {
                query.in(DeliveryClassDO::getDeptId, deptIds);
            }
        }
        if (!includeSystem) query.eq(DeliveryClassDO::getSystemClass, false);
        return selectPage(req, query.orderByAsc(DeliveryClassDO::getSystemClass)
                .orderByDesc(DeliveryClassDO::getCreateTime).orderByDesc(DeliveryClassDO::getId));
    }
    @Select("SELECT COUNT(1) FROM zsjos_service_relation WHERE tenant_id=#{tenantId} AND class_id=#{classId} AND deleted=b'0' AND status IN ('active','paused','completed')")
    int countStudents(@Param("tenantId") Long tenantId, @Param("classId") Long classId);
    @Select("SELECT COUNT(1) FROM zsjos_service_relation WHERE tenant_id=#{tenantId} AND class_id=#{classId} AND deleted=b'0'")
    int countAllRelations(@Param("tenantId") Long tenantId, @Param("classId") Long classId);

    @Select({"<script>", "SELECT class_id AS classId, COUNT(1) AS studentCount FROM zsjos_service_relation",
            "WHERE tenant_id=#{tenantId} AND deleted=b'0' AND status IN ('active','paused','completed')",
            "AND class_id IN", "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY class_id", "</script>"})
    List<Map<String, Object>> countStudentsByClassIds(@Param("tenantId") Long tenantId,
                                                       @Param("classIds") Collection<Long> classIds);
    default List<DeliveryClassDO> selectVisible(Long homeroomUserId, String status, boolean includeSystem) {
        LambdaQueryWrapperX<DeliveryClassDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(DeliveryClassDO::getStatus, status);
        if (!includeSystem) query.eq(DeliveryClassDO::getHomeroomUserId, homeroomUserId);
        return selectList(query.orderByAsc(DeliveryClassDO::getSystemClass)
                .orderByDesc(DeliveryClassDO::getCreateTime).orderByDesc(DeliveryClassDO::getId));
    }
}

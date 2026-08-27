package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PartnerOwnershipMapper extends BaseMapperX<PartnerOwnershipDO> {
    default PartnerOwnershipDO selectByPartnerId(Long partnerId) {
        return selectOne(PartnerOwnershipDO::getPartnerId, partnerId);
    }

    default List<PartnerOwnershipDO> selectByEmployeeUserId(Long employeeUserId) {
        return selectList(new LambdaQueryWrapperX<PartnerOwnershipDO>()
                .eq(PartnerOwnershipDO::getEmployeeUserId, employeeUserId)
                .orderByDesc(PartnerOwnershipDO::getAssignedAt));
    }

    @Select({"<script>",
            "SELECT partner.id,partner.partner_no,partner.name,partner.mobile,partner.status,",
            "partner.bound_system_user_id,partner.channel_id,partner.enabled_at,partner.disabled_at,",
            "ownership.employee_user_id AS assigned_employee_user_id,",
            "ownership.employee_name_snapshot AS assigned_employee_name,ownership.assigned_at,",
            "ownership.version AS assignment_version ",
            "FROM zsjos_partner_ownership ownership ",
            "JOIN zsjos_partner partner ON partner.id=ownership.partner_id ",
            "AND partner.tenant_id=ownership.tenant_id AND partner.deleted=b'0' ",
            "WHERE ownership.tenant_id=#{tenantId} AND ownership.employee_user_id=#{employeeUserId} ",
            "AND ownership.deleted=b'0' ",
            "<if test='status != null and status != &quot;&quot;'>AND partner.status=#{status} </if>",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (partner.name LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.partner_no LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.mobile LIKE CONCAT('%',#{keyword},'%')) </if>",
            "ORDER BY ownership.assigned_at DESC,ownership.partner_id DESC ",
            "LIMIT #{offset},#{pageSize}",
            "</script>"})
    List<SubordinatePartnerRow> selectSubordinatePage(@Param("tenantId") Long tenantId,
                                                       @Param("employeeUserId") Long employeeUserId,
                                                       @Param("status") String status,
                                                       @Param("keyword") String keyword,
                                                       @Param("offset") long offset,
                                                       @Param("pageSize") int pageSize);

    @Select({"<script>",
            "SELECT COUNT(1) FROM zsjos_partner_ownership ownership ",
            "JOIN zsjos_partner partner ON partner.id=ownership.partner_id ",
            "AND partner.tenant_id=ownership.tenant_id AND partner.deleted=b'0' ",
            "WHERE ownership.tenant_id=#{tenantId} AND ownership.employee_user_id=#{employeeUserId} ",
            "AND ownership.deleted=b'0' ",
            "<if test='status != null and status != &quot;&quot;'>AND partner.status=#{status} </if>",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (partner.name LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.partner_no LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.mobile LIKE CONCAT('%',#{keyword},'%')) </if>",
            "</script>"})
    long selectSubordinateCount(@Param("tenantId") Long tenantId,
                                @Param("employeeUserId") Long employeeUserId,
                                @Param("status") String status,
                                @Param("keyword") String keyword);

    @Select({"<script>",
            "SELECT partner.id,partner.partner_no,partner.name,partner.mobile,partner.status,",
            "partner.bound_system_user_id,partner.channel_id,partner.enabled_at,partner.disabled_at,",
            "ownership.employee_user_id AS assigned_employee_user_id,",
            "ownership.employee_name_snapshot AS assigned_employee_name,ownership.assigned_at,",
            "ownership.version AS assignment_version ",
            "FROM zsjos_partner partner LEFT JOIN zsjos_partner_ownership ownership ",
            "ON ownership.partner_id=partner.id AND ownership.tenant_id=partner.tenant_id AND ownership.deleted=b'0' ",
            "WHERE partner.tenant_id=#{tenantId} AND partner.deleted=b'0' ",
            "<if test='status != null and status != &quot;&quot;'>AND partner.status=#{status} </if>",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (partner.name LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.partner_no LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.mobile LIKE CONCAT('%',#{keyword},'%')) </if>",
            "ORDER BY partner.id DESC LIMIT #{offset},#{pageSize}",
            "</script>"})
    List<SubordinatePartnerRow> selectManagedPage(@Param("tenantId") Long tenantId,
                                                   @Param("status") String status,
                                                   @Param("keyword") String keyword,
                                                   @Param("offset") long offset,
                                                   @Param("pageSize") int pageSize);

    @Select({"<script>",
            "SELECT COUNT(1) FROM zsjos_partner partner ",
            "WHERE partner.tenant_id=#{tenantId} AND partner.deleted=b'0' ",
            "<if test='status != null and status != &quot;&quot;'>AND partner.status=#{status} </if>",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (partner.name LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.partner_no LIKE CONCAT('%',#{keyword},'%') ",
            "OR partner.mobile LIKE CONCAT('%',#{keyword},'%')) </if>",
            "</script>"})
    long selectManagedCount(@Param("tenantId") Long tenantId,
                            @Param("status") String status,
                            @Param("keyword") String keyword);

    @Delete("DELETE FROM zsjos_partner_ownership WHERE id=#{id} AND version=#{version} AND tenant_id=#{tenantId}")
    int deleteByIdAndVersion(@Param("id") Long id, @Param("version") Integer version,
                             @Param("tenantId") Long tenantId);
}

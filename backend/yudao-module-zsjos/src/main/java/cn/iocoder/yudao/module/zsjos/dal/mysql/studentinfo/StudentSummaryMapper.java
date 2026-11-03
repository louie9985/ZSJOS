package cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StudentSummaryMapper {
    @lombok.Data class Binding { private Long serviceId, formId; }
    // Same form identity as StudentInfoFormMapper.byCase, resolved once for a bounded page.
    @Select("""
        <script>
        SELECT sr.id service_id, (SELECT MAX(f.id) FROM zsjos_student_info_form f
          WHERE f.deleted=0 AND f.tenant_id=sr.tenant_id AND
          (f.registration_case_id=sr.registration_case_id OR EXISTS
            (SELECT 1 FROM zsjos_student_info_task t JOIN zsjos_registration_case c
             ON c.order_item_id=t.order_item_id AND c.tenant_id=t.tenant_id AND c.deleted=0
             WHERE c.id=sr.registration_case_id AND t.form_id=f.id AND t.tenant_id=f.tenant_id
               AND t.person_id=f.person_id AND t.deleted=0))) form_id
        FROM zsjos_service_relation sr WHERE sr.tenant_id=#{tenantId} AND sr.deleted=0 AND sr.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Binding> bindings(@Param("ids") List<Long> ids,@Param("tenantId") Long tenantId);
}

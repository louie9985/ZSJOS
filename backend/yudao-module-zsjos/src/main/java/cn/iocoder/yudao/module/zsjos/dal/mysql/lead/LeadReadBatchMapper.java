package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.Set;

/** Batch counterparts of the existing student-history and media-participant read relationships. */
@Mapper
public interface LeadReadBatchMapper {
    @Select("<script>SELECT DISTINCT o.lead_id FROM zsjos_service_relation sr "
            + "JOIN zsjos_order o ON o.id=sr.order_id AND o.tenant_id=sr.tenant_id AND o.deleted=0 "
            + "WHERE sr.tenant_id=#{tenantId} AND sr.deleted=0 AND sr.status='active' "
            + "AND o.lead_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND (sr.owner_user_id=#{userId} OR sr.content_director_user_id=#{userId} OR sr.career_planner_user_id=#{userId} "
            + "OR (sr.acceptance_status='accepted' AND EXISTS (SELECT 1 FROM zsjos_media_account ma "
            + "WHERE ma.student_person_id=sr.person_id AND ma.tenant_id=sr.tenant_id AND ma.deleted=0 "
            + "AND (ma.director_user_id=#{userId} OR ma.owner_operator_user_id=#{userId}))))</script>")
    Set<Long> selectStudentReadableLeadIds(@Param("ids") Collection<Long> ids,
                                         @Param("userId") Long userId, @Param("tenantId") Long tenantId);
}

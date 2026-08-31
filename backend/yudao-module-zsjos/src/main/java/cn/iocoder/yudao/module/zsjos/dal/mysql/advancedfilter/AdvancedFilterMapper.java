package cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter;

import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdvancedFilterMapper {
    @SelectProvider(type = SqlProvider.class, method = "leadSql")
    List<Long> selectLeadIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "orderSql")
    List<Long> selectOrderIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "appealSql")
    List<Long> selectAppealIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "duplicateReviewSql")
    List<Long> selectDuplicateReviewIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "registrationSql")
    List<Long> selectRegistrationCaseIds(@Param("query") AdvancedFilterQuery query);

    @SelectProvider(type = SqlProvider.class, method = "studentSql")
    List<Long> selectStudentPersonIds(@Param("query") AdvancedFilterQuery query);

    @Select("SELECT a.id FROM zsjos_lead_appeal a JOIN zsjos_lead l ON l.id=a.lead_id AND l.deleted=b'0' "
            + "WHERE a.deleted=b'0' AND a.tenant_id=#{tenantId} AND (l.lead_no LIKE CONCAT('%',#{keyword},'%') "
            + "OR l.submitted_name LIKE CONCAT('%',#{keyword},'%') OR l.submitted_mobile LIKE CONCAT('%',#{keyword},'%') "
            + "OR l.submitted_wechat_id LIKE CONCAT('%',#{keyword},'%'))")
    List<Long> selectAppealIdsByKeyword(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);

    @Select("SELECT dr.id FROM zsjos_lead_duplicate_review dr WHERE dr.deleted=b'0' AND dr.tenant_id=#{tenantId} "
            + "AND (JSON_UNQUOTE(JSON_EXTRACT(dr.submission_snapshot,'$.name')) LIKE CONCAT('%',#{keyword},'%') "
            + "OR JSON_UNQUOTE(JSON_EXTRACT(dr.submission_snapshot,'$.mobile')) LIKE CONCAT('%',#{keyword},'%') "
            + "OR JSON_UNQUOTE(JSON_EXTRACT(dr.submission_snapshot,'$.wechatId')) LIKE CONCAT('%',#{keyword},'%'))")
    List<Long> selectDuplicateReviewIdsByKeyword(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);

    final class SqlProvider {
        public static String leadSql(Map<String, Object> ignored) {
            return "SELECT l.id FROM zsjos_lead l WHERE l.deleted=b'0' AND l.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String orderSql(Map<String, Object> ignored) {
            return "SELECT o.id FROM zsjos_order o WHERE o.deleted=b'0' AND o.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String appealSql(Map<String, Object> ignored) {
            return "SELECT a.id FROM zsjos_lead_appeal a WHERE a.deleted=b'0' AND a.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String duplicateReviewSql(Map<String, Object> ignored) {
            return "SELECT dr.id FROM zsjos_lead_duplicate_review dr WHERE dr.deleted=b'0' AND dr.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String registrationSql(Map<String, Object> ignored) {
            return "SELECT rc.id FROM zsjos_registration_case rc WHERE rc.deleted=b'0' AND rc.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
        }
        public static String studentSql(Map<String, Object> ignored) {
            return "SELECT p.id FROM zsjos_person p WHERE p.deleted=b'0' AND p.tenant_id=#{query.parameters.tenantId} "
                    + "AND EXISTS (SELECT 1 FROM zsjos_service_relation vsr LEFT JOIN zsjos_registration_case_route vcr "
                    + "ON vcr.registration_case_id=vsr.registration_case_id AND vcr.assignee_user_id=#{query.parameters.userId} "
                    + "AND vcr.selected=b'1' AND vcr.deleted=b'0' AND vcr.tenant_id=vsr.tenant_id "
                    + "WHERE vsr.person_id=p.id AND vsr.status='active' AND vsr.deleted=b'0' "
                    + "AND vsr.tenant_id=p.tenant_id AND (vsr.owner_user_id=#{query.parameters.userId} OR vcr.id IS NOT NULL)) "
                    + "AND (${query.whereSql})";
        }
    }
}

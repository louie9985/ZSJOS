package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ClassExamProgressMapper {
    record Progress(String stage, Integer version) {}
    record Member(String stage, String outcome, String outcomeLabel) {}
    @Select("SELECT stage,version FROM zsjos_class_exam_progress WHERE class_id=#{id} AND tenant_id=#{tenant}")
    Progress get(@Param("id") Long id, @Param("tenant") Long tenant);
    @Insert("INSERT INTO zsjos_class_exam_progress(class_id,tenant_id,stage,version) VALUES(#{id},#{tenant},#{stage},1) ON DUPLICATE KEY UPDATE stage=#{stage},version=version+1,update_time=CURRENT_TIMESTAMP")
    void save(@Param("id") Long id, @Param("tenant") Long tenant, @Param("stage") String stage);
    @Select("SELECT stage,outcome,outcome_label AS outcomeLabel FROM zsjos_student_exam_progress WHERE service_relation_id=#{id} AND tenant_id=#{tenant}")
    Member member(@Param("id") Long id, @Param("tenant") Long tenant);
    @Insert("INSERT INTO zsjos_student_exam_progress(service_relation_id,tenant_id,stage) VALUES(#{id},#{tenant},#{stage}) ON DUPLICATE KEY UPDATE stage=#{stage},update_time=CURRENT_TIMESTAMP")
    void saveMember(@Param("id") Long id, @Param("tenant") Long tenant, @Param("stage") String stage);
    @Update("UPDATE zsjos_student_exam_progress SET outcome=#{value},outcome_label=#{label},update_time=CURRENT_TIMESTAMP WHERE service_relation_id=#{id} AND tenant_id=#{tenant}")
    int outcome(@Param("id") Long id, @Param("tenant") Long tenant, @Param("value") String value, @Param("label") String label);
}

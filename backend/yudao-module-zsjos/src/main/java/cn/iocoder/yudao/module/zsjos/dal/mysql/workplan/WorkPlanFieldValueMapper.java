package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanFieldValueDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkPlanFieldValueMapper extends BaseMapperX<WorkPlanFieldValueDO> {
    default List<WorkPlanFieldValueDO> selectListBySubject(String subjectType, Long subjectId) {
        return selectList(new LambdaQueryWrapperX<WorkPlanFieldValueDO>()
                .eq(WorkPlanFieldValueDO::getSubjectType, subjectType)
                .eq(WorkPlanFieldValueDO::getSubjectId, subjectId));
    }

    default List<WorkPlanFieldValueDO> selectListBySubjects(String subjectType, List<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<WorkPlanFieldValueDO>()
                .eq(WorkPlanFieldValueDO::getSubjectType, subjectType)
                .in(WorkPlanFieldValueDO::getSubjectId, subjectIds));
    }

    @Delete("DELETE FROM zsjos_work_field_value WHERE subject_type = #{subjectType} AND subject_id = #{subjectId}")
    void deleteBySubject(String subjectType, Long subjectId);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_text LIKE CONCAT('%',#{value},'%')")
    List<Long> selectPlanIdsTextContains(String fieldKey, String value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_decimal = #{value}")
    List<Long> selectPlanIdsDecimalEquals(String fieldKey, BigDecimal value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_decimal >= #{value}")
    List<Long> selectPlanIdsDecimalGte(String fieldKey, BigDecimal value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_decimal <= #{value}")
    List<Long> selectPlanIdsDecimalLte(String fieldKey, BigDecimal value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_datetime >= #{value}")
    List<Long> selectPlanIdsDatetimeGte(String fieldKey, LocalDateTime value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND v.value_datetime <= #{value}")
    List<Long> selectPlanIdsDatetimeLte(String fieldKey, LocalDateTime value);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND (v.value_text=#{value} OR v.value_ref_id=#{refId})")
    List<Long> selectPlanIdsExact(String fieldKey, String value, Long refId);

    @Select("SELECT DISTINCT d.plan_id FROM zsjos_work_plan_field_definition d " +
            "JOIN zsjos_work_field_value v ON v.field_definition_id=d.id AND v.deleted=0 " +
            "WHERE d.deleted=0 AND d.section='plan' AND d.origin='template' AND d.field_key=#{fieldKey} " +
            "AND v.subject_type='plan' AND JSON_CONTAINS(v.value_json, JSON_QUOTE(#{value}))")
    List<Long> selectPlanIdsJsonContains(String fieldKey, String value);
}

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

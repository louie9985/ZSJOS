package cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ExamScheduleMapper extends BaseMapperX<ExamScheduleDO> {
    default ExamScheduleDO selectForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ExamScheduleDO>().eq(ExamScheduleDO::getId, id).last("FOR UPDATE"));
    }
    default List<ExamScheduleDO> selectExactList(ExamSchedulePageReqVO req, boolean includeUnpublished) {
        return selectList(base(req, includeUnpublished)
                .eq(ExamScheduleDO::getScheduleType, "EXACT")
                .geIfPresent(ExamScheduleDO::getExactDate, req.getRangeStart())
                .leIfPresent(ExamScheduleDO::getExactDate, req.getRangeEnd())
                .orderByAsc(ExamScheduleDO::getExactDate)
                .orderByAsc(ExamScheduleDO::getId));
    }

    default PageResult<ExamScheduleDO> selectRoughPage(ExamSchedulePageReqVO req, boolean includeUnpublished) {
        var query = base(req, includeUnpublished)
                .eq(ExamScheduleDO::getScheduleType, "ROUGH")
                .leIfPresent(ExamScheduleDO::getRoughStartDate, req.getRangeEnd())
                .geIfPresent(ExamScheduleDO::getRoughEndDate, req.getRangeStart())
                .orderByAsc(ExamScheduleDO::getRoughStartDate)
                .orderByAsc(ExamScheduleDO::getId);
        return selectPage(req, query);
    }

    private static LambdaQueryWrapperX<ExamScheduleDO> base(ExamSchedulePageReqVO req, boolean includeUnpublished) {
        return new LambdaQueryWrapperX<ExamScheduleDO>()
                .eqIfPresent(ExamScheduleDO::getCategoryId, req.getCategoryId())
                .eq(!includeUnpublished, ExamScheduleDO::getRecordStatus, "PUBLISHED");
    }
}

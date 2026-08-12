package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkReportDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkReportMapper extends BaseMapperX<WorkReportDO> {
    default List<WorkReportDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<WorkReportDO>().eq(WorkReportDO::getTaskId, taskId)
                .orderByAsc(WorkReportDO::getRevisionNo));
    }
    default WorkReportDO selectLatest(Long taskId) {
        return selectOne(new LambdaQueryWrapperX<WorkReportDO>().eq(WorkReportDO::getTaskId, taskId)
                .orderByDesc(WorkReportDO::getRevisionNo).last("LIMIT 1"));
    }
    default int confirm(Long id, String decision, String comment, Long userId, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<WorkReportDO>().eq(WorkReportDO::getId, id)
                .isNull(WorkReportDO::getConfirmationDecision)
                .set(WorkReportDO::getConfirmationDecision, decision).set(WorkReportDO::getConfirmationComment, comment)
                .set(WorkReportDO::getConfirmedByUserId, userId).set(WorkReportDO::getConfirmedAt, now));
    }
}

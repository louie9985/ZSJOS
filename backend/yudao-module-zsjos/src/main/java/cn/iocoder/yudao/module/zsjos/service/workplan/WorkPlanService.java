package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface WorkPlanService {
    PageResult<WorkPlanRespVO> getPage(WorkPlanPageReqVO reqVO, Long userId);
    PageResult<WorkPlanRespVO> searchPage(WorkPlanSearchReqVO reqVO, Long userId);
    WorkPlanRespVO get(Long id, Long userId);
    WorkTaskRespVO getTask(Long id, Long userId);
    PageResult<WorkTaskRespVO> getMyTaskPage(PageParam pageParam, String status, Long userId);
    Long create(WorkPlanSaveReqVO reqVO, Long userId);
    void update(Long id, WorkPlanSaveReqVO reqVO, Long userId);
    void publish(Long id, Integer version, Long userId);
    void cancel(Long id, WorkPlanCancelReqVO reqVO, Long userId);
    Long addTask(Long planId, WorkTaskSaveReqVO reqVO, Long userId);
    Long createTemporaryTask(WorkTaskSaveReqVO reqVO, Long userId);
    void adjustTask(Long taskId, WorkTaskSaveReqVO reqVO, Long userId);
    void cancelTask(Long taskId, WorkPlanCancelReqVO reqVO, Long userId);
    void submitReport(Long taskId, WorkReportSubmitReqVO reqVO, Long userId);
    void confirmReport(Long taskId, WorkReportConfirmReqVO reqVO, Long userId);
    void submitSummary(Long planId, WorkPlanSummaryReqVO reqVO, Long userId);
    WorkPlanAttachmentUploadRespVO uploadAttachment(MultipartFile file, Long userId) throws IOException;
    WorkPlanExportData export(WorkPlanSearchReqVO reqVO, Long userId);
}

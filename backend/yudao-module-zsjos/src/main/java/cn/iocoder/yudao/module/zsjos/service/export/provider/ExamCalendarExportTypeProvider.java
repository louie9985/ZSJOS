package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleExportReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleRespVO;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXPORT_PERMISSION_DENIED;

@Component
public class ExamCalendarExportTypeProvider
        extends AbstractPagedExportTypeProvider<ExamScheduleExportReqVO, ExamScheduleRespVO> {
    @Resource private ExamScheduleService examScheduleService;
    @Resource private PermissionApi permissionApi;

    @Override public String getType() { return "exam_calendar"; }
    @Override public String getCreatePermission() { return "zsjos:exam-calendar:export"; }
    @Override public void checkCreator(Long userId) {
        if (!permissionApi.hasAnyPermissions(userId, "zsjos:exam-calendar:query")) {
            throw exception(EXPORT_PERMISSION_DENIED);
        }
    }
    @Override protected Class<ExamScheduleExportReqVO> requestType() { return ExamScheduleExportReqVO.class; }
    @Override protected PageResult<ExamScheduleRespVO> getPage(ExamScheduleExportReqVO request, Long userId) {
        return examScheduleService.exportPage(request, userId);
    }
    @Override protected List<String> columns() {
        return List.of("考试名称", "时间类型", "开始日期", "结束日期", "发布状态", "备注");
    }
    @Override protected List<Object> toRow(ExamScheduleRespVO item) {
        boolean exact = "EXACT".equals(item.getScheduleType());
        String status = switch (item.getRecordStatus()) {
            case "DRAFT" -> "草稿";
            case "PUBLISHED" -> "已发布";
            case "REVOKED" -> "已撤销";
            default -> item.getRecordStatus();
        };
        return List.of(value(item.getScheduleName()), exact ? "单日" : "多日",
                value(exact ? item.getExactDate() : item.getStartDate()),
                value(exact ? item.getExactDate() : item.getEndDate()), status, value(item.getRemark()));
    }
    @Override protected String sheetName() { return "考期明细"; }
    private static String value(Object value) { return value == null ? "" : value.toString(); }
}

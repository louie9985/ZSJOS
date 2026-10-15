package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamCalendarExportTypeProviderTest {
    private final ExamScheduleService service = mock(ExamScheduleService.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private ExamCalendarExportTypeProvider provider() {
        var provider = new ExamCalendarExportTypeProvider();
        ReflectionTestUtils.setField(provider, "validator", Validation.buildDefaultValidatorFactory().getValidator());
        ReflectionTestUtils.setField(provider, "examScheduleService", service);
        ReflectionTestUtils.setField(provider, "permissionApi", permissions);
        return provider;
    }
    private ExportTaskDO task() {
        return new ExportTaskDO().setTaskNo("EXP-TEST").setCreatorUserId(7L).setCreatorNameSnapshot("测试导出人")
                .setFilterJson("{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\"}");
    }
    @Test void validatesRequiredDatesAndOrderAndStatus() {
        var provider = provider();
        for (String filter : List.of("{}", "{\"rangeStart\":\"2026-10-01\"}",
                "{\"rangeStart\":\"2026-10-02\",\"rangeEnd\":\"2026-10-01\"}",
                "{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-01\",\"recordStatus\":\"INVALID\"}")) {
            assertThrows(RuntimeException.class, () -> provider.validateFilter(filter));
        }
        assertDoesNotThrow(() -> provider.validateFilter(task().getFilterJson()));
        verifyNoInteractions(service);
    }
    @Test void requiresViewPermissionIncludingAtDownloadCheck() {
        var provider = provider();
        assertEquals("zsjos:exam-calendar:export", provider.getCreatePermission());
        assertThrows(RuntimeException.class, () -> provider.checkCreator(7L));
        when(permissions.hasAnyPermissions(7L, "zsjos:exam-calendar:query")).thenReturn(true);
        assertDoesNotThrow(() -> provider.checkCreator(7L));
    }
    @Test void workbookIncludesAllPagesFullDatesAndLiteralText() throws Exception {
        var row = new ExamScheduleRespVO();
        row.setScheduleName("=测试考期"); row.setScheduleType("MULTI_DAY"); row.setRecordStatus("PUBLISHED");
        row.setStartDate(LocalDate.of(2026,9,28)); row.setEndDate(LocalDate.of(2026,11,2)); row.setRemark("第一行\n第二行");
        when(service.exportPage(any(), eq(7L))).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleExportReqVO.class);
            assertEquals(LocalDate.of(2026,10,1), request.getRangeStart());
            return new PageResult<>(request.getPageNo() == 1 ? Collections.nCopies(200,row) : List.of(row),201L);
        });
        var result = provider().generate(task());
        assertEquals(201, result.rowCount());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            var sheet = workbook.getSheet("考期明细");
            assertEquals(201, sheet.getLastRowNum());
            assertEquals("=测试考期",sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("2026-09-28",sheet.getRow(1).getCell(5).getStringCellValue());
            assertEquals("2026-11-02",sheet.getRow(201).getCell(6).getStringCellValue());
            assertEquals("第一行\n第二行",sheet.getRow(1).getCell(8).getStringCellValue());
        }
        verify(service, times(2)).exportPage(any(),eq(7L));
    }
    @Test void emptyWorkbookHasHeadersAndMetadata() throws Exception {
        when(service.exportPage(any(),eq(7L))).thenReturn(new PageResult<>(List.of(),0L));
        var result = provider().generate(task());
        assertEquals(0,result.rowCount());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            assertEquals("考试名称",workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
            assertEquals("EXP-TEST",workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }
}

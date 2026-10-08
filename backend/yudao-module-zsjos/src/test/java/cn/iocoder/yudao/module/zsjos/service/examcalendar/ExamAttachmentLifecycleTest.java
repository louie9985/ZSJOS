package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamAttachmentLifecycleTest {
    @Mock ExamScheduleMapper mapper;
    @Mock PermissionApi permissionApi;
    @Mock ExamScheduleAttachmentService attachmentService;
    @Mock CalendarNotificationSnapshotService notificationSnapshots;
    @InjectMocks ExamScheduleService service;
    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "attachment-test"), ExamScheduleDO.class);
    }
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    ExamScheduleSaveReqVO request() { return new ExamScheduleSaveReqVO().setScheduleName("考试通知").setScheduleType("EXACT")
            .setExactDate(LocalDate.of(2099,1,1)).setRemark("原备注"); }
    ExamScheduleDO row() { return new ExamScheduleDO().setId(5L).setScheduleName("考试通知").setScheduleType("EXACT")
            .setExactDate(LocalDate.of(2099,1,1)).setRecordStatus("DRAFT").setCalendarVersion(2).setRemark("原备注").setAttachmentIdsJson("[11]"); }
    @Test void createsWithReferencesAndFailedValidationCannotInsert() {
        when(permissionApi.hasAnyPermissions(7L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(attachmentService.validate(List.of(11L), null, 7L)).thenReturn("[11]");
        service.create(request().setAttachmentIds(List.of(11L)), 7L);
        var captor = ArgumentCaptor.forClass(ExamScheduleDO.class); verify(mapper).insert(captor.capture());
        assertEquals("[11]",captor.getValue().getAttachmentIdsJson());
        when(attachmentService.validate(List.of(12L), null, 7L)).thenThrow(new IllegalArgumentException("invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.create(request().setAttachmentIds(List.of(12L)),7L));
        verify(mapper,times(1)).insert(any(ExamScheduleDO.class));
    }
    @Test void omissionPreservesAndRemovalAdvancesVersion() {
        when(permissionApi.hasAnyPermissions(7L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectForUpdate(5L)).thenReturn(row());
        service.update(5L,request(),7L);
        var captor=ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).update(captor.capture(),any());
        assertEquals("[11]",captor.getValue().getAttachmentIdsJson()); assertEquals(2,captor.getValue().getCalendarVersion());
        when(attachmentService.validate(List.of(),"[11]",7L)).thenReturn("[]");
        service.update(5L,request().setAttachmentIds(List.of()),7L);
        verify(mapper,times(2)).update(captor.capture(),any());
        assertEquals("[]",captor.getValue().getAttachmentIdsJson()); assertEquals(3,captor.getValue().getCalendarVersion());
    }
    @Test void reeditReturnsAttachmentsEvenOnIdempotentReplay() {
        when(permissionApi.hasAnyPermissions(7L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var row=row().setRecordStatus("REVOKED").setRevokedBy(7L).setReeditClaimedAt(LocalDateTime.now()).setReeditOperationKey("request-000000001");
        row.setTenantId(1L); row.setDeleted(true);
        when(mapper.selectReeditRecordForUpdate(5L,1L)).thenReturn(row);
        var files=List.of(new ExamScheduleAttachmentRespVO().setFileId(11L).setName("官方通知.pdf"));
        when(attachmentService.describe("[11]")).thenReturn(files);
        assertEquals(files,service.reedit(5L,"request-000000001",7L).getAttachments());
        verify(mapper,never()).insert(any(ExamScheduleDO.class));
    }
    @Test void attachmentChangesFingerprintButHistoricalEmptyVersionsStayCompatible() {
        var row=row().setAttachmentIdsJson(null);
        String original=CalendarNotificationSnapshotService.projectExam(row,"MANUAL").getContentHash();
        row.setAttachmentIdsJson("[]");
        assertEquals(original,CalendarNotificationSnapshotService.projectExam(row,"MANUAL").getContentHash());
        row.setAttachmentIdsJson("[11]");
        var snapshot=CalendarNotificationSnapshotService.projectExam(row,"MANUAL");
        assertNotEquals(original,snapshot.getContentHash()); assertTrue(snapshot.getDetailsJson().contains("attachmentIds"));
    }
}

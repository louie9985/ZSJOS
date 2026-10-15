package cn.iocoder.yudao.module.zsjos.service.contentreview;
import cn.iocoder.yudao.module.zsjos.service.positioning.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ContentReviewPositioningProjectionTest {
    @InjectMocks PositioningCardService service;
    @Mock ContentReviewStudentAccess reviewAccess;
    @Mock PositioningCardSubmissionMapper submissionMapper;
    @Mock PositioningCardMapper mapper;
    @Mock PositioningAssignmentService assignmentService;
    @Mock PositioningCardObjectPermissionProvider objectPermissionProvider;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi adminUserApi;
    @Test void nonOwnerReadsOnlyBoundAccountServiceSubmissionsWithoutOriginalOwnerCheck() {
        when(reviewAccess.requireAccount(10L,40L,20L)).thenReturn(new MediaAccountDO().setId(40L).setStudentPersonId(30L).setCreateServiceRelationId(50L));
        var row=new PositioningCardSubmissionDO();row.setId(61L);row.setCardId(60L);row.setAccountId(null);row.setStudentPersonId(30L);row.setStatus("confirmed");row.setServiceRelationId(50L);
        row.setValuesSnapshotJson("{\"goal\":\"原定位\"}");
        var other=new PositioningCardSubmissionDO();other.setId(62L);other.setServiceRelationId(51L);
        when(submissionMapper.selectByService(50L)).thenReturn(List.of(row,other));
        when(assignmentService.masterId(50L)).thenReturn(60L);
        var result=service.accountForReview(10L,40L,20L);
        assertEquals(1,result.size());assertEquals("原定位",result.getFirst().getValuesSnapshot().get("goal"));
        assertFalse(result.getFirst().getCanUploadEvidence());assertTrue(result.getFirst().getAvailableActions().isEmpty());
        verify(objectPermissionProvider,never()).check(anyLong(),anyString(),anyLong());
        when(submissionMapper.selectCurrentConfirmedByAccount(40L)).thenReturn(row);
        var overview=service.overviewForReview(10L,40L,20L);
        assertEquals(61L,overview.account().effective().getSubmissionId());
        assertFalse(overview.service().canCreate()); assertFalse(overview.service().canSubmit());
        assertFalse(overview.service().canSelectMaster());
        when(submissionMapper.selectCurrentConfirmedByAccount(40L)).thenReturn(other);
        assertNull(service.overviewForReview(10L,40L,20L).account().effective());
    }
}

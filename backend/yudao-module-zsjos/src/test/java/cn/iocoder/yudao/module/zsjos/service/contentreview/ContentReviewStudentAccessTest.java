package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentReviewStudentAccessTest {
    @InjectMocks ContentReviewStudentAccess access;
    @Mock ContentReviewBatchMapper batches;
    @Mock MediaAccountMapper accounts;
    @Mock ServiceRelationMapper relations;
    @Mock ContentReviewSelectionService selection;
    @Mock BpmProcessInstanceApi processes;
    ContentReviewBatchDO batch; MediaAccountDO account;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        batch=new ContentReviewBatchDO();batch.setId(10L);batch.setTenantId(1L);batch.setDirectorUserId(20L);
        batch.setStudentPersonId(30L);batch.setStatus("DIRECTOR_REVIEW");batch.setSubmittedAt(LocalDateTime.now());
        batch.setProcessInstanceId("process");batch.setBusinessKey("content-review-batch:10");
        batch.setRelationSnapshotJson("{\"source\":\"STARTER_ROLE_SELECTION\"}");
        batch.setContextSnapshotJson("{\"reviewAccountScopes\":[{\"accountId\":40,\"directorUserId\":50,\"operatorUserId\":60}]}");
        account=new MediaAccountDO().setId(40L).setStudentPersonId(30L).setDirectorUserId(50L).setOwnerOperatorUserId(60L);account.setTenantId(1L);
        lenient().when(batches.selectById(10L)).thenReturn(batch);
        lenient().when(accounts.selectById(40L)).thenReturn(account);
        lenient().when(selection.hasReadPermissions(20L)).thenReturn(true);
        lenient().when(processes.isRunning("process","content-review-batch:10")).thenReturn(true);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void selectedNonOwnerCanReadOnlyExactAccountThroughoutRound() {
        assertEquals(40L,access.requireAccount(10L,40L,20L).getId());
        batch.setStatus("FINAL_REVIEW"); assertTrue(access.hasPermission(10L,"read",20L));
        assertFalse(access.hasPermission(10L,"edit",20L));
        assertThrows(RuntimeException.class,()->access.requireAccount(10L,41L,20L));
        assertThrows(RuntimeException.class,()->access.requireService(10L,1L,20L));
    }
    @Test void terminalStatesAndEndedEngineRevokeAccess() {
        for(String state:java.util.List.of("COMPLETED","PUBLISHED","NEED_MODIFY","CANCELLED","DRAFT")) {
            batch.setStatus(state);assertFalse(access.hasPermission(10L,"read",20L));
        }
        batch.setStatus("DIRECTOR_REVIEW");when(processes.isRunning("process","content-review-batch:10")).thenReturn(false);
        assertFalse(access.hasPermission(10L,"read",20L));
    }
    @Test void historicalRoundsOtherUsersAndOtherTenantsNeverGainAccess() {
        assertFalse(access.hasPermission(10L,"read",21L));
        batch.setRelationSnapshotJson("{\"scene\":\"old_binding\"}");assertFalse(access.hasPermission(10L,"read",20L));
        batch.setRelationSnapshotJson("{\"source\":\"STARTER_ROLE_SELECTION\"}");
        TenantContextHolder.setTenantId(2L);assertFalse(access.hasPermission(10L,"read",20L));
    }
    @Test void changedOwnershipOrRevokedFeatureFailsClosed() {
        account.setStudentPersonId(31L);assertFalse(access.hasPermission(10L,"read",20L));
        account.setStudentPersonId(30L).setDirectorUserId(51L);assertFalse(access.hasPermission(10L,"read",20L));
        account.setDirectorUserId(50L);when(selection.hasReadPermissions(20L)).thenReturn(false);
        assertFalse(access.hasPermission(10L,"read",20L));
    }
}

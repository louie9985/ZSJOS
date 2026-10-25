package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDeleteRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountDeleteRequestMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaAccountDeleteContentProviderTest {
    @InjectMocks MediaAccountDeleteContentProvider provider;
    @Mock MediaAccountDeleteRequestMapper requests;
    @Mock MediaAccountObjectPermissionProvider perms;
    @Mock AdminUserApi users;

    private MediaAccountDeleteRequestDO request() {
        return new MediaAccountDeleteRequestDO().setAccountId(210L).setRequestedByUserId(525L).setReviewerUserId(47L)
                .setStatus("pending").setReason("误新增").setAccountSnapshotJson(
                        "{\"accountNo\":\"MA-TEST\",\"nickname\":\"测试账号\",\"platform\":\"测试平台\",\"requestedByName\":\"申请运营\",\"reviewerName\":\"所属编导\"}");
    }

    @Test void displaysSnapshotNamesNumberPlatformAndChineseStateForExactRound() {
        when(requests.byProcess("round-old")).thenReturn(request());
        var detail = provider.detail("210", 47L, "round-old");
        assertEquals("删除账号 · 测试账号", detail.getTitle()); assertEquals("审批中", detail.getStatusText());
        var values = detail.getGroups().getFirst().getFields().stream().map(f -> f.getValue()).toList();
        assertTrue(values.containsAll(java.util.List.of("测试账号", "MA-TEST", "测试平台", "申请运营", "所属编导")));
        assertFalse(values.contains("210")); assertFalse(values.contains("525")); assertFalse(values.contains("47"));
        verify(requests, never()).byAccount(anyLong()); verifyNoInteractions(users);
    }

    @Test void oldRequestsResolveRealUsersWithoutInventingAccountSnapshots() {
        var request = request().setAccountSnapshotJson("{}"); when(requests.byProcess("old")).thenReturn(request);
        var operator = new AdminUserRespDTO(); operator.setNickname("当前通讯录运营");
        var director = new AdminUserRespDTO(); director.setNickname("当前通讯录编导");
        when(users.getUser(525L)).thenReturn(operator); when(users.getUser(47L)).thenReturn(director);
        var detail = provider.detail("210", 47L, "old");
        assertFalse(detail.getTitle().contains("210"));
        assertTrue(detail.getGroups().getFirst().getFields().stream().anyMatch(f -> "当前通讯录编导".equals(f.getValue())));
    }

    @Test void mismatchedInstanceOrUnrelatedViewerNeverReceivesFields() {
        when(requests.byProcess("other")).thenReturn(request().setAccountId(211L));
        assertTrue(provider.detail("210", 47L, "other").getGroups().isEmpty());
        when(requests.byProcess("current")).thenReturn(request());
        assertNull(provider.brief("210", 999L, "current"));
        assertTrue(provider.detail("210", 999L, "current").getGroups().isEmpty());
    }

    @Test void approvedButUnfinishedIsDisplayedAsRetryableRatherThanDeleted() {
        when(requests.byProcess("current")).thenReturn(request().setStatus("approved_pending"));
        assertEquals("审批已通过，删除处理待重试", provider.detail("210", 47L, "current").getStatusText());
    }
}

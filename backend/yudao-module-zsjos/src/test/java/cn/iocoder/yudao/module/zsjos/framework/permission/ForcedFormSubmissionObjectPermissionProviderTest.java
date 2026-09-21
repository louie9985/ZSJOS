package cn.iocoder.yudao.module.zsjos.framework.permission;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.ForcedFormSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForcedFormSubmissionObjectPermissionProviderTest {
    @InjectMocks private ForcedFormSubmissionObjectPermissionProvider provider;
    @Mock private ForcedFormMapper formMapper;
    @Mock private ForcedFormSubmissionMapper submissionMapper;
    @Mock private PermissionApi permissionApi;

    @Test void readRequiresSubmissionAndParentAndDoesNotAuthorizeCommands() {
        assertFalse(provider.hasPermission(1L, "read", 30L));
        var submission = new ForcedFormSubmissionDO();
        submission.setId(1L); submission.setFormId(2L); submission.setUserId(10L);
        when(submissionMapper.selectById(1L)).thenReturn(submission);
        assertFalse(provider.hasPermission(1L, "read", 30L));
        var form = new ForcedFormDO(); form.setId(2L); form.setCreator("20");
        when(formMapper.selectById(2L)).thenReturn(form);
        assertTrue(provider.hasPermission(1L, "read", 10L));
        assertTrue(provider.hasPermission(1L, "read", 20L));
        assertFalse(provider.hasPermission(1L, "read", 30L));
        when(permissionApi.hasTenantReadAllAccess(30L)).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "read", 30L));
        assertFalse(provider.hasPermission(1L, "submit", 30L));
        assertFalse(provider.hasPermission(1L, "attachment-upload", 30L));
    }
}

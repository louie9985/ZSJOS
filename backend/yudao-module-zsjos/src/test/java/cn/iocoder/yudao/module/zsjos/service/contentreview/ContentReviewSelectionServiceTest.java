package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentReviewSelectionServiceTest {
    @InjectMocks ContentReviewSelectionService service;
    @Mock ContentReviewBatchMapper batchMapper;
    @Mock ContentReviewConfigService configService;
    @Mock BpmDefinitionReadApi definitionApi;
    @Mock AdminUserApi userApi;
    @Mock PermissionApi permissionApi;
    @Test void candidatesAreRoleMembersWithAllPermissionsExcludingInitiator() {
        var batch=new ContentReviewBatchDO();batch.setOperatorUserId(1L);batch.setStatus("DRAFT");batch.setRevisionOfBatchId(9L);
        var previous=new ContentReviewBatchDO();previous.setDirectorUserId(2L);
        when(batchMapper.selectById(10L)).thenReturn(batch);when(batchMapper.selectById(9L)).thenReturn(previous);
        var config=new ContentReviewConfigDO();when(configService.requireReadyConfig(1L)).thenReturn(config);
        var def=new BpmProcessDefinitionMetadataRespDTO();def.setId("v2");def.setVersion(2);
        when(configService.requireCurrentDefinition(config)).thenReturn(def);
        when(configService.requireReviewTaskKeys(def)).thenReturn(new ContentReviewConfigService.ReviewTaskKeys("opaque-node","end-node"));
        var scope=new BpmStarterSelectionScopeRespDTO();scope.setRoleIds(List.of(7L));scope.setUserIds(List.of(1L,2L,3L));
        when(definitionApi.getStarterSelectionScope("v2","opaque-node")).thenReturn(scope);
        when(userApi.getUserList(scope.getUserIds())).thenReturn(List.of(user(1L),user(2L),user(3L)));
        for(String p:ContentReviewSelectionService.READ_PERMISSIONS) when(permissionApi.hasAnyPermissions(2L,p)).thenReturn(true);
        var result=service.context(10L,1L);
        assertEquals(List.of(2L),result.getCandidates().stream().map(c->c.id()).toList());
        assertEquals("v2",result.getProcessDefinitionId());assertEquals(2L,result.getPreviousDirectorUserId());
        when(definitionApi.getStarterSelectionScope("v2","opaque-node")).thenReturn(null);
        assertThrows(RuntimeException.class,()->service.context(10L,1L));
    }
    @Test void nonOwnerCannotEnumerateCandidates() {
        var batch=new ContentReviewBatchDO();batch.setOperatorUserId(2L);batch.setStatus("DRAFT");
        when(batchMapper.selectById(10L)).thenReturn(batch);
        assertThrows(RuntimeException.class,()->service.context(10L,1L));
        verifyNoInteractions(definitionApi,configService,userApi);
    }
    private AdminUserRespDTO user(Long id) {var u=new AdminUserRespDTO();u.setId(id);u.setStatus(0);u.setNickname("测试编导"+id);return u;}
}

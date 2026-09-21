package cn.iocoder.yudao.module.zsjos.service.userrelation;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.userrelation.UserRelationSceneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRelationSceneServiceImplTest {

    @InjectMocks
    private UserRelationSceneServiceImpl service;
    @Mock
    private UserRelationSceneMapper sceneMapper;
    @Mock
    private LeadAssignmentRelationMapper relationMapper;
    @Mock
    private LeadAssignmentRelationLogMapper relationLogMapper;
    @Mock
    private PostApi postApi;

    @Test
    void detailResponseKeepsEveryConfiguredPost() {
        var row = scene(1L, "multiple", 0);
        row.setSourcePostCodes(java.util.List.of("first", "second"));
        row.setTargetPostCodes(java.util.List.of("third", "fourth"));
        when(sceneMapper.selectById(1L)).thenReturn(row);
        var response = service.getScene(1L);
        assertEquals(row.getSourcePostCodes(), response.getSourcePostCodes());
        assertEquals(row.getTargetPostCodes(), response.getTargetPostCodes());
    }

    @Test
    void createSceneRejectsDuplicateCode() {
        UserRelationSceneDO existing = scene(1L, "duplicate", CommonStatusEnum.ENABLE.getStatus());
        when(sceneMapper.selectByCode("duplicate")).thenReturn(existing);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createScene(request(null, "duplicate")));

        assertEquals(USER_RELATION_SCENE_CODE_DUPLICATE.getCode(), exception.getCode());
        verify(sceneMapper, never()).insert(any(UserRelationSceneDO.class));
    }

    @Test
    void updateSceneRejectsCodeChange() {
        when(sceneMapper.selectById(1L)).thenReturn(scene(1L, "original", CommonStatusEnum.ENABLE.getStatus()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateScene(request(1L, "changed")));

        assertEquals(USER_RELATION_SCENE_CODE_IMMUTABLE.getCode(), exception.getCode());
        verify(sceneMapper, never()).updateById(any(UserRelationSceneDO.class));
    }

    @Test
    void createSceneRejectsUnknownPost() {
        when(sceneMapper.selectByCode("new_scene")).thenReturn(null);
        when(postApi.getPostByCode("source_post")).thenReturn(post(11L));
        when(postApi.getPostByCode("target_post")).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createScene(request(null, "new_scene")));

        assertEquals(USER_RELATION_SCENE_POST_INVALID.getCode(), exception.getCode());
        verify(sceneMapper, never()).insert(any(UserRelationSceneDO.class));
    }

    @Test
    void deleteSceneRejectsSceneWithRelations() {
        when(sceneMapper.selectById(1L)).thenReturn(scene(1L, "in_use", CommonStatusEnum.ENABLE.getStatus()));
        when(relationMapper.selectCountByScene("in_use")).thenReturn(1L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteScene(1L));

        assertEquals(USER_RELATION_SCENE_IN_USE.getCode(), exception.getCode());
        verify(sceneMapper, never()).deleteById(anyLong());
        verifyNoInteractions(relationLogMapper);
    }

    @Test
    void getEnabledSceneRejectsDisabledScene() {
        when(sceneMapper.selectByCode("disabled"))
                .thenReturn(scene(1L, "disabled", CommonStatusEnum.DISABLE.getStatus()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getEnabledSceneByCode("disabled"));

        assertEquals(USER_RELATION_SCENE_DISABLED.getCode(), exception.getCode());
    }

    @Test
    void savesEverySelectedPostAndDeduplicates() {
        var request = request(null, "multiple");
        request.setSourcePostCodes(java.util.List.of("source_post", "second", "source_post"));
        request.setTargetPostCodes(java.util.List.of("target_post", "second"));
        when(postApi.getPostByCode(anyString())).thenReturn(post(11L));
        service.createScene(request);
        var captor = org.mockito.ArgumentCaptor.forClass(UserRelationSceneDO.class);
        verify(sceneMapper).insert(captor.capture());
        assertEquals(java.util.List.of("source_post", "second"), captor.getValue().getSourcePostCodes());
        assertEquals(java.util.List.of("target_post", "second"), captor.getValue().getTargetPostCodes());
    }

    @Test
    void emptyArrayDoesNotFallBackToLegacyPost() {
        var request = request(null, "empty");
        request.setSourcePostCodes(java.util.List.of());
        assertEquals(USER_RELATION_SCENE_POST_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.createScene(request)).getCode());
        verify(sceneMapper, never()).insert(any(UserRelationSceneDO.class));
    }

    @Test
    void unknownAdditionalPostRejectsWholeSave() {
        var request = request(null, "unknown");
        request.setSourcePostCodes(java.util.List.of("source_post", "unknown"));
        when(postApi.getPostByCode("source_post")).thenReturn(post(11L));
        assertThrows(ServiceException.class, () -> service.createScene(request));
        verify(sceneMapper, never()).insert(any(UserRelationSceneDO.class));
    }

    @Test
    void readsLegacyPostWithoutRevivingExplicitEmptyArray() {
        var row = new UserRelationSceneDO();
        row.setSourcePostCode("source_post");
        row.setTargetPostCode("target_post");
        assertEquals(java.util.List.of("source_post"), row.getSourcePostCodes());
        assertEquals(java.util.List.of("target_post"), row.getTargetPostCodes());
        row.setSourcePostCodes(java.util.List.of());
        assertEquals(java.util.List.of(), row.getSourcePostCodes());
    }

    private static UserRelationSceneSaveReqVO request(Long id, String code) {
        UserRelationSceneSaveReqVO request = new UserRelationSceneSaveReqVO();
        request.setId(id);
        request.setName("测试场景");
        request.setCode(code);
        request.setSourceLabel("来源用户");
        request.setTargetLabel("目标用户");
        request.setSourcePostCode("source_post");
        request.setTargetPostCode("target_post");
        request.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return request;
    }

    private static UserRelationSceneDO scene(Long id, String code, Integer status) {
        UserRelationSceneDO scene = new UserRelationSceneDO();
        scene.setId(id);
        scene.setCode(code);
        scene.setStatus(status);
        return scene;
    }

    private static PostRespDTO post(Long id) {
        PostRespDTO post = new PostRespDTO();
        post.setId(id);
        post.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return post;
    }

}

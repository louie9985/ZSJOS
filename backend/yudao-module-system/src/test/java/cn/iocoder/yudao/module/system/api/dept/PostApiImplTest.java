package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.PostDO;
import cn.iocoder.yudao.module.system.service.dept.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostApiImplTest {

    @InjectMocks
    private PostApiImpl api;
    @Mock
    private PostService postService;

    @Test
    void postCodeLookupIgnoresCallerDataScope() throws Exception {
        when(postService.getPost("new_media_operator"))
                .thenReturn(new PostDO().setId(10L).setCode("new_media_operator").setName("新媒体运营"));

        assertEquals(10L, api.getPostByCode("new_media_operator").getId());
        DataPermission annotation = PostApiImpl.class.getMethod("getPostByCode", String.class)
                .getAnnotation(DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }
}

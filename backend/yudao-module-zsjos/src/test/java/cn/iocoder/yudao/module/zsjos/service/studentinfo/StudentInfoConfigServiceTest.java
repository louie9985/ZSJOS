package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormConfigMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInfoConfigServiceTest {
    @InjectMocks StudentInfoConfigService service;
    @Mock StudentInfoFormConfigMapper mapper;
    @Spy StudentInfoFields fields=new StudentInfoFields();
    @BeforeEach void setup() { TenantContextHolder.setTenantId(9L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void staleDraftCannotOverwriteNewerRevision() {
        var row=new StudentInfoFormConfigDO(); row.setId(3L); row.setRevision(2);
        when(mapper.state("DRAFT")).thenReturn(row);
        var request=new Save(); request.setId(3L); request.setRevision(1); request.setFields(fields.presets());
        assertThrows(ServiceException.class, () -> service.save(request));
        var order=inOrder(mapper); order.verify(mapper).ensureLock(9L); order.verify(mapper).lockTenant(9L);
        verify(mapper,never()).updateById(any(StudentInfoFormConfigDO.class));
    }
    @Test void publishArchivesOldVersionWithoutRewritingItsFields() {
        var definitions=fields.presets(); definitions.forEach(f -> f.setEnabled("text".equals(f.getType())));
        var draft=new StudentInfoFormConfigDO(); draft.setId(3L); draft.setRevision(2); draft.setStatus("DRAFT");
        draft.setFieldsJson(JsonUtils.toJsonString(definitions));
        var old=new StudentInfoFormConfigDO(); old.setId(2L); old.setStatus("PUBLISHED"); old.setFieldsJson("[]");
        when(mapper.state("DRAFT")).thenReturn(draft); when(mapper.state("PUBLISHED")).thenReturn(old);
        var req=new Publish(); req.setId(3L); req.setRevision(2); service.publish(req);
        assertEquals("ARCHIVED",old.getStatus()); assertEquals("[]",old.getFieldsJson());
        assertEquals("PUBLISHED",draft.getStatus()); assertEquals(3,draft.getRevision()); assertNotNull(draft.getPublishedAt());
    }
    @Test void missingPublicationFailsClosed() { assertThrows(ServiceException.class,service::published); }
}

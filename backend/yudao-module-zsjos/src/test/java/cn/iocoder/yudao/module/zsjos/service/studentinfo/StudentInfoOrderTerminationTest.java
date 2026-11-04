package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInfoOrderTerminationTest {
    @InjectMocks StudentInfoFormService service;
    @Mock StudentInfoFormMapper forms;
    @Mock RegistrationCaseMapper cases;
    @Mock SalesOrderMapper orders;
    @Mock StudentInfoTaskMapper collectionTasks;
    @Mock StudentInfoFormValueMapper values;
    @Mock StudentInfoImageService images;
    @Mock TenantFrameworkService tenants;
    @Spy StudentInfoFields fields = new StudentInfoFields();
    private final String token="a".repeat(43);
    private StudentInfoFormDO form() {
        var form=new StudentInfoFormDO(); form.setId(1L); form.setRegistrationCaseId(2L); form.setTenantId(1L);
        form.setStatus("DRAFT"); form.setExpiresAt(LocalDateTime.now().plusDays(1)); form.setTokenHash(StudentInfoFormService.hash(token));
        form.setFieldsSnapshot("[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"enabled\":true,\"required\":true}]");
        return form;
    }
    private RegistrationCaseDO item() { var item=new RegistrationCaseDO(); item.setId(2L); item.setOrderId(3L); item.setStatus("ended"); return item; }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"revision_required","superseded","terminated"})
    void cancelledOrderTaskPublicLinkStillSavesAndSubmits(String status) {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"rejection-test"),StudentInfoFormDO.class);
        var form=form();form.setRegistrationCaseId(null);form.setOrderItemId(4L);form.setPersonId(5L);
        var task=new StudentInfoTaskDO();task.setId(6L);task.setOrderId(3L);task.setOrderItemId(4L);task.setPersonId(5L);
        when(forms.byToken(anyString())).thenReturn(form);when(forms.lock(1L)).thenReturn(form);
        when(collectionTasks.byItem(4L)).thenReturn(task);
        var order=new SalesOrderDO();order.setStatus(status);when(orders.selectByIdForUpdate(3L,1L)).thenReturn(order);
        var draft=new DraftWrite();draft.setRevision(0L);draft.setValues(Map.of("name","合成学员"));
        assertEquals("合成学员",service.saveDraft(token,draft).getValues().get("name"));
        var submit=new Submit();submit.setRevision(0L);submit.setValues(Map.of("name","合成学员"));
        service.submit(token,submit);
        verify(values).insert(any(StudentInfoFormValueDO.class));
        verify(forms).update(isNull(),argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<StudentInfoFormDO> wrapper)->((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentInfoFormDO>)wrapper).getParamNameValuePairs().containsValue("SUBMITTED")));
        assertNull(form.getRevokedAt());assertEquals(StudentInfoFormService.hash(token),form.getTokenHash());
        verify(collectionTasks,never()).updateById(any(StudentInfoTaskDO.class));
    }
    @Test void terminatedOrderDoesNotInvalidateExistingPublicDraft() {
        TenantContextHolder.setTenantId(1L); TenantContextHolder.setIgnore(false);
        var form=form(); when(forms.byToken(anyString())).thenReturn(form); when(forms.lock(1L)).thenReturn(form);
        when(cases.selectById(2L)).thenReturn(item()); when(cases.selectByIdForUpdate(2L,1L)).thenReturn(item());
        var request=new DraftWrite(); request.setRevision(0L); request.setValues(Map.of("name","合成草稿"));
        assertEquals("合成草稿",service.saveDraft(token,request).getValues().get("name"));
        verify(forms).updateById(any(StudentInfoFormDO.class));
        assertEquals("DRAFT",service.effectiveState(form));
    }
    @Test void staffCannotObtainTerminatedOrdersLink() {
        when(forms.byCase(2L)).thenReturn(form()); when(cases.selectById(2L)).thenReturn(item());
        var order=new SalesOrderDO(); order.setStatus("terminated"); when(orders.selectById(3L)).thenReturn(order);
        assertThrows(ServiceException.class,()->service.getLink(2L));
    }
}

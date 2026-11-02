package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.Command;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInfoTaskServiceTest {
    @InjectMocks StudentInfoTaskService service;
    @Mock StudentInfoTaskMapper tasks;
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper registrationCases;
    @Mock StudentInfoTaskPermissionProvider permission;
    @Mock SalesOrderMapper orders;
    @Mock SalesOrderApprovalRoundMapper approvalRounds;
    @Mock SalesOrderItemMapper items;
    @Mock StudentInfoFormMapper forms;
    @Mock StudentInfoFormValueMapper answers;
    @Mock StudentArchiveRevisionMapper revisions;
    @Mock StudentInfoFormService collection;
    @Mock StudentInfoImageService images;
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private SalesOrderDO order(String status) { var order=new SalesOrderDO();order.setId(1L);order.setPersonId(3L);order.setStatus(status);return order; }
    private StudentInfoTaskDO task() { var task=new StudentInfoTaskDO();task.setId(4L);task.setOrderId(1L);task.setOrderItemId(2L);task.setPersonId(3L);task.setVersion(0);return task; }
    private Command command() { var req=new Command();req.setVersion(0);req.setIdempotencyKey("fixture-key");req.setSourceFormId(8L);req.setSourceRevision(0);return req; }
    @Test void rejectedAndSupersededTasksStayCancelledRegardlessOfFormCompletion() {
        var task=task();when(tasks.selectById(4L)).thenReturn(task);
        when(collection.prepareTaskForm(task)).thenReturn(new StudentInfoFormDO());
        for (String status:List.of("revision_required","superseded","terminated")) {
            when(orders.selectById(1L)).thenReturn(order(status));
            if ("terminated".equals(status)) when(approvalRounds.selectCount(any())).thenReturn(1L);
            task.setFormId(null);
            assertEquals("CANCELLED",service.detail(4L).getStatus());
            task.setFormId(8L);var form=submitted();when(forms.selectById(8L)).thenReturn(form);
            for (String state:List.of("DRAFT","SUBMITTED")) {
                form.setStatus(state);form.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
                assertEquals("CANCELLED",service.detail(4L).getStatus());
                assertFalse(service.detail(4L).getCanProcess());
            }
            form.setSubmitSource("reuse");assertEquals("CANCELLED",service.detail(4L).getStatus());
        }
        verify(forms,never()).updateById(any(StudentInfoFormDO.class));
        verify(collection,never()).rotateTaskForm(any());
    }
    @Test void rejectedOrderBlocksAllStaffCommandsWithoutRevokingThePublicForm() {
        when(tasks.selectById(4L)).thenReturn(task());when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("revision_required"));
        assertThrows(ServiceException.class,()->service.link(4L));
        assertThrows(ServiceException.class,()->service.generate(4L,command()));
        assertThrows(ServiceException.class,()->service.regenerate(4L,command()));
        assertThrows(ServiceException.class,()->service.revoke(4L,command()));
        assertThrows(ServiceException.class,()->service.sent(4L,command()));
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));
        verifyNoInteractions(forms,collection,images);
        assertTrue(StudentInfoTaskService.open(order("pending_approval")));
        assertTrue(StudentInfoTaskService.open(order("effective")));
    }
    @Test void cancelledFilterIncludesRejectedHistoryAndExcludesItFromPending() throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"filter-test"),StudentInfoTaskDO.class);
        when(permission.canList(any())).thenReturn(true);
        try (var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:collection_rejection;MODE=MySQL");var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE zsjos_student_info_task(id BIGINT,order_id BIGINT,tenant_id BIGINT,form_id BIGINT)");
            statement.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR(30))");
            statement.execute("CREATE TABLE zsjos_order_approval_round(order_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR(30))");
            statement.execute("INSERT INTO zsjos_order VALUES(1,1,0,'revision_required'),(2,1,0,'superseded'),(3,1,0,'terminated'),(4,1,0,'pending_approval'),(5,1,0,'terminated')");
            statement.execute("INSERT INTO zsjos_order_approval_round VALUES(3,1,0,'rejected'),(5,2,0,'rejected'),(5,1,1,'rejected')");
            statement.execute("INSERT INTO zsjos_student_info_task VALUES(1,1,1,NULL),(2,2,1,20),(3,3,1,30),(4,4,1,NULL),(5,5,1,NULL)");
            when(tasks.selectPage(any(cn.iocoder.yudao.framework.common.pojo.PageParam.class),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call->{
                var wrapper=(com.baomidou.mybatisplus.core.conditions.Wrapper<?>)call.getArgument(1);
                try(var result=statement.executeQuery("SELECT id FROM zsjos_student_info_task WHERE "+wrapper.getSqlSegment())) {
                    var ids=new java.util.ArrayList<Long>();while(result.next())ids.add(result.getLong(1));
                    var query=(cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.Query)call.getArgument(0);
                    assertEquals("CANCELLED".equals(query.getStatus())?List.of(3L,2L,1L):List.of(5L,4L),ids);
                }
                return new cn.iocoder.yudao.framework.common.pojo.PageResult<StudentInfoTaskDO>(List.of(),0L);
            });
            var query=new cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.Query();
            query.setStatus("CANCELLED");service.page(query);query.setStatus("PENDING");service.page(query);
        }
    }
    @Test void registrationNavigationResolvesExactItemAndReturnsReverseTarget() {
        when(permission.canList(any())).thenReturn(true);
        var item=new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO();item.setId(6L);item.setOrderId(1L);item.setOrderItemId(2L);
        when(registrationCases.selectById(6L)).thenReturn(item);when(registrationCases.selectByOrderItemId(2L)).thenReturn(item);
        var task=task();when(tasks.byItem(2L)).thenReturn(task);when(orders.selectById(1L)).thenReturn(order("effective"));
        when(collection.prepareTaskForm(task)).thenReturn(new StudentInfoFormDO());
        var purchased = new SalesOrderItemDO(); purchased.setOrderId(1L);
        purchased.setProductSnapshot("{\"name\":\"课程\",\"specs\":[{\"attrKey\":\"sale\",\"attrName\":\"销售规格\",\"value\":\"v1\",\"label\":\"三级 | 精品班\",\"labelMissing\":false}]}");
        when(items.selectById(2L)).thenReturn(purchased);
        var row=service.byRegistration(6L);assertEquals(4L,row.getId());assertEquals(6L,row.getRegistrationCaseId());
        assertEquals("三级 | 精品班", row.getSpecs().get(0).label());
    }
    @Test void registrationNavigationRejectsOrderMismatchAndAbsentScope() {
        assertThrows(ServiceException.class,()->service.byRegistration(6L));verifyNoInteractions(registrationCases,tasks);
        when(permission.canList(any())).thenReturn(true);
        var item=new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO();item.setOrderId(99L);item.setOrderItemId(2L);
        when(registrationCases.selectById(6L)).thenReturn(item);when(tasks.byItem(2L)).thenReturn(task());
        assertThrows(ServiceException.class,()->service.byRegistration(6L));verifyNoInteractions(collection);
    }
    @Test void generationAutomaticallyPrefillsLatestArchiveWithoutClientSourceAndReplaysSafely() {
        var task=task();when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("pending_approval"));when(orders.selectById(1L)).thenReturn(order("pending_approval"));
        var target=new StudentInfoFormDO();when(collection.prepareTaskForm(task)).thenReturn(target);
        var source=submitted();when(forms.currentArchive(3L)).thenReturn(source);when(forms.lock(8L)).thenReturn(source);
        var field=new Field();field.setKey("name");field.setLabel("姓名");field.setType("text");field.setEnabled(true);field.setRequired(true);
        when(collection.definitions(any())).thenReturn(List.of(field));
        var answer=new StudentInfoFormValueDO();answer.setFieldKey("name");answer.setValueText("已有资料待核对");when(answers.byForm(8L)).thenReturn(List.of(answer));
        doAnswer(call->{target.setId(9L);target.setStatus("DRAFT");target.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));return null;}).when(collection).issueTaskForm(target);
        when(forms.selectById(9L)).thenReturn(target);
        var request=command();request.setSourceFormId(null);request.setSourceRevision(null);
        assertEquals("UNSENT",service.generate(4L,request).getStatus());
        var draft=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(target.getDraftSnapshot(),cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Draft.class);
        assertEquals("已有资料待核对",draft.getValues().get("name"));assertEquals(8L,task.getSourceFormId());assertNull(request.getSourceFormId());
        assertEquals(9L,service.generate(4L,request).getFormId());verify(collection,times(1)).issueTaskForm(target);
        verify(images).copyForCollection(eq(source),eq(target),anyList(),anyList());verify(answers,never()).insert(any(StudentInfoFormValueDO.class));
    }
    private StudentInfoFormDO submitted() {
        var form=new StudentInfoFormDO(); form.setId(8L); form.setPersonId(3L); form.setOrderItemId(2L); form.setStatus("SUBMITTED"); return form;
    }
    @Test void detailRejectsBrokenCustomerBinding() {
        var task=task();task.setFormId(8L);when(tasks.selectById(4L)).thenReturn(task);
        var form=submitted();form.setPersonId(99L);when(forms.selectById(8L)).thenReturn(form);
        assertThrows(ServiceException.class,()->service.information(4L,false));verifyNoInteractions(answers,images);
    }
    @Test void sensitiveReadFailsBeforeFetchingForm() {
        assertThrows(ServiceException.class,()->service.information(4L,true));verifyNoInteractions(tasks,forms);
    }
    @Test void archivePreviewRejectsAnOlderSourceAfterNewSubmission() {
        when(tasks.selectById(4L)).thenReturn(task());var current=submitted();current.setId(9L);
        when(forms.currentArchive(3L)).thenReturn(current);
        assertThrows(ServiceException.class,()->service.archive(4L,8L,0,false));
        verifyNoInteractions(answers,images);
    }
    @Test void reuseRejectsAnOlderArchiveWithoutCopyingAnything() {
        var task=task();when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("effective"));
        when(collection.prepareTaskForm(task)).thenReturn(new StudentInfoFormDO());
        var current=submitted();current.setId(9L);when(forms.currentArchive(3L)).thenReturn(current);
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));
        verify(forms,never()).lock(8L);verifyNoInteractions(images,answers);
    }
    @Test void completedTaskCannotBeOverwrittenByArchiveReuse() {
        var task=task();task.setFormId(8L);when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("effective"));when(forms.lock(8L)).thenReturn(submitted());
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));verifyNoInteractions(collection,images,answers);
    }
    @Test void reuseReplacesUnsubmittedTaskWithSnapshotAndPreservesOriginalDraft() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"reuse-test"),StudentInfoFormDO.class);
        var task=task();task.setFormId(7L);when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("effective"));when(orders.selectById(1L)).thenReturn(order("effective"));
        var old=submitted();old.setId(7L);old.setStatus("DRAFT");old.setFieldsSnapshot("frozen-contract");old.setDraftSnapshot("retained-draft");when(forms.lock(7L)).thenReturn(old);
        var source=submitted();when(forms.currentArchive(3L)).thenReturn(source);when(forms.lock(8L)).thenReturn(source);
        var field=new Field();field.setKey("name");field.setLabel("姓名");field.setType("text");field.setEnabled(true);field.setRequired(true);
        when(collection.definitions(any())).thenReturn(List.of(field));
        var answer=new StudentInfoFormValueDO();answer.setId(50L);answer.setFormId(8L);answer.setFieldKey("name");answer.setValueText("合成档案");when(answers.byForm(8L)).thenReturn(List.of(answer));
        var target=new java.util.concurrent.atomic.AtomicReference<StudentInfoFormDO>();
        doAnswer(call->{var form=(StudentInfoFormDO)call.getArgument(0);form.setId(9L);target.set(form);return null;}).when(collection).issueTaskForm(any());
        when(forms.selectById(9L)).thenAnswer(call->target.get());
        var row=service.reuse(4L,command());assertEquals("REUSED",row.getStatus());assertEquals(9L,task.getFormId());
        assertEquals("frozen-contract",target.get().getFieldsSnapshot());assertEquals("retained-draft",old.getDraftSnapshot());assertEquals(8L,answer.getFormId());
        var copy=ArgumentCaptor.forClass(StudentInfoFormValueDO.class);verify(answers).insert(copy.capture());assertEquals(9L,copy.getValue().getFormId());assertEquals("合成档案",copy.getValue().getValueText());
        verify(forms).update(isNull(),argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<StudentInfoFormDO> wrapper)->((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentInfoFormDO>)wrapper).getParamNameValuePairs().containsValue("REVOKED")));
    }
    @Test void draftPreviewIsReadOnlyAndMasksSensitiveFields() {
        var task=task();task.setFormId(8L);when(tasks.selectById(4L)).thenReturn(task);
        var form=submitted();form.setStatus("DRAFT");form.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        form.setDraftSnapshot("{\"values\":{\"mobile\":\"13800000000\"},\"selections\":{}}");
        when(forms.selectById(8L)).thenReturn(form);
        var field=new Field();field.setKey("mobile");field.setSensitive(true);when(collection.definitions(form)).thenReturn(List.of(field));
        assertEquals("已隐藏",service.information(4L,false).getValues().get("mobile"));
        when(collection.canReadSensitive()).thenReturn(true);
        assertEquals("13800000000",service.information(4L,true).getValues().get("mobile"));
        verify(forms,never()).updateById(any(StudentInfoFormDO.class));verifyNoInteractions(revisions,answers);
    }
    @Test void currentRevisionMasksValuesAndRejectsHistoricalAttachment() {
        var task=task();task.setFormId(8L);when(tasks.selectById(4L)).thenReturn(task);
        var form=submitted();when(forms.selectById(8L)).thenReturn(form);
        var revision=new StudentArchiveRevisionDO();revision.setAnswersJson("[{\"fieldKey\":\"mobile\",\"sensitive\":true,\"valueText\":\"13800000000\"}]");revision.setImageIdsJson("[]");
        when(revisions.latest(8L)).thenReturn(revision);
        assertNotEquals("13800000000",service.information(4L,false).getValues().get("mobile"));
        when(collection.canReadSensitive()).thenReturn(true);
        assertThrows(ServiceException.class,()->service.image(4L,99L));verify(images,never()).archiveRead(anyLong(),anyLong());
    }
    @Test void submittedFormCannotBeRotated() {
        var task=task();task.setFormId(8L);when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("effective"));
        var form=submitted();when(forms.selectById(8L)).thenReturn(form);when(forms.lock(8L)).thenReturn(form);
        assertThrows(ServiceException.class,()->service.regenerate(4L,command()));
        assertThrows(ServiceException.class,()->service.revoke(4L,command()));verify(collection,never()).rotateTaskForm(any());
    }
    @Test void repeatedApprovalCreatesOnlyMissingItemTasks() {
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("pending_approval"));
        var item=new SalesOrderItemDO();item.setId(2L);when(items.selectListByOrderId(1L)).thenReturn(List.of(item));
        when(tasks.byItem(2L)).thenReturn(null,task());service.ensureForOrder(1L);service.ensureForOrder(1L);
        verify(tasks,times(1)).insert(any(StudentInfoTaskDO.class));
    }
    @Test void terminatedOrderRejectsEveryStaffCommandBeforeFormAccess() {
        when(tasks.selectById(4L)).thenReturn(task());when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("terminated"));
        assertThrows(ServiceException.class,()->service.link(4L));assertThrows(ServiceException.class,()->service.generate(4L,command()));
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));assertThrows(ServiceException.class,()->service.sent(4L,command()));
        assertThrows(ServiceException.class,()->service.revoke(4L,command()));
        verifyNoInteractions(forms,collection,images);
    }
    @Test void anotherCustomersSourceIsRejectedBeforeCopy() {
        var task=task();when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("pending_approval"));
        when(collection.prepareTaskForm(task)).thenReturn(new StudentInfoFormDO());
        var source=new StudentInfoFormDO();source.setId(8L);source.setPersonId(99L);source.setStatus("SUBMITTED");when(forms.lock(8L)).thenReturn(source);when(forms.currentArchive(3L)).thenReturn(source);
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));verify(collection,never()).issueTaskForm(any());verifyNoInteractions(images);
    }
    @Test void missingEducationFieldRejectsDirectReuseBeforeAnyFormIsIssued() {
        var task=task();when(tasks.selectById(4L)).thenReturn(task);when(tasks.lock(4L)).thenReturn(task);
        when(orders.selectByIdForUpdate(1L,1L)).thenReturn(order("pending_approval"));
        var target=new StudentInfoFormDO();when(collection.prepareTaskForm(task)).thenReturn(target);
        var source=new StudentInfoFormDO();source.setId(8L);source.setPersonId(3L);source.setStatus("SUBMITTED");when(forms.lock(8L)).thenReturn(source);when(forms.currentArchive(3L)).thenReturn(source);
        var field=new Field();field.setKey("target_school");field.setLabel("拟报名院校");field.setType("text");field.setEnabled(true);field.setRequired(true);
        when(collection.definitions(target)).thenReturn(List.of(field));when(collection.definitions(source)).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.reuse(4L,command()));verify(collection,never()).issueTaskForm(any());verify(tasks,never()).updateById(any(StudentInfoTaskDO.class));
    }
}


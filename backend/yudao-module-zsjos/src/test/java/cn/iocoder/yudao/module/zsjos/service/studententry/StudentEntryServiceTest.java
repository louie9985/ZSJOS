package cn.iocoder.yudao.module.zsjos.service.studententry;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassOptionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studententry.vo.StudentEntryVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.collaboration.CollaborationGroupMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonContactClaimMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.registration.*;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactNotifyPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentEntryServiceTest {
    @InjectMocks StudentEntryService service;
    @Mock PermissionApi permissionApi; @Mock PostApi postApi; @Mock AdminUserApi userApi;
    @Mock PersonMapper personMapper; @Mock PersonContactClaimMapper claimMapper;
    @Mock PersonIdentityWriteService identities; @Mock ServiceRelationMapper relations;
    @Mock StudentServiceObjectPermissionProvider readPermission; @Mock DeliveryClassService classes;
    @Mock DeliveryClassMapper classMapper; @Mock ZsjosProductSkuService products;
    @Mock CollaborationGroupMapper groups; @Mock StudentEntryCommandMapper commands;
    @Mock BusinessEventMapper events; @Mock StudentContactNotifyPublisher notifications;
    @Mock StudentEntryPersonAccess personAccess;
    @BeforeEach void before() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void after() { TenantContextHolder.clear(); }
    Create request(String type, boolean teaching, Long classId) {
        return new Create(type, "录入姓名", "13800000000", null, null, "media".equals(type) ? 22L : null,
                teaching, classId, null, false, "entry-test");
    }
    void permitted() { when(permissionApi.hasAnyPermissions(9L, StudentEntryService.PERMISSION)).thenReturn(true); }
    void base(Create req, boolean formal) {
        permitted();
        var command = new StudentEntryCommandDO(); command.setId(3L);
        command.setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(req)));
        when(commands.lock(1L,9L,"entry-test")).thenReturn(command);
        var post = new PostRespDTO(); post.setId(5L); post.setStatus(0); when(postApi.getPostByCode("content_director")).thenReturn(post);
        var director = new AdminUserRespDTO(); director.setId(22L); director.setNickname("有效编导"); director.setStatus(0);
        when(userApi.getUserListByPostIds(Set.of(5L))).thenReturn(List.of(director));
        when(products.getLeadCatalog()).thenReturn(new LeadProductCatalogRespVO(List.of(),List.of(),List.of()));
        var option = new DeliveryClassOptionRespVO(); option.setId(8L); option.setClassKind(formal ? "NORMAL" : "PENDING"); option.setSystemClass(!formal); option.setHomeroomUserId(formal ? 33L : null);
        when(classes.options(null,true)).thenReturn(List.of(option));
        lenient().when(classMapper.selectByIdForUpdate(8L,1L)).thenAnswer(i -> {
            var row = new DeliveryClassDO(); row.setId(8L); row.setClassKind(formal ? "NORMAL" : "PENDING");
            row.setStatus("SERVING"); row.setSystemClass(!formal); row.setHomeroomUserId(formal ? 33L : null); return row;
        });
        var owner = new AdminUserRespDTO(); owner.setId(33L);
        lenient().when(classes.validateHomeroom(33L)).thenReturn(owner);
        var person = new PersonDO(); person.setId(11L); person.setPersonNo("XY-TEST"); person.setMobile("13800000000"); person.setIdentityStatus("student");
        lenient().when(identities.createReserved(anyString(),anyString(),isNull(),eq("student"),eq("student-entry:3"))).thenReturn(person);
        lenient().when(personMapper.selectById(11L)).thenReturn(person);
        lenient().doAnswer(i -> { i.getArgument(0,ServiceRelationDO.class).setId(44L); return 1; }).when(relations).insert(any(ServiceRelationDO.class));
    }
    @Test void mediaOnlyStartsImmediatelyWithoutTeachingOrOrders() {
        var req=request("media",false,null);base(req,false); var result=service.create(9L,req);
        var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());var row=capture.getValue();
        assertTrue(MediaServiceReadiness.ready(row)); assertFalse(row.getTeachingDelivery()); assertNull(row.getClassId());
        assertEquals("pending",row.getAcceptanceStatus());assertNull(row.getOrderId());assertNull(row.getOrderItemId());assertNull(row.getRegistrationCaseId());
        assertEquals("录入姓名",row.getStudentNameSnapshot()); assertEquals("independent_entry",row.getStudentNameSource());
        assertEquals(44L,result.serviceRelationId());assertFalse(result.canRead());verify(groups).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.collaboration.CollaborationGroupDO.class));
        verify(notifications).publish(eq(RegistrationConstants.NOTIFY_SCENE_DIRECTOR_ASSIGNED),eq(44L),anyString(),isNull(),any(),any());
        verify(notifications,never()).publish(eq(RegistrationConstants.NOTIFY_SCENE_PLANNER_ASSIGNED),any(),anyString(),any(),any(),any());
    }
    @Test void sharedMediaIsReadyWhileTeachingWaitsInPendingClass() {
        var req=request("media",true,null);base(req,false);service.create(9L,req);
        var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());var row=capture.getValue();
        assertTrue(row.getTeachingDelivery());assertTrue(row.getMediaReady());assertEquals(8L,row.getClassId());assertNull(row.getOwnerUserId());assertEquals("pending",row.getAcceptanceStatus());
    }
    @Test void teachingFormalClassUsesHomeroomAndAwaitsAcceptance() {
        var req=request("teaching",true,8L);base(req,true);service.create(9L,req);
        var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());var row=capture.getValue();
        assertTrue(row.getTeachingDelivery());assertFalse(row.getMediaReady());assertNull(row.getContentDirectorUserId());assertEquals(33L,row.getOwnerUserId());assertNull(row.getServiceSnapshot());
        assertEquals("pending",row.getAcceptanceStatus());verify(notifications).publish(eq(RegistrationConstants.NOTIFY_SCENE_PLANNER_ASSIGNED),eq(44L),anyString(),isNull(),any(),any());
    }
    @Test void teachingPendingHasNoOwner() {
        var req=request("teaching",true,8L);base(req,false);service.create(9L,req);
        var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());assertNull(capture.getValue().getOwnerUserId());
        verifyNoInteractions(notifications);
    }
    @Test void noCreatePermissionDoesNotReadOrWriteBusinessData() {
        assertThrows(RuntimeException.class,()->service.create(9L,request("media",false,null)));
        verifyNoInteractions(commands,personMapper,relations,products);
    }
    @Test void reusedCommandWithDifferentRequestFailsBeforeOptionsOrIdentityReads() {
        permitted();var command=new StudentEntryCommandDO();command.setRequestHash("other");when(commands.lock(anyLong(),anyLong(),anyString())).thenReturn(command);
        assertThrows(RuntimeException.class,()->service.create(9L,request("media",false,null)));verifyNoInteractions(products,identities,relations);
    }
    @Test void occupiedContactDoesNotDisclosePersonOrServices() {
        permitted();var person=new PersonDO();person.setId(11L);when(personMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(person));
        var check=service.check(9L,new Contact("13800000000",null));assertEquals("occupied",check.status());assertNull(check.personId());assertNull(check.name());assertTrue(check.services().isEmpty());
    }
    @Test void contactsMatchingDifferentPeopleFailClosed() {
        permitted();var one=new PersonDO();one.setId(11L);var two=new PersonDO();two.setId(12L);
        when(personMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(one,two));
        assertEquals("conflict",service.check(9L,new Contact("13800000000","wechat")).status());verifyNoInteractions(personAccess);
    }
    @Test void legacyMediaUsesAcceptanceAndExplicitMediaNeverAcceptsTeaching() {
        var row=new ServiceRelationDO();row.setAcceptanceStatus("pending");assertFalse(MediaServiceReadiness.ready(row));
        row.setAcceptanceStatus("accepted");assertTrue(MediaServiceReadiness.ready(row));row.setMediaReady(false);assertFalse(MediaServiceReadiness.ready(row));
        row.setMediaReady(true);row.setAcceptanceStatus("pending");assertTrue(MediaServiceReadiness.ready(row));assertEquals("pending",row.getAcceptanceStatus());
    }
    @Test void sameCommandReplaysWithoutAnotherServiceOrNewCandidateValidation() {
        permitted();var req=request("media",false,null);var command=new StudentEntryCommandDO();
        command.setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(req)));
        command.setPersonId(11L);command.setServiceRelationId(44L);when(commands.lock(1L,9L,"entry-test")).thenReturn(command);
        var person=new PersonDO();person.setPersonNo("XY-ORIGINAL");lenient().when(personMapper.selectById(11L)).thenReturn(person);
        assertEquals(44L,service.create(9L,req).serviceRelationId());verifyNoInteractions(products,identities,groups,events,notifications);
        verify(relations,never()).insert(any(ServiceRelationDO.class));
    }
    @Test void reusedIdentityIsExplicitAndKeepsOriginalProfileAndServiceName() {
        var req=new Create("media","新的服务姓名","13800000000",null,null,22L,false,null,11L,true,"entry-test");base(req,false);
        var person=new PersonDO();person.setId(11L);person.setName("原档案姓名");person.setMobile("13800000000");person.setIdentityStatus("student");
        when(personMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(person));
        when(personMapper.selectByIdForUpdate(11L,1L)).thenReturn(person);
        service.create(9L,req);verify(personAccess).checkReuse(11L,9L);
        verify(claimMapper).bindReservations(1L,"student-entry:3",11L);
        assertEquals("原档案姓名",person.getName());verify(personMapper,never()).updateById(any(PersonDO.class));verifyNoInteractions(identities);
        var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());assertEquals("新的服务姓名",capture.getValue().getStudentNameSnapshot());
    }
    @Test void staleReuseOrMissingConfirmationCannotCreateAnotherService() {
        var req=request("media",false,null);base(req,false);var person=new PersonDO();person.setId(11L);
        when(personMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(person));
        assertThrows(RuntimeException.class,()->service.create(9L,req));verify(relations,never()).insert(any(ServiceRelationDO.class));verifyNoInteractions(identities,groups,events);
    }
    @Test void contactsAreMandatoryEvenWithForgedPersonId() {
        permitted();var req=new Create("media","姓名",null," ",null,22L,false,null,11L,true,"entry-test");
        assertThrows(RuntimeException.class,()->service.create(9L,req));verifyNoInteractions(commands,personAccess,identities);
    }
    @Test void mediaReadinessAllowsDirectorButNeverTeachingContactsBeforeAcceptance() {
        var row=new ServiceRelationDO();row.setId(44L);row.setPersonId(11L);row.setStatus("active");row.setTeachingDelivery(true);
        row.setMediaReady(true);row.setAcceptanceStatus("pending");row.setContentDirectorUserId(22L);
        var provider=new StudentServiceObjectPermissionProvider();
        org.springframework.test.util.ReflectionTestUtils.setField(provider,"relationMapper",relations);
        org.springframework.test.util.ReflectionTestUtils.setField(provider,"permissionApi",permissionApi);
        when(relations.selectById(44L)).thenReturn(row);
        assertTrue(provider.hasPermission(44L,"director-precheck",22L));assertTrue(provider.hasPermission(44L,"read",22L));
        assertFalse(provider.hasPermission(44L,"accept",22L));assertFalse(provider.hasPermission(44L,"contact",22L));
        row.setMediaReady(false);row.setAcceptanceStatus("accepted");row.setCareerPlannerUserId(55L);
        assertTrue(provider.hasPermission(44L,"read",55L));
    }
    @Test void collaboratorPredicateBindsActorWithoutOpeningOtherServices() {
        var query = new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ServiceRelationDO>();
        query.apply(MediaServiceReadiness.COLLABORATOR_SQL,22L);
        String sql = query.getSqlSegment();
        assertFalse(sql.contains("{0}"));assertTrue(sql.contains("career_planner_user_id"));
        assertTrue(sql.contains("media_ready"));assertTrue(query.getParamNameValuePairs().containsValue(22L));
    }
    @Test void selectedSkuUsesServerSnapshotAndStillCreatesNoOrder() {
        var req=new Create("media","姓名","13800000000",null,"sku-one",22L,false,null,null,false,"entry-test");base(req,false);
        var sku=new LeadProductCatalogRespVO.Sku("product-one","sku-one","规格",Map.of(),java.math.BigDecimal.TEN);
        when(products.getLeadCatalog()).thenReturn(new LeadProductCatalogRespVO(List.of(),List.of(),List.of(sku)));
        when(products.validateLeadProduct("product-one",false,"sku-one",false)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot("product-one","服务端课程",null,null,List.of(),null,null,null,null,"sku-one","规格","{}",java.math.BigDecimal.TEN,false,false));
        service.create(9L,req);var capture=ArgumentCaptor.forClass(ServiceRelationDO.class);verify(relations).insert(capture.capture());
        assertTrue(capture.getValue().getServiceSnapshot().contains("服务端课程"));assertNull(capture.getValue().getOrderId());
    }
    @Test void unknownOrDisabledSkuDoesNotCreatePersonOrService() {
        var req=new Create("media","姓名","13800000000",null,"disabled",22L,false,null,null,false,"entry-test");base(req,false);
        assertThrows(RuntimeException.class,()->service.create(9L,req));verifyNoInteractions(identities,groups,events);verify(relations,never()).insert(any(ServiceRelationDO.class));
    }
}

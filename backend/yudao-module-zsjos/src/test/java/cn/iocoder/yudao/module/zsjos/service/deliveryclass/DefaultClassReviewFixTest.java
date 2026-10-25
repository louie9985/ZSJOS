package cn.iocoder.yudao.module.zsjos.service.deliveryclass;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DefaultClassReviewFixTest {
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"fixture"),DeliveryClassDO.class);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void clearingDepartmentWritesNullForBothOwnedContainers() throws Exception {
  for(var type:List.of(UnscheduledDeliveryClassService.class,ExceptionDeliveryClassService.class)){
   var service=type.getConstructor().newInstance();var mapper=mock(DeliveryClassMapper.class);var qualification=mock(DeliveryClassService.class);
   ReflectionTestUtils.setField(service,"classes",mapper);ReflectionTestUtils.setField(service,"classService",qualification);ReflectionTestUtils.setField(service,"depts",mock(DeptApi.class));
   when(qualification.validateHomeroom(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setNickname("name"));
   var row=new DeliveryClassDO().setId(1L).setVersion(2).setDeptId(80L).setDeptNameSnapshot("old").setHomeroomUserNameSnapshot("name");
   when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
   when(mapper.update(isNull(),any(Wrapper.class))).thenAnswer(call->{LambdaUpdateWrapper<DeliveryClassDO> sql=call.getArgument(1);assertTrue(sql.getSqlSet().contains("dept_id="));assertTrue(sql.getSqlSet().contains("dept_name_snapshot="));assertTrue(sql.getParamNameValuePairs().containsValue(null));return 1;});
   ReflectionTestUtils.invokeMethod(service,"ensure",20L);assertNull(row.getDeptId());assertNull(row.getDeptNameSnapshot());
  }
 }
 @Test void exceptionCreateRaceUsesCurrentRead(){
  var service=new ExceptionDeliveryClassService();var mapper=mock(DeliveryClassMapper.class);var qualification=mock(DeliveryClassService.class);
  ReflectionTestUtils.setField(service,"classes",mapper);ReflectionTestUtils.setField(service,"classService",qualification);ReflectionTestUtils.setField(service,"depts",mock(DeptApi.class));
  when(qualification.validateHomeroom(20L)).thenReturn(new AdminUserRespDTO().setId(20L));
  when(mapper.insert(any(DeliveryClassDO.class))).thenThrow(new DuplicateKeyException("synthetic race"));
  var winner=new DeliveryClassDO().setId(99L);when(mapper.selectPlannerDefaultForUpdate("EXCEPTION",20L,1L)).thenReturn(winner);
  assertSame(winner,service.ensure(20L));verify(mapper,times(1)).selectOne(any(Wrapper.class));
 }
 @Test void onePlannerFailureDoesNotAbortReconciliation(){
  var service=spy(new ExceptionDeliveryClassService());var users=mock(AdminUserApi.class);var roles=mock(RoleApi.class);var permissions=mock(PermissionApi.class);
  ReflectionTestUtils.setField(service,"users",users);ReflectionTestUtils.setField(service,"roles",roles);ReflectionTestUtils.setField(service,"permissions",permissions);
  when(roles.getRoleByCode("study_planner")).thenReturn(new RoleRespDTO().setId(1L).setStatus(0));
  when(permissions.getUserRoleIdListByRoleIds(Set.of(1L))).thenReturn(new LinkedHashSet<>(List.of(20L,21L)));
  when(users.getUser(anyLong())).thenAnswer(c->new AdminUserRespDTO().setId(c.getArgument(0)).setStatus(0));
  doThrow(new IllegalStateException("synthetic failure")).when(service).ensure(20L);doReturn(new DeliveryClassDO()).when(service).ensure(21L);
  assertDoesNotThrow(service::reconcile);verify(service).ensure(21L);
 }
 @Test void assignablePageFiltersRetiredPlannerBeforePaging(){
  var service=new DeliveryClassServiceImpl();var mapper=mock(DeliveryClassMapper.class,CALLS_REAL_METHODS);var permissions=mock(PermissionApi.class);var roles=mock(RoleApi.class);var users=mock(AdminUserApi.class);
  ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"permissionApi",permissions);ReflectionTestUtils.setField(service,"roleApi",roles);ReflectionTestUtils.setField(service,"adminUserApi",users);
  when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);when(roles.getRoleByCode("study_planner")).thenReturn(new RoleRespDTO().setId(1L).setStatus(0));
  when(permissions.getUserRoleIdListByRoleIds(Set.of(1L))).thenReturn(Set.of(20L,21L));when(users.getUserList(anyCollection())).thenReturn(List.of(new AdminUserRespDTO().setId(20L).setStatus(0),new AdminUserRespDTO().setId(21L).setStatus(1)));
  var request=new DeliveryClassPageReqVO();request.setAssignableOnly(true);var query=service.scopedQuery(1L,request,true);
  String sql=query.getSqlSegment();assertTrue(sql.contains("homeroom_user_id IN"));assertTrue(query.getParamNameValuePairs().containsValue(20L));assertFalse(query.getParamNameValuePairs().containsValue(21L));
 }
 @Test void oversizedGroupKeyReturnsDomainError(){
  var service=new DeliveryClassGroupService();var classService=mock(DeliveryClassServiceImpl.class);
  ReflectionTestUtils.setField(service,"classService",classService);
  when(classService.scopedQuery(anyLong(),any(),anyBoolean())).thenReturn(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<DeliveryClassDO>());
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.children(1L,"group:999999999999999999999",new DeliveryClassPageReqVO(),true));
  assertEquals(DeliveryClassGroupErrors.INVALID.getCode(),error.getCode());
 }

}

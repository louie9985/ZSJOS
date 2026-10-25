package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassGroupVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassGroupErrors.*;

class DeliveryClassGroupServiceTest {
    private JdbcTemplate jdbc;
    private DeliveryClassGroupService service;
    private DeliveryClassGroupMapper groups;
    private DeliveryClassMapper classes;
    private DeliveryClassGroupPermissionProvider auth;
    private DeliveryClassObjectPermissionProvider classAuth;
    private DeliveryClassService commands;
    private DeliveryClassServiceImpl reader;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var data=new DriverManagerDataSource("jdbc:h2:mem:group_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE zsjos_delivery_class_group(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100),dept_id BIGINT,dept_name_snapshot VARCHAR,reference_date DATE,version INT DEFAULT 0,tenant_id BIGINT DEFAULT 1,deleted INT DEFAULT 0,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE zsjos_delivery_class(id BIGINT PRIMARY KEY,class_name VARCHAR(100),class_no VARCHAR(40),group_id BIGINT,dept_id BIGINT,homeroom_user_id BIGINT,homeroom_user_name_snapshot VARCHAR,system_class BOOLEAN,class_kind VARCHAR,status VARCHAR,exam_schedule_snapshot VARCHAR,version INT DEFAULT 0,tenant_id BIGINT DEFAULT 1,deleted INT DEFAULT 0,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE events(class_id BIGINT,source_id BIGINT,target_id BIGINT)");
        var config=new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config,""),DeliveryClassDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config,""),DeliveryClassGroupDO.class);
        groups=mock(DeliveryClassGroupMapper.class);classes=mock(DeliveryClassMapper.class);
        when(groups.selectList()).thenAnswer(c -> groupRows());
        when(groups.selectById(anyLong())).thenAnswer(c -> group(c.getArgument(0)));
        when(groups.lock(anyLong(),anyLong())).thenAnswer(c -> group(c.getArgument(0)));
        when(groups.insert(any(DeliveryClassGroupDO.class))).thenAnswer(c -> {
            DeliveryClassGroupDO g=c.getArgument(0);
            jdbc.update("INSERT INTO zsjos_delivery_class_group(name,dept_id,reference_date) VALUES(?,?,?)",g.getName(),g.getDeptId(),g.getReferenceDate());
            g.setId(jdbc.queryForObject("SELECT MAX(id) FROM zsjos_delivery_class_group",Long.class));return 1;
        });
        when(classes.selectList(any(Wrapper.class))).thenAnswer(c -> selectClasses(c.getArgument(0)));
        when(classes.selectByIdForUpdate(anyLong(),anyLong())).thenAnswer(c -> row(c.getArgument(0)));
        when(classes.selectById(anyLong())).thenAnswer(c -> row(c.getArgument(0)));
        when(classes.selectCount(any(Wrapper.class))).thenAnswer(c -> (long)selectClasses(c.getArgument(0)).size());
        when(classes.update(isNull(),any(Wrapper.class))).thenAnswer(c -> update("zsjos_delivery_class",c.getArgument(1)));
        when(groups.update(isNull(),any(Wrapper.class))).thenAnswer(c -> update("zsjos_delivery_class_group",c.getArgument(1)));
        when(groups.deleteById(anyLong())).thenAnswer(c -> jdbc.update("UPDATE zsjos_delivery_class_group SET deleted=1 WHERE id=?",(Long)c.getArgument(0)));
        var events=mock(DeliveryClassMembershipEventMapper.class);
        when(events.insert(any(DeliveryClassMembershipEventDO.class))).thenAnswer(c -> { DeliveryClassMembershipEventDO e=c.getArgument(0);return jdbc.update("INSERT INTO events VALUES(?,?,?)",e.getClassId(),e.getSourceGroupId(),e.getTargetGroupId()); });
        reader=mock(DeliveryClassServiceImpl.class);
        when(reader.scopedQuery(anyLong(),any(),anyBoolean())).thenAnswer(c -> {
            DeliveryClassPageReqVO req=c.getArgument(1);
            var q=new LambdaQueryWrapperX<DeliveryClassDO>().eqIfPresent(DeliveryClassDO::getStatus,req.getStatus())
                    .eqIfPresent(DeliveryClassDO::getHomeroomUserId,req.getHomeroomUserId());
            if(!(boolean)c.getArgument(2))q.eq(DeliveryClassDO::getHomeroomUserId,9L);
            return q.orderByDesc(DeliveryClassDO::getId);
        });
        when(reader.toPageResult(any(), any())).thenAnswer(c -> {
            PageResult<DeliveryClassDO> p=c.getArgument(0);
            return new PageResult<>(p.getList().stream().map(r -> { var v=BeanUtils.toBean(r,DeliveryClassRespVO.class);v.setStudentCount(2);var d=DeliveryClassDates.fromSnapshot(r.getExamScheduleSnapshot());if(d!=null){v.setStartDate(d.start());v.setEndDate(d.end());}return v; }).toList(),p.getTotal());
        });
        auth=mock(DeliveryClassGroupPermissionProvider.class);when(auth.canMaintain(any(),anyLong(),anyString())).thenReturn(true);
        when(auth.maintenanceContext(anyLong())).thenReturn(new DeliveryClassGroupPermissionProvider.MaintainContext(80L,new DeliveryClassScopeService.Scope(false,Set.of(80L)),true,true,true));
        classAuth=mock(DeliveryClassObjectPermissionProvider.class);
        commands=mock(DeliveryClassService.class);
        var users=mock(AdminUserApi.class);var user=new AdminUserRespDTO();user.setId(9L);user.setDeptId(80L);when(users.getUser(9L)).thenReturn(user);
        var target=new DeliveryClassGroupService();
        Object[][] fields={{"groups",groups},{"classes",classes},{"events",events},{"classService",reader},{"classCommands",commands},{"authorization",auth},{"classAuthorization",classAuth},{"users",users},{"departments",mock(DeptApi.class)}};
        for(var f:fields)ReflectionTestUtils.setField(target,(String)f[0],f[1]);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));
        service=(DeliveryClassGroupService)proxy.getProxy();
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    private List<DeliveryClassGroupDO> groupRows(){return jdbc.query("SELECT * FROM zsjos_delivery_class_group WHERE deleted=0",new BeanPropertyRowMapper<>(DeliveryClassGroupDO.class));}
    private DeliveryClassGroupDO group(Long id){return groupRows().stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);}
    private DeliveryClassDO row(Long id){return jdbc.query("SELECT * FROM zsjos_delivery_class WHERE id=?",new BeanPropertyRowMapper<>(DeliveryClassDO.class),id).stream().findFirst().orElse(null);}
    private record Bound(String sql,Object[] params){}
    private Bound bind(String sql,AbstractWrapper<?,?,?> wrapper){
        var pattern=Pattern.compile("#\\{ew.paramNameValuePairs\\.(\\w+)}");var matcher=pattern.matcher(sql);List<Object> args=new ArrayList<>();
        while(matcher.find())args.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
        return new Bound(matcher.replaceAll("?"),args.toArray());
    }
    private List<DeliveryClassDO> selectClasses(AbstractWrapper<?,?,?> wrapper){
        var q=bind("SELECT * FROM (SELECT * FROM zsjos_delivery_class WHERE tenant_id=1 AND deleted=0) zsjos_delivery_class "+wrapper.getCustomSqlSegment(),wrapper);
        return jdbc.query(q.sql(),new BeanPropertyRowMapper<>(DeliveryClassDO.class),q.params());
    }
    private int update(String table,AbstractWrapper<?,?,?> wrapper){var q=bind("UPDATE "+table+" SET "+wrapper.getSqlSet()+" "+wrapper.getCustomSqlSegment(),wrapper);return jdbc.update(q.sql(),q.params());}
    private void addGroup(long id,String name){jdbc.update("INSERT INTO zsjos_delivery_class_group(id,name,dept_id) VALUES(?,?,80)",id,name);}
    private void addClass(long id,Long group,String kind,long owner){jdbc.update("INSERT INTO zsjos_delivery_class(id,class_no,class_name,group_id,dept_id,homeroom_user_id,system_class,class_kind,status,exam_schedule_snapshot) VALUES(?,?,?,?,80,?,?,?,'SERVING','2026-12-01')",id,"BJ"+id,"同名测试",group,owner,!kind.equals("NORMAL"),kind);}
    private DeliveryClassPageReqVO request(int page,int size){var r=new DeliveryClassPageReqVO();r.setPageNo(page);r.setPageSize(size);return r;}
    private Move move(Long target,long... ids){var m=new Move();m.setTargetGroupId(target);m.setClasses(Arrays.stream(ids).mapToObj(id -> {var e=new Member();e.setId(id);e.setVersion(0);return e;}).toList());return m;}

    @Test void groupIsPagedAsOneRootAndSummariesIncludeAllMatchingChildren(){
        addGroup(1,"同名组");for(long id=1;id<=25;id++)addClass(id,1L,"NORMAL",9);
        addClass(30,null,"NORMAL",9);addClass(40,null,"UNSCHEDULED",9);addClass(41,null,"EXCEPTION",9);addClass(42,null,"PENDING",9);
        var first=service.page(9L,request(1,2),true);assertEquals(5,first.getTotal());assertEquals(29,first.getClassTotal());
        assertEquals(List.of("class:42","system:UNSCHEDULED"),first.getList().stream().map(Node::getKey).toList());
        var second=service.page(9L,request(2,2),true);var node=second.getList().stream().filter(n -> "GROUP".equals(n.getType())).findFirst().orElseThrow();
        assertEquals(25,node.getClassCount());assertEquals(50,node.getServiceCount());assertEquals("2026-12-01",node.getExamDate());
    }
    @Test void personalScopeDoesNotRevealSiblingCountsAndKeywordSearchesFrozenGroupName(){
        addGroup(1,"父级名称");addClass(1,1L,"NORMAL",9);addClass(2,1L,"NORMAL",10);
        var req=request(1,12);req.setKeyword("父级名称");var page=service.page(9L,req,false);
        assertEquals(1,page.getClassTotal());assertEquals(2,page.getList().getFirst().getServiceCount());assertFalse(page.getList().getFirst().isAutoExpand());
        req.setKeyword("BJ1");page=service.page(9L,req,false);assertTrue(page.getList().getFirst().isAutoExpand());
        req.setKeyword("不存在");assertEquals(0,service.page(9L,req,false).getTotal());
    }
    @Test void emptyGroupsAreVisibleOnlyToMaintainersWithoutChildFilters(){
        addGroup(1,"空组");assertEquals(1,service.page(9L,request(1,12),true).getTotal());
        assertEquals(0,service.page(9L,request(1,12),false).getTotal());
        var req=request(1,12);req.setHomeroomUserId(9L);assertEquals(0,service.page(9L,req,true).getTotal());
        when(auth.maintenanceContext(anyLong())).thenReturn(new DeliveryClassGroupPermissionProvider.MaintainContext(80L,new DeliveryClassScopeService.Scope(false,Set.of(80L)),false,false,false));assertEquals(0,service.page(9L,request(1,12),true).getTotal());
    }
    @Test void mixedDatesAndStatusesAreDerivedFromActualChildren(){
        addGroup(1,"参考日期不同");addClass(1,1L,"NORMAL",9);addClass(2,1L,"NORMAL",10);
        jdbc.update("UPDATE zsjos_delivery_class SET status='COMPLETED',exam_schedule_snapshot='2027-01-02' WHERE id=2");
        var node=service.page(9L,request(1,12),true).getList().getFirst();assertEquals("多个考期",node.getExamDate());assertEquals(1,node.getServingCount());assertEquals(1,node.getCompletedCount());
    }
    @Test void moveChangesOnlyMembershipAndVersionAndKeepsHistoricalAudit(){
        addGroup(1,"原组");addGroup(2,"目标组");addClass(1,1L,"NORMAL",9);jdbc.update("UPDATE zsjos_delivery_class SET status='COMPLETED' WHERE id=1");
        service.move(move(2L,1),9L);assertEquals(2,row(1L).getGroupId());assertEquals(1,row(1L).getVersion());assertEquals("COMPLETED",row(1L).getStatus());assertEquals(9,row(1L).getHomeroomUserId());
        assertEquals("1:2",jdbc.queryForObject("SELECT CONCAT(source_id,':',target_id) FROM events",String.class));
        var out=move(null,1);out.getClasses().getFirst().setVersion(1);service.move(out,9L);assertNull(row(1L).getGroupId());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
    }
    @Test void unauthorizedSecondClassRollsBackFirstMoveAndAudit(){
        addGroup(1,"组");addClass(1,null,"NORMAL",9);addClass(2,null,"NORMAL",9);
        doThrow(exception(DENIED)).when(classAuth).check(2L,"update",9L);
        assertThrows(RuntimeException.class,() -> service.move(move(1L,1,2),9L));assertNull(row(1L).getGroupId());assertEquals(0,row(1L).getVersion());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
    }
    @Test void staleVersionCrossDepartmentAndSystemClassAreRejectedAtomically(){
        addGroup(1,"目标组");addClass(1,null,"NORMAL",9);var stale=move(1L,1);stale.getClasses().getFirst().setVersion(8);
        assertThrows(RuntimeException.class,() -> service.move(stale,9L));
        jdbc.update("UPDATE zsjos_delivery_class_group SET dept_id=90 WHERE id=1");assertThrows(RuntimeException.class,() -> service.move(move(1L,1),9L));
        addClass(2,null,"UNSCHEDULED",9);assertThrows(RuntimeException.class,() -> service.move(move(null,2),9L));
        assertNull(row(1L).getGroupId());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
    }
    @Test void deleteRequiresActuallyEmptyGroupAndVersion(){
        addGroup(1,"组");addClass(1,1L,"NORMAL",9);assertThrows(RuntimeException.class,() -> service.delete(1L,0,9L));
        jdbc.update("UPDATE zsjos_delivery_class SET group_id=NULL WHERE id=1");assertThrows(RuntimeException.class,() -> service.delete(1L,1,9L));service.delete(1L,0,9L);assertNull(group(1L));
    }
    @Test void groupEditDoesNotChangeChildrenAndCanClearReferenceDate(){
        addGroup(1,"旧名");addClass(1,1L,"NORMAL",9);var req=new Save();req.setName("新名");req.setVersion(0);
        service.update(1L,req,9L);assertEquals("新名",group(1L).getName());assertNull(group(1L).getReferenceDate());assertEquals("2026-12-01",row(1L).getExamScheduleSnapshot());assertEquals(0,row(1L).getVersion());
    }
    @Test void creatingGroupAndBatchIsOneTransactionOnFailure(){
        var req=new CreateClasses();req.setGroupMode("NEW");req.setClassName("小班");req.setStartDate(LocalDate.of(2026,12,1));req.setHomeroomUserIds(List.of(9L));var g=new Save();g.setName("父级");req.setNewGroup(g);
        when(commands.createBatch(any(),anyLong())).thenAnswer(c -> {addClass(1,null,"NORMAL",9);throw exception(CONFLICT);});
        assertThrows(RuntimeException.class,() -> service.createClasses(req,9L));assertEquals(0,groupRows().size());assertNull(row(1L));
    }
    @Test void combinedCreateAssignsAllIndependentClassesAndLeavesLegacyCommandUnchanged(){
        var req=new CreateClasses();req.setGroupMode("NEW");req.setClassName("小班");req.setStartDate(LocalDate.of(2026,12,1));req.setHomeroomUserIds(List.of(9L,10L));var g=new Save();g.setName("父级");req.setNewGroup(g);
        when(commands.createBatch(any(),anyLong())).thenAnswer(c -> {addClass(1,null,"NORMAL",9);addClass(2,null,"NORMAL",10);return List.of(1L,2L);});
        var created=service.createClasses(req,9L);assertEquals(List.of(1L,2L),created.classIds());assertEquals(created.groupId(),row(1L).getGroupId());assertEquals(created.groupId(),row(2L).getGroupId());assertEquals(0,row(1L).getVersion());
    }
}

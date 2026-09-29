package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaStudentFilterQueryTest {
    private final AdvancedFilterService service = new AdvancedFilterService();
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ServiceRelationMapper relations = mock(ServiceRelationMapper.class);
    private SqlSession session;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var source = new DriverManagerDataSource("jdbc:h2:mem:mediaFilter" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(AdvancedFilterMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zsjos_media_account(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT,student_person_id BIGINT,owner_operator_user_id BIGINT,director_user_id BIGINT,create_service_relation_id BIGINT,run_status VARCHAR(40),platform_value VARCHAR(80),current_status_value VARCHAR(80))");
        // Person 100 has two accounts: neither is operator 20 AND platform dy.
        account(1,1,0,100,20L,10L,null,"wx","good");
        account(2,1,0,100,21L,10L,null,"dy","good");
        account(3,1,0,101,20L,10L,null,"dy","good");
        account(4,1,0,101,20L,10L,null,"dy","good");
        account(5,1,0,102,20L,99L,null,"dy","good"); // not readable
        account(6,2,0,103,20L,10L,null,"dy","good"); // foreign tenant
        account(7,1,1,104,20L,10L,null,"dy","good"); // deleted
        account(8,1,0,105,null,10L,null,"wx",null);
        var provider = new MediaAccountObjectPermissionProvider();
        ReflectionTestUtils.setField(provider,"permissionApi",permissions);
        ReflectionTestUtils.setField(provider,"relationMapper",relations);
        ReflectionTestUtils.setField(service,"mapper",session.getMapper(AdvancedFilterMapper.class));
        ReflectionTestUtils.setField(service,"accountPermissions",provider);
    }
    @AfterEach void close() { session.close(); TenantContextHolder.clear(); }
    private void account(long id,long tenant,int deleted,long person,Long owner,Long director,Long relation,String platform,String status) {
        jdbc.update("INSERT INTO zsjos_media_account VALUES(?,?,?,?,?,?,?,'active',?,?)",id,tenant,deleted,person,owner,director,relation,platform,status);
    }
    private static AdvancedFilterConditionReqVO condition(String key,String op,Object value) {
        var c=new AdvancedFilterConditionReqVO(); c.setFieldKey("mediaAccount."+key); c.setOperator(op); c.setValue(value); return c;
    }
    private static AdvancedFilterGroupReqVO group(String logic,AdvancedFilterConditionReqVO... conditions) {
        var g=new AdvancedFilterGroupReqVO(); g.setLogic(logic); g.setConditions(List.of(conditions)); return g;
    }
    private Set<Long> match(AdvancedFilterGroupReqVO g) { return new HashSet<>(service.matchMediaStudentPersonIds(g,10L)); }

    @Test void sameAccountAndDeduplicationTenantDeletionAndAuthorization() {
        var g=group("AND",condition("ownerOperatorUserId","in",List.of("20")),condition("platform","in",List.of("dy")));
        assertEquals(Set.of(101L),match(g));
        assertEquals(Set.of(20L,21L),service.mediaStudentOperatorIds(10L));
        when(permissions.hasAnyPermissions(10L,"zsjos:media-account:query-all")).thenReturn(true);
        assertEquals(Set.of(101L,102L),match(g));
        assertNull(service.matchMediaStudentPersonIds(null,10L));
        assertNull(service.matchMediaStudentPersonIds(new AdvancedFilterGroupReqVO(),10L));
    }
    @Test void nestedOrMultiselectEmptyAndNegativeRemainOnOneVisibleAccount() {
        var g=group("AND",condition("ownerOperatorUserId","in",List.of("20","21")));
        g.setGroups(List.of(group("OR",condition("platform","in",List.of("wx")),condition("currentStatus","is_empty",null))));
        assertEquals(Set.of(100L),match(g));
        assertEquals(Set.of(105L),match(group("AND",condition("currentStatus","is_empty",null))));
        assertEquals(Set.of(100L,105L),match(group("AND",condition("platform","not_in",List.of("dy")))));
        assertTrue(match(group("AND",condition("platform","in",List.of("x' OR 1=1 --")))).isEmpty());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> match(group("AND",condition("unknown","in",List.of("20")))));
    }
    @Test void sourceRelationPermissionMatchesExistingProviderIncludingReadAll() {
        account(9,1,0,106,20L,10L,30L,"dy","good");
        var r=new ServiceRelationDO(); r.setId(30L); r.setPersonId(106L); r.setTenantId(1L);
        r.setStatus("active"); r.setAcceptanceStatus("accepted"); r.setContentDirectorUserId(10L);
        when(relations.selectByIds(List.of(30L))).thenReturn(List.of(r));
        var g=group("AND",condition("ownerOperatorUserId","in",List.of("20")),condition("platform","in",List.of("dy")));
        assertEquals(Set.of(101L,106L),match(g));
        r.setAcceptanceStatus("pending"); assertEquals(Set.of(101L),match(g));
        when(permissions.hasTenantReadAllAccess(10L)).thenReturn(true);
        assertEquals(Set.of(101L,102L,106L),match(g));
        r.setTenantId(2L); assertEquals(Set.of(101L,102L),match(g));
        r.setTenantId(1L); r.setPersonId(999L); assertEquals(Set.of(101L,102L),match(g));
    }
}

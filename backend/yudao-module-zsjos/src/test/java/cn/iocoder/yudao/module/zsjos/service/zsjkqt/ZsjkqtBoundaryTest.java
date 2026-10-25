package cn.iocoder.yudao.module.zsjos.service.zsjkqt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.ZsjkqtController;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo.ZsjkqtVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.ZsjkqtDraftMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.ZsjkqtConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt.ZsjkqtDraftDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ZsjkqtBoundaryTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean ZsjkqtController controller() { return new ZsjkqtController(); }
        @Bean static org.springframework.validation.beanvalidation.MethodValidationPostProcessor methodValidation() {
            return new org.springframework.validation.beanvalidation.MethodValidationPostProcessor();
        }
    }

    @Test
    void controller_authorization_is_executed_and_http_excel_contract_is_discoverable() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(ZsjkqtService.class);
            context.getBeanFactory().registerSingleton("service", service);
            context.getBeanFactory().registerSingleton("excel", new ZsjkqtExcelEngine());
            context.register(Config.class);
            context.refresh();
            var controller = context.getBean(ZsjkqtController.class);
            var security = context.getBean(SecurityFrameworkService.class);
            assertThrows(AccessDeniedException.class, () -> controller.page(1,20,null,null,null));
            assertThrows(AccessDeniedException.class, () -> controller.delete(1L,0));
            verifyNoInteractions(service);

            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler("zsjkqt-test", mock(ApiErrorLogCommonApi.class)))
                    .build();
            mvc.perform(get("/zsjos/zsjkqt/drafts")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));

            when(security.hasPermission(ZsjkqtErrors.ACCESS)).thenReturn(true);
            when(service.page(1,20,null,null,null)).thenReturn(new PageResult<>(List.of(),0L));
            assertEquals(0L, controller.page(1,20,null,null,null).getData().getTotal());
            mvc.perform(get("/zsjos/zsjkqt/drafts")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(get("/zsjos/zsjkqt/drafts").param("pageNo", "0"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
            Path source = Path.of("../../docs/testing/zsjkqt/original/上下班打卡_月报_20260501-20260531 (1).xlsx");
            var file = new MockMultipartFile("file", source.getFileName().toString(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(source));
            mvc.perform(multipart("/zsjos/zsjkqt/parse/source").file(file)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.attendance.employees.length()").value(39));
            mvc.perform(post("/zsjos/zsjkqt/drafts").contentType("application/json").content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        }
    }

    @Test
    void service_function_permission_is_executed_before_data_access() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var drafts = mock(ZsjkqtDraftMapper.class);
            context.registerBean("ss", SecurityFrameworkService.class, () -> mock(SecurityFrameworkService.class));
            context.registerBean(ZsjkqtDraftMapper.class, () -> drafts);
            context.registerBean(ZsjkqtConfigMapper.class, () -> mock(ZsjkqtConfigMapper.class));
            context.registerBean(ZsjkqtExcelEngine.class);
            context.registerBean(ZsjkqtService.class);
            context.register(SecurityOnly.class);
            context.refresh();
            var service = context.getBean(ZsjkqtService.class);
            assertThrows(AccessDeniedException.class, () -> service.get(1L));
            assertThrows(AccessDeniedException.class, () -> service.delete(1L,0));
            verifyNoInteractions(drafts);
        }
    }

    @Configuration @EnableMethodSecurity static class SecurityOnly { }

    @Test
    void real_mapper_enforces_tenant_logical_delete_and_optimistic_write_scope() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:zsjkqt-"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=YEAR,MONTH", "sa", "");
        try (var keepAlive = source.getConnection()) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                CREATE TABLE zsjkqt_draft (
                    id bigint primary key, client_id varchar(64), name varchar(120), year int, month int,
                    source_file_name varchar(255), source_file_hash varchar(64), employee_count int,
                    confirmed_count int, snapshot_json clob, version int, updater_name varchar(200),
                    creator varchar(64), create_time timestamp, updater varchar(64), update_time timestamp,
                    deleted boolean default false, tenant_id bigint not null)
                """);
            jdbc.update("INSERT INTO zsjkqt_draft(id,client_id,name,version,tenant_id,deleted) VALUES(1,'one','合成甲',0,1,false),(2,'two','合成乙',0,2,false),(3,'gone','合成历史',0,1,true)");
            var config = new MybatisConfiguration();
            config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,
                    new com.baomidou.mybatisplus.core.config.GlobalConfig()
                            .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig())
                            .setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler()));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            config.addInterceptor(interceptor);
            config.addMapper(ZsjkqtDraftMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            try (var session = factory.openSession(true)) {
                var mapper = session.getMapper(ZsjkqtDraftMapper.class);
                TenantContextHolder.setTenantId(1L);
                assertEquals("合成甲",mapper.selectById(1L).getName());
                assertNull(mapper.selectById(2L));
                assertNull(mapper.selectById(3L));
                assertEquals("合成甲", mapper.selectByClientIdForUpdate("one", 1L).getName());
                assertNull(mapper.selectByClientIdForUpdate("two", 1L));
                assertNull(mapper.selectByClientIdForUpdate("gone", 1L));
                assertEquals(1,mapper.selectList(new LambdaQueryWrapperX<>()).size());
                jdbc.update("UPDATE zsjkqt_draft SET update_time='2000-01-01 00:00:00',updater='old-operator' WHERE id=1");
                jdbc.update("INSERT INTO zsjkqt_draft(id,client_id,name,version,tenant_id,deleted,update_time) VALUES(4,'four','合成较新草稿',0,1,false,'2020-01-01 00:00:00')");
                var user = new cn.iocoder.yudao.framework.security.core.LoginUser();
                user.setId(8L); user.setInfo(java.util.Map.of("nickname", "合成编辑人"));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, List.of()));
                var service = new ZsjkqtService();
                org.springframework.test.util.ReflectionTestUtils.setField(service,"drafts",mapper);
                var snapshot = cn.iocoder.yudao.framework.common.util.json.JsonUtils.getObjectMapper()
                        .readTree("{\"schemaVersion\":1,\"month\":{\"year\":2026,\"month\":6,\"employees\":[]}}");
                var result = service.save(1L,new ZsjkqtVO.Save("one","已更新",0,snapshot));
                assertEquals(1,result.version());
                assertEquals("8",result.updater());
                assertEquals("合成编辑人",result.updaterName());
                assertTrue(result.updateTime().isAfter(java.time.LocalDateTime.of(2020,1,1,0,0)));
                assertEquals(List.of(1L,4L),mapper.selectList(new LambdaQueryWrapperX<ZsjkqtDraftDO>()
                        .orderByDesc(ZsjkqtDraftDO::getUpdateTime).orderByDesc(ZsjkqtDraftDO::getId))
                        .stream().map(ZsjkqtDraftDO::getId).toList());
                assertEquals(0,mapper.update(new LambdaUpdateWrapper<ZsjkqtDraftDO>()
                        .eq(ZsjkqtDraftDO::getId,1L).eq(ZsjkqtDraftDO::getVersion,0)
                        .set(ZsjkqtDraftDO::getName,"过期覆盖")));
                assertEquals(0,mapper.deleteById(2L));
                assertEquals(1,mapper.deleteById(1L));
                assertNull(mapper.selectById(1L));
                assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM zsjkqt_draft",Integer.class));
                assertEquals("合成乙",jdbc.queryForObject("SELECT name FROM zsjkqt_draft WHERE id=2",String.class));
            }
        } finally {
            TenantContextHolder.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}

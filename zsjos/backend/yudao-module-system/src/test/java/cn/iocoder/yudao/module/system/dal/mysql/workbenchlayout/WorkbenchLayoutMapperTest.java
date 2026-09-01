package cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout;

import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutDO;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutVersionDO;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(WorkbenchLayoutMapperTest.TenantInterceptorTestConfiguration.class)
class WorkbenchLayoutMapperTest extends BaseDbUnitTest {

    @Resource
    private WorkbenchLayoutMapper layoutMapper;
    @Resource
    private WorkbenchLayoutVersionMapper versionMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldIsolateLayoutQueriesAndRowLocksByTenant() {
        TenantContextHolder.setTenantId(1L);
        WorkbenchLayoutDO tenantOne = layout("GLOBAL", 0L);
        layoutMapper.insert(tenantOne);

        TenantContextHolder.setTenantId(2L);
        assertThat(layoutMapper.selectByScope("GLOBAL", 0L)).isNull();
        assertThat(layoutMapper.selectByIdForUpdate(tenantOne.getId())).isNull();
        WorkbenchLayoutDO tenantTwo = layout("GLOBAL", 0L);
        layoutMapper.insert(tenantTwo);

        assertThat(layoutMapper.selectByScope("GLOBAL", 0L).getId()).isEqualTo(tenantTwo.getId());

        TenantContextHolder.setTenantId(1L);
        assertThat(layoutMapper.selectByScope("GLOBAL", 0L).getId()).isEqualTo(tenantOne.getId());
        assertThat(layoutMapper.selectByIdForUpdate(tenantTwo.getId())).isNull();
    }

    @Test
    void shouldIsolateImmutableVersionHistoryByTenant() {
        TenantContextHolder.setTenantId(1L);
        WorkbenchLayoutVersionDO tenantOne = version(99L, 1);
        versionMapper.insert(tenantOne);

        TenantContextHolder.setTenantId(2L);
        WorkbenchLayoutVersionDO tenantTwo = version(99L, 1);
        versionMapper.insert(tenantTwo);
        assertThat(versionMapper.selectListByLayoutId(99L))
                .extracting(WorkbenchLayoutVersionDO::getId)
                .containsExactly(tenantTwo.getId());

        TenantContextHolder.setTenantId(1L);
        assertThat(versionMapper.selectListByLayoutId(99L))
                .extracting(WorkbenchLayoutVersionDO::getId)
                .containsExactly(tenantOne.getId());
    }

    @Test
    void shouldKeepEnabledRolePrioritiesUniqueInsideTenant() {
        TenantContextHolder.setTenantId(1L);
        layoutMapper.insert(layout("ROLE", 11L).setPublishedVersionId(101L)
                .setPublishedVersionNo(1).setPublishedEnabled(true).setPublishedPriority(1));

        TenantContextHolder.setTenantId(2L);
        layoutMapper.insert(layout("ROLE", 21L).setPublishedVersionId(201L)
                .setPublishedVersionNo(1).setPublishedEnabled(true).setPublishedPriority(1));

        TenantContextHolder.setTenantId(1L);
        assertThatThrownBy(() -> layoutMapper.insert(layout("ROLE", 12L).setPublishedVersionId(102L)
                .setPublishedVersionNo(1).setPublishedEnabled(true).setPublishedPriority(1)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private WorkbenchLayoutDO layout(String scopeType, Long scopeId) {
        return new WorkbenchLayoutDO().setScopeType(scopeType).setScopeId(scopeId)
                .setDraftSnapshotJson("{}").setDraftRevision(0);
    }

    private WorkbenchLayoutVersionDO version(Long layoutId, Integer versionNo) {
        return new WorkbenchLayoutVersionDO().setLayoutId(layoutId).setScopeType("GLOBAL")
                .setScopeId(0L).setVersionNo(versionNo).setSnapshotJson("{}")
                .setEnabled(true).setPublishRemark("test").setPublisherUserId(1L)
                .setPublishTime(LocalDateTime.of(2026, 8, 26, 20, 0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TenantInterceptorTestConfiguration {

        @Bean
        static BeanPostProcessor tenantMybatisPlusInterceptorPostProcessor() {
            return new BeanPostProcessor() {

                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor(
                                new TenantDatabaseInterceptor(new TenantProperties()));
                        MyBatisUtils.addInterceptor(interceptor, tenantInterceptor, 0);
                    }
                    return bean;
                }

            };
        }

    }

}

package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import(EamStockBalanceMapperTest.TenantInterceptorTestConfiguration.class)
class EamStockBalanceMapperTest extends BaseDbUnitTest {

    @Resource
    private EamStockBalanceMapper balanceMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldIsolateSignatureLookupAndRowLockByTenant() {
        TenantContextHolder.setTenantId(1L);
        EamStockBalanceDO tenantOne = balance();
        balanceMapper.insert(tenantOne);

        TenantContextHolder.setTenantId(2L);
        assertThat(selectBySignature()).isNull();
        assertThat(balanceMapper.selectByIdForUpdate(tenantOne.getId())).isNull();
        EamStockBalanceDO tenantTwo = balance();
        balanceMapper.insert(tenantTwo);
        assertThat(selectBySignature().getId()).isEqualTo(tenantTwo.getId());

        TenantContextHolder.setTenantId(1L);
        assertThat(selectBySignature().getId()).isEqualTo(tenantOne.getId());
        assertThat(balanceMapper.selectByIdForUpdate(tenantTwo.getId())).isNull();
    }

    @Test
    void shouldApplyTenantConditionToAtomicReservationUpdate() {
        TenantContextHolder.setTenantId(1L);
        EamStockBalanceDO tenantOne = balance();
        balanceMapper.insert(tenantOne);

        TenantContextHolder.setTenantId(2L);
        assertThat(balanceMapper.reserve(tenantOne.getId(), 2)).isZero();

        TenantContextHolder.setTenantId(1L);
        assertThat(balanceMapper.reserve(tenantOne.getId(), 2)).isEqualTo(1);
        assertThat(balanceMapper.selectById(tenantOne.getId()).getReservedQuantity()).isEqualTo(2);
    }

    private EamStockBalanceDO selectBySignature() {
        return balanceMapper.selectBySignature(10L, "个", "signature", 2, 1, 1);
    }

    private EamStockBalanceDO balance() {
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setName("办公用品");
        balance.setCategoryId(10L);
        balance.setManagementMode(2);
        balance.setDeliveryMode(1);
        balance.setCustodyMode(1);
        balance.setUnit("个");
        balance.setAttributeSignature("signature");
        balance.setExtFields(Map.of());
        balance.setExtFieldLabels(Map.of());
        balance.setOnHandQuantity(10);
        balance.setReservedQuantity(0);
        balance.setFrozenQuantity(0);
        balance.setMinimumQuantity(0);
        balance.setVersion(0);
        return balance;
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

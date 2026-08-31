package cn.iocoder.yudao.framework.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class YudaoMybatisAutoConfigurationTest {

    @Test
    void mybatisPlusInterceptorContainsOptimisticLocker() {
        MybatisPlusInterceptor interceptor = new YudaoMybatisAutoConfiguration().mybatisPlusInterceptor();

        assertTrue(interceptor.getInterceptors().stream()
                .anyMatch(OptimisticLockerInnerInterceptor.class::isInstance));
    }
}

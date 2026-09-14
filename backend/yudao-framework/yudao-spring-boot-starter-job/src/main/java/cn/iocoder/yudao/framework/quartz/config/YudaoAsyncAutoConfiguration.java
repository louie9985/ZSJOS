package cn.iocoder.yudao.framework.quartz.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Arrays;

/**
 * 异步任务 Configuration
 */
@AutoConfiguration
@EnableAsync
public class YudaoAsyncAutoConfiguration {

    @Bean
    public BeanPostProcessor threadPoolTaskExecutorBeanPostProcessor(ObjectProvider<ExecutionAuditHook> hookProvider) {
        var decorator = new AuditTaskDecorator(hookProvider.orderedStream().toList());
        return new BeanPostProcessor() {

            @Override
            @SuppressWarnings("PatternVariableCanBeUsed")
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                // 处理 ThreadPoolTaskExecutor
                if (bean instanceof ThreadPoolTaskExecutor) {
                    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
                    executor.setTaskDecorator(r -> TtlRunnable.get(decorator.decorate(r)));
                    return executor;
                }
                // 处理 SimpleAsyncTaskExecutor
                // 参考 https://t.zsxq.com/CBoks 增加
                if (bean instanceof SimpleAsyncTaskExecutor) {
                    SimpleAsyncTaskExecutor executor = (SimpleAsyncTaskExecutor) bean;
                    executor.setTaskDecorator(r -> TtlRunnable.get(decorator.decorate(r)));
                    return executor;
                }
                return bean;
            }

        };
    }

}

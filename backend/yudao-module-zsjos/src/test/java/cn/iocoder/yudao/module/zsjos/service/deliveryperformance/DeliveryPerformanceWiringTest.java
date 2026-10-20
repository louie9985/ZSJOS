package cn.iocoder.yudao.module.zsjos.service.deliveryperformance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.OrderDetailMapper;
import cn.iocoder.yudao.module.zsjos.service.performance.OrderDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DeliveryPerformanceWiringTest {

    @Test
    void statisticsUsesDeliveryDetailsWhenSalesDetailsBeanAlsoExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("access", mock(DeliveryPerformanceAccess.class));
            context.registerBean(DeliveryPerformanceFactMapper.class, () -> mock(DeliveryPerformanceFactMapper.class));
            context.registerBean(DeliveryPerformanceDetailMapper.class, () -> mock(DeliveryPerformanceDetailMapper.class));
            context.getBeanFactory().registerSingleton("targets", mock(DeliveryPerformanceTargetService.class));
            context.registerBean(DeliveryPerformanceReportMapper.class, () -> mock(DeliveryPerformanceReportMapper.class));
            context.registerBean(DeliveryConversionSnapshotMapper.class, () -> mock(DeliveryConversionSnapshotMapper.class));
            context.registerBean(DeliveryOrderDetailMapper.class, () -> mock(DeliveryOrderDetailMapper.class));
            context.registerBean(OrderDetailMapper.class, () -> mock(OrderDetailMapper.class));
            context.register(OrderDetailsService.class, DeliveryOrderDetailsService.class,
                    DeliveryPerformanceStatisticsService.class);

            context.refresh();

            assertSame(context.getBean(DeliveryOrderDetailsService.class), ReflectionTestUtils.getField(
                    context.getBean(DeliveryPerformanceStatisticsService.class), "orderDetailsService"));
            assertSame(context.getBean(OrderDetailsService.class), context.getBean("orderDetailsService"));
        }
    }
}

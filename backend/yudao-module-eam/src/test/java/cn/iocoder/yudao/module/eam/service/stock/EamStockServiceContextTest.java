package cn.iocoder.yudao.module.eam.service.stock;

import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockBalanceMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockMovementMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReminderMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReservationMapper;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

class EamStockServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(EamStockBalanceMapper.class, () -> mock(EamStockBalanceMapper.class))
            .withBean(EamStockMovementMapper.class, () -> mock(EamStockMovementMapper.class))
            .withBean(EamStockReservationMapper.class, () -> mock(EamStockReservationMapper.class))
            .withBean(EamAssetMapper.class, () -> mock(EamAssetMapper.class))
            .withBean(EamStockHoldingMapper.class, () -> mock(EamStockHoldingMapper.class))
            .withBean(EamAssetService.class, () -> mock(EamAssetService.class))
            .withBean(EamStockReminderMapper.class, () -> mock(EamStockReminderMapper.class))
            .withBean(EamStockServiceImpl.class);

    @Test
    void bootJacksonObjectMapperInjectsIntoStockService() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(ObjectMapper.class);
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(EamStockServiceImpl.class);
        });
    }
}

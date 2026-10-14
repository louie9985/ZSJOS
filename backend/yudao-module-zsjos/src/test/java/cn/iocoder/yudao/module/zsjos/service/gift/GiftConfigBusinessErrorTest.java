package cn.iocoder.yudao.module.zsjos.service.gift;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.gift.vo.GiftConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.gift.GiftConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GiftConfigBusinessErrorTest {
    @Test void duplicateCreateAndUpdateReturnStableBusinessError() {
        var mapper = mock(GiftConfigMapper.class);
        var service = new GiftConfigServiceImpl();
        ReflectionTestUtils.setField(service, "m", mapper);
        when(mapper.selectCount(any())).thenReturn(1L);
        var request = new GiftConfigSaveReqVO();
        request.setName("测试礼品");
        request.setCode("fixture-gift");
        ServiceException create = assertThrows(ServiceException.class, () -> service.create(request));
        request.setId(1L);
        ServiceException update = assertThrows(ServiceException.class, () -> service.update(request));
        assertEquals(1_900_095_000, create.getCode());
        assertEquals(create.getCode(), update.getCode());
        verify(mapper, never()).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO.class));
        verify(mapper, never()).updateById(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO.class));
    }
}

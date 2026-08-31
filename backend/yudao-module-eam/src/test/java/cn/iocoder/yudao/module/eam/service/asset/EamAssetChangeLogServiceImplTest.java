package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetChangeLogDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetChangeLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum.EDIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EamAssetChangeLogServiceImplTest {

    @InjectMocks
    private EamAssetChangeLogServiceImpl service;
    @Mock
    private EamAssetChangeLogMapper mapper;

    @Test
    void recordShouldUseExplicitSystemUserId() {
        EamAssetDO before = new EamAssetDO().setId(10L).setStatus(0);
        EamAssetDO after = new EamAssetDO().setId(10L).setStatus(0);

        service.record(before, after, EDIT.getType(), null, "公开页面编辑资产信息", 99L);

        ArgumentCaptor<EamAssetChangeLogDO> captor = ArgumentCaptor.forClass(EamAssetChangeLogDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(99L, captor.getValue().getOperatorId());
    }

}

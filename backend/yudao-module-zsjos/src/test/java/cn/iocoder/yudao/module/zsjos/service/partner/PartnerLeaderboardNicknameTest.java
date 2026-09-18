package cn.iocoder.yudao.module.zsjos.service.partner;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.partner.PartnerLeaderboardConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerLeaderboardNicknameTest {
    @InjectMocks private PartnerPortalServiceImpl service;
    @Mock private PartnerMapper partnerMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PartnerLeaderboardConfigService leaderboardConfigService;

    @Test void allRankProjectionsUseNicknameAndNeverFallbackToName() {
        var partner = new PartnerDO().setId(7L).setStatus("enabled").setName("真实姓名").setNickname("星光");
        var config = new PartnerLeaderboardConfigDO().setEnabled(true).setEnabledTypes("lead_count");
        when(partnerMapper.selectById(7L)).thenReturn(partner);
        when(partnerMapper.selectListByIds(anyCollection())).thenReturn(List.of(partner));
        when(leaderboardConfigService.get()).thenReturn(config);
        when(leaderboardConfigService.eligibleEmployeeIds(config)).thenReturn(Set.of());
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        var request = new PartnerLeaderboardPageReqVO();
        request.setPeriod("total"); request.setType("lead_count");
        TenantContextHolder.setTenantId(1L);
        try {
            for (String nickname : new String[]{"星光", "", " ", null}) {
                partner.setNickname(nickname);
                var result = service.getLeaderboard(7L, request);
                String expected = "星光".equals(nickname) ? "星光" : "未设置昵称";
                assertEquals(expected, result.getList().get(0).getDisplayName());
                assertEquals(expected, result.getTop3().get(0).getDisplayName());
                assertEquals(expected, result.getMyRank().getDisplayName());
                assertEquals(expected, result.getNearbyRanks().get(0).getDisplayName());
            }
        } finally { TenantContextHolder.clear(); }
    }
}

package cn.iocoder.yudao.module.zsjos.service.partner;

import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsDetailPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeadActivityRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeadFilterOptionsRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardRespVO;

public interface PartnerPortalService {

    PartnerHomeStatisticsRespVO getHomeStatistics(Long partnerId, String period);

    PartnerHomeStatisticsDetailRespVO getHomeStatisticsDetails(Long partnerId,
                                                               PartnerHomeStatisticsDetailPageReqVO request);

    PartnerLeaderboardConfigRespVO getLeaderboardConfig();

    PartnerLeaderboardRespVO getLeaderboard(Long partnerId, PartnerLeaderboardPageReqVO request);

    PartnerLeadActivityRespVO getLeadActivity(Long partnerId, Long leadId);

    PartnerLeadFilterOptionsRespVO getLeadFilterOptions();
}

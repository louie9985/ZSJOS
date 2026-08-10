package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterAdminRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterCapabilityRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterVersionRespVO;

import java.util.List;

public interface LeadInboxFilterConfigService {

    LeadInboxFilterAdminRespVO getAdminConfig(String audience);

    void saveDraft(LeadInboxFilterSaveReqVO reqVO);

    Integer publish(String audience, Long userId);

    Integer rollback(String audience, Integer versionNo, Long userId);

    List<LeadInboxFilterVersionRespVO> getVersions(String audience);

    List<LeadInboxFilterCapabilityRespVO> getCapabilities();

    LeadInboxFilterConfigVO getPublishedConfig(String audience);

    LeadInboxFilterQuery resolveQuery(LeadInboxFilterConfigVO config, String groupKey, String optionKey);
}

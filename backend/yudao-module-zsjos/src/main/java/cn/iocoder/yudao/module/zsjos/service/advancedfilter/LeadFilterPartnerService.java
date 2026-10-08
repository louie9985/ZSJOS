package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadIdentityMaskingService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_SUBMITTED;

/** Filter options inherit management list scope and its counterparty-name masking. */
@Service
public class LeadFilterPartnerService {
    @Resource private LeadMapper leadMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private LeadObjectPermissionService permissions;
    @Resource private SecurityFrameworkService security;
    @Resource private LeadIdentityMaskingService masking;

    public List<OptionVO> options(Long userId) {
        boolean all = permissions.hasTenantReadAll(userId) || permissions.hasQueryAll();
        List<Long> related = all ? List.of() : permissions.getRelatedAndManagedUserIds(userId).stream().sorted().toList();
        var candidates = leadMapper.selectPartnerSubmitterCandidates(
                security.hasPermission(PERMISSION_QUERY_SUBMITTED) ? related : List.of(),
                security.hasPermission(PERMISSION_QUERY_OWNED) ? related : List.of(), all);
        var representative = new LinkedHashMap<Long, LeadDO>();
        candidates.forEach(lead -> representative.putIfAbsent(lead.getPartnerId(), lead));
        if (representative.isEmpty()) return List.of();
        return partnerMapper.selectListByIds(representative.keySet()).stream()
                .map(partner -> new OptionVO(String.valueOf(partner.getId()), masking.partnerName(
                        masking.resolve(userId, representative.get(partner.getId())), partner.getName())))
                .sorted(Comparator.comparing(OptionVO::label, Comparator.nullsLast(String::compareTo))
                        .thenComparing(OptionVO::value)).toList();
    }
}

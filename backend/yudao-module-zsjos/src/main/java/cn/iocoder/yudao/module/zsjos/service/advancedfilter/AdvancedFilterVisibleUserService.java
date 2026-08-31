package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Resolves personnel options only from an established object scope for the requested scene. */
@Service
public class AdvancedFilterVisibleUserService {

    public record Resolution(boolean supported, List<AdvancedFilterCatalogRespVO.OptionVO> options) {
        static Resolution unsupported() {
            return new Resolution(false, List.of());
        }

        static Resolution supported(List<AdvancedFilterCatalogRespVO.OptionVO> options) {
            return new Resolution(true, options);
        }
    }

    @Resource private LeadObjectPermissionService leadObjectPermissionService;
    @Resource private ServiceRelationMapper serviceRelationMapper;
    @Resource private AdminUserApi adminUserApi;

    public Resolution resolve(String scene, Long userId) {
        return switch (scene) {
            case "lead", "order", "lead_appeal", "duplicate_review", "registration" ->
                    Resolution.supported(leadUsers(userId));
            case "subordinate_sales" -> Resolution.supported(
                    enabledOptions(leadObjectPermissionService.getManagedUserIds(userId)));
            case "student" -> Resolution.supported(serviceRelationMapper.existsActiveByOwner(userId)
                    ? enabledOptions(Set.of(userId)) : List.of());
            default -> Resolution.unsupported();
        };
    }

    private List<AdvancedFilterCatalogRespVO.OptionVO> leadUsers(Long userId) {
        if (leadObjectPermissionService.hasQueryAll()) {
            return options(adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()));
        }
        return enabledOptions(leadObjectPermissionService.getRelatedAndManagedUserIds(userId));
    }

    private List<AdvancedFilterCatalogRespVO.OptionVO> enabledOptions(Set<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        return options(adminUserApi.getUserList(userIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .toList());
    }

    private static List<AdvancedFilterCatalogRespVO.OptionVO> options(List<AdminUserRespDTO> users) {
        return users.stream()
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname,
                                Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(AdminUserRespDTO::getId))
                .map(user -> new AdvancedFilterCatalogRespVO.OptionVO(
                        String.valueOf(user.getId()), user.getNickname()))
                .toList();
    }
}

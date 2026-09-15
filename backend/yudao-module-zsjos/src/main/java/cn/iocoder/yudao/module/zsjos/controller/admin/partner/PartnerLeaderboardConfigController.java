package cn.iocoder.yudao.module.zsjos.controller.admin.partner;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.partner.PartnerLeaderboardConfigDO;
import cn.iocoder.yudao.module.zsjos.service.partner.PartnerLeaderboardConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - H5 排行榜配置")
@RestController
@RequestMapping("/zsjos/partner/leaderboard-config")
public class PartnerLeaderboardConfigController {
    @Resource private PartnerLeaderboardConfigService service;

    @GetMapping("/get")
    @Operation(summary = "获得 H5 排行榜配置")
    @PreAuthorize("@ss.hasPermission('zsjos:partner:leaderboard-config:query')")
    public CommonResult<ConfigRespVO> get() { return success(ConfigRespVO.from(service.get())); }

    @PutMapping("/save")
    @Operation(summary = "保存 H5 排行榜配置")
    @PreAuthorize("@ss.hasPermission('zsjos:partner:leaderboard-config:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody ConfigReqVO req) {
        service.save(req.toDO()); return success(true);
    }

    @Data
    public static class ConfigReqVO {
        private Boolean enabled;
        private Boolean includeEmployeeSubmitter;
        private List<String> employeeRoleCodes;
        private List<String> enabledTypes;
        @NotBlank private String defaultType;
        @NotBlank private String defaultPeriod;
        public PartnerLeaderboardConfigDO toDO() { return new PartnerLeaderboardConfigDO().setEnabled(enabled).setIncludeEmployeeSubmitter(includeEmployeeSubmitter).setEmployeeRoleCodes(employeeRoleCodes == null ? "" : String.join(",", employeeRoleCodes)).setEnabledTypes(String.join(",", enabledTypes == null ? List.of() : enabledTypes)).setDefaultType(defaultType).setDefaultPeriod(defaultPeriod).setPageSize(20).setMaskName(true); }
    }
    @Data
    public static class ConfigRespVO {
        private Boolean enabled; private Boolean includeEmployeeSubmitter; private List<String> employeeRoleCodes; private List<String> enabledTypes; private String defaultType; private String defaultPeriod;
        static ConfigRespVO from(PartnerLeaderboardConfigDO d) { ConfigRespVO v=new ConfigRespVO(); v.enabled=d.getEnabled(); v.includeEmployeeSubmitter=d.getIncludeEmployeeSubmitter(); v.employeeRoleCodes=PartnerLeaderboardConfigService.splitCodes(d.getEmployeeRoleCodes()); v.enabledTypes=List.of(d.getEnabledTypes().split(",")); v.defaultType=d.getDefaultType(); v.defaultPeriod=d.getDefaultPeriod(); return v; }
    }
}

package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.SubordinatePartnerPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.SubordinatePartnerRow;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SubordinatePartnerService {
    @Resource private PartnerOwnershipService ownershipService;
    @Resource private PartnerOwnershipMapper ownershipMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadManagementService leadManagementService;

    public PageResult<PartnerRespVO> getPage(SubordinatePartnerPageReqVO reqVO, Long userId) {
        if (!ownershipService.canQuery(userId)) return PageResult.empty();
        String keyword = reqVO.getKeyword() == null || reqVO.getKeyword().isBlank()
                ? null : reqVO.getKeyword().trim();
        long offset = ((long) reqVO.getPageNo() - 1L) * reqVO.getPageSize();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = ownershipMapper.selectSubordinateCount(
                tenantId, userId, reqVO.getStatus(), keyword);
        if (total == 0 || offset >= total) return new PageResult<>(List.of(), total);
        List<PartnerRespVO> rows = ownershipMapper.selectSubordinatePage(
                tenantId, userId, reqVO.getStatus(), keyword, offset, reqVO.getPageSize())
                .stream().map(this::toResponse).toList();
        return new PageResult<>(rows, total);
    }

    public PageResult<LeadManagementRespVO> getLeadPage(Long partnerId, LeadManagementPageReqVO reqVO, Long userId) {
        ownershipService.checkRead(userId, partnerId);
        return leadManagementService.getPartnerLeadPage(reqVO, partnerId);
    }

    public LeadManagementRespVO getLead(Long leadId, Long userId) {
        var lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (lead.getPartnerId() == null) throw exception(PARTNER_OWNERSHIP_PERMISSION_DENIED);
        ownershipService.checkRead(userId, lead.getPartnerId());
        LeadManagementRespVO result = leadManagementService.getLead(leadId, userId);
        result.setAvailableActions(List.of());
        return result;
    }

    private PartnerRespVO toResponse(SubordinatePartnerRow row) {
        PartnerRespVO result = new PartnerRespVO();
        result.setId(row.getId()); result.setPartnerNo(row.getPartnerNo()); result.setName(row.getName());
        result.setMobile(row.getMobile()); result.setStatus(row.getStatus());
        result.setBoundSystemUserId(row.getBoundSystemUserId()); result.setChannelId(row.getChannelId());
        result.setEnabledAt(row.getEnabledAt()); result.setDisabledAt(row.getDisabledAt());
        result.setAssignedEmployeeUserId(row.getAssignedEmployeeUserId());
        result.setAssignedEmployeeName(row.getAssignedEmployeeName()); result.setAssignedAt(row.getAssignedAt());
        result.setAssignmentVersion(row.getAssignmentVersion()); result.setAssignmentEffective(true);
        return result;
    }
}

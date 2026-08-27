package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerMeRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PartnerManagementServiceImpl implements PartnerManagementService {
    @Resource private PartnerMapper mapper;
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private PartnerOwnershipService ownershipService;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(PartnerCreateReqVO reqVO) {
        if (mapper.selectByPartnerNo(reqVO.getPartnerNo()) != null) throw exception(PARTNER_STATE_INVALID);
        PartnerDO partner = BeanUtils.toBean(reqVO, PartnerDO.class);
        partner.setBoundSystemUserId(null); partner.setStatus(PARTNER_STATUS_ENABLED);
        partner.setEnabledAt(LocalDateTime.now()); partner.setVersion(0); mapper.insert(partner);
        partnerAccountService.create(partner.getId(), reqVO.getMobile(), reqVO.getPassword());
        return partner.getId();
    }

    @Override public List<PartnerRespVO> list() {
        return mapper.selectList().stream().map(partner -> {
            PartnerRespVO result = BeanUtils.toBean(partner, PartnerRespVO.class);
            var ownership = ownershipService.getByPartnerId(partner.getId());
            if (ownership != null) {
                result.setAssignedEmployeeUserId(ownership.getEmployeeUserId());
                result.setAssignedEmployeeName(ownership.getEmployeeNameSnapshot());
                result.setAssignedAt(ownership.getAssignedAt());
                result.setAssignmentVersion(ownership.getVersion());
                result.setAssignmentEffective(ownershipService.canRead(ownership.getEmployeeUserId(), partner.getId()));
            }
            return result;
        }).toList();
    }

    @Override public PartnerMeRespVO getMe(Long accountId) {
        PartnerContext context = partnerAccountService.requireContext(accountId);
        PartnerDO partner = mapper.selectById(context.partnerId());
        if (partner == null) throw exception(PARTNER_NOT_EXISTS);
        return BeanUtils.toBean(partner, PartnerMeRespVO.class);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void disable(Long id, PartnerStateReqVO reqVO) {
        PartnerDO partner = require(id);
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(PARTNER_STATE_INVALID);
        partner.setStatus(PARTNER_STATUS_DISABLED); partner.setDisabledAt(LocalDateTime.now()); mapper.updateById(partner);
        partnerAccountService.setEnabled(partner.getId(), false);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void enable(Long id, PartnerStateReqVO reqVO) {
        PartnerDO partner = require(id);
        if (!PARTNER_STATUS_DISABLED.equals(partner.getStatus())) throw exception(PARTNER_STATE_INVALID);
        partner.setStatus(PARTNER_STATUS_ENABLED); partner.setEnabledAt(LocalDateTime.now()); partner.setDisabledAt(null);
        mapper.updateById(partner);
        partnerAccountService.setEnabled(partner.getId(), true);
    }

    private PartnerDO require(Long id) {
        PartnerDO partner = mapper.selectById(id); if (partner == null) throw exception(PARTNER_NOT_EXISTS); return partner;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMobile(Long id, PartnerMobileUpdateReqVO reqVO) {
        require(id);
        partnerAccountService.updateMobile(id, reqVO.getMobile());
        mapper.updateById(new PartnerDO().setId(id).setMobile(reqVO.getMobile()));
    }

    @Override
    public void resetPassword(Long id, PartnerPasswordResetReqVO reqVO) {
        require(id);
        partnerAccountService.resetPassword(id, reqVO.getPassword());
    }
}

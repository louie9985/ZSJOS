package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.*;

@Service
public class PartnerManagementServiceImpl implements PartnerManagementService {
    @Resource private PartnerMapper mapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PostApi postApi;
    @Resource private LeadMapper leadMapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(PartnerCreateReqVO reqVO) {
        if (mapper.selectByPartnerNo(reqVO.getPartnerNo()) != null) throw exception(PARTNER_STATE_INVALID);
        AdminUserCreateReqDTO account = new AdminUserCreateReqDTO();
        account.setUsername(reqVO.getUsername()); account.setPassword(reqVO.getPassword());
        account.setNickname(reqVO.getName()); account.setMobile(reqVO.getMobile()); account.setPostIds(Set.of());
        Long userId = adminUserApi.createUser(account);
        PartnerDO partner = BeanUtils.toBean(reqVO, PartnerDO.class);
        partner.setBoundSystemUserId(userId); partner.setStatus(PARTNER_STATUS_ENABLED);
        partner.setEnabledAt(LocalDateTime.now()); partner.setVersion(0); mapper.insert(partner);
        return partner.getId();
    }

    @Override public List<PartnerRespVO> list() { return BeanUtils.toBean(mapper.selectList(), PartnerRespVO.class); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void disable(Long id, PartnerStateReqVO reqVO) {
        PartnerDO partner = require(id);
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(PARTNER_STATE_INVALID);
        partner.setStatus(PARTNER_STATUS_DISABLED); partner.setDisabledAt(LocalDateTime.now()); mapper.updateById(partner);
        adminUserApi.updateUserStatus(partner.getBoundSystemUserId(), CommonStatusEnum.DISABLE.getStatus(), reqVO.getReason());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void enable(Long id, PartnerStateReqVO reqVO) {
        PartnerDO partner = require(id);
        if (!PARTNER_STATUS_DISABLED.equals(partner.getStatus())) throw exception(PARTNER_STATE_INVALID);
        AdminUserRespDTO user = adminUserApi.getUser(partner.getBoundSystemUserId());
        if (user == null || user.getPostIds() != null && !user.getPostIds().isEmpty()) throw exception(PARTNER_ACCOUNT_CONFLICT);
        partner.setStatus(PARTNER_STATUS_ENABLED); partner.setEnabledAt(LocalDateTime.now()); partner.setDisabledAt(null);
        mapper.updateById(partner);
        adminUserApi.updateUserStatus(partner.getBoundSystemUserId(), CommonStatusEnum.ENABLE.getStatus(), reqVO.getReason());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void convert(Long id, PartnerConvertReqVO reqVO) {
        PartnerDO partner = require(id);
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(PARTNER_STATE_INVALID);
        PostRespDTO operator = postApi.getPostByCode(NEW_MEDIA_OPERATOR);
        PostRespDTO manager = postApi.getPostByCode(DEPT_MANAGER);
        Set<Long> posts;
        if ("new_media_employee".equals(reqVO.getTargetType()) && operator != null) posts = Set.of(operator.getId());
        else if ("new_media_manager".equals(reqVO.getTargetType()) && operator != null && manager != null) {
            posts = Set.of(operator.getId(), manager.getId());
        } else throw exception(PARTNER_CONVERSION_POST_INVALID);
        AdminUserOrganizationUpdateReqDTO update = new AdminUserOrganizationUpdateReqDTO();
        update.setUserId(partner.getBoundSystemUserId()); update.setDeptId(reqVO.getDeptId()); update.setPostIds(posts);
        adminUserApi.updateUserOrganization(update);
        partner.setStatus(PARTNER_STATUS_CONVERTED); partner.setDisabledAt(LocalDateTime.now()); mapper.updateById(partner);
        if (reqVO.isMigrateHistoricalOrganization()) leadMapper.updateSourceDeptByPartnerId(id, reqVO.getDeptId());
    }

    private PartnerDO require(Long id) {
        PartnerDO partner = mapper.selectById(id); if (partner == null) throw exception(PARTNER_NOT_EXISTS); return partner;
    }
}

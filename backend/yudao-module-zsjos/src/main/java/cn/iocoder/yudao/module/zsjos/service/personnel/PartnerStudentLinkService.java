package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_LINK_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_REFERENCE_INVALID;

@Service
public class PartnerStudentLinkService {
    @Resource private PartnerStudentLinkMapper mapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PersonMapper personMapper;

    @Transactional(rollbackFor = Exception.class)
    public void bind(Long partnerId, Long studentId, String reason, Long userId) {
        if (partnerMapper.selectById(partnerId) == null || personMapper.selectById(studentId) == null) {
            throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        }
        if (mapper.selectActiveByPartner(partnerId) != null || mapper.selectActiveByStudent(studentId) != null) {
            throw exception(PARTNER_STUDENT_LINK_CONFLICT);
        }
        PartnerStudentLinkDO link = new PartnerStudentLinkDO();
        link.setPartnerId(partnerId);
        link.setStudentPersonId(studentId);
        link.setStatus("active");
        link.setStartedAt(LocalDateTime.now());
        link.setOperatedByUserId(userId);
        link.setReason(reason);
        try {
            mapper.insert(link);
        } catch (DuplicateKeyException ex) {
            throw exception(PARTNER_STUDENT_LINK_CONFLICT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long partnerId, String reason, Long userId) {
        if (partnerMapper.selectById(partnerId) == null) throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        PartnerStudentLinkDO link = mapper.selectActiveByPartner(partnerId);
        if (link == null) return;
        link.setStatus("ended");
        link.setEndedAt(LocalDateTime.now());
        link.setOperatedByUserId(userId);
        link.setReason(reason);
        mapper.updateById(link);
    }
}

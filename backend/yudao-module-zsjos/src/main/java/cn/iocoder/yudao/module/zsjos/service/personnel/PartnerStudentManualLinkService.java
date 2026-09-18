package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentLinkRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_STUDENT_REFERENCE_INVALID;

/** Manual ADMIN operations require student visibility; invitation activation has its own authorization. */
@Service
public class PartnerStudentManualLinkService {
    @Resource private PartnerStudentLinkService links;
    @Resource private PartnerStudentLinkMapper mapper;
    @Resource private PartnerMapper partners;
    @Resource private PersonMapper people;

    @ZsjosPermission(bizType = "student", bizId = "#studentPersonId", action = "read")
    public PartnerStudentLinkRespVO getStudent(Long studentPersonId) {
        requireStudent(studentPersonId);
        var result = new PartnerStudentLinkRespVO();
        var link = mapper.selectActiveByStudent(studentPersonId);
        if (link == null) return result;
        if (!Objects.equals(link.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        }
        var partner = partners.selectById(link.getPartnerId());
        if (partner == null || !Objects.equals(partner.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        }
        result.setBound(true);
        result.setPartnerNo(partner.getPartnerNo());
        result.setPartnerName(partner.getName());
        result.setStartedAt(link.getStartedAt());
        return result;
    }

    @ZsjosPermission(bizType = "student", bizId = "#studentPersonId", action = "read")
    public void bind(Long partnerId, Long studentPersonId, String reason, Long userId) {
        requireStudent(studentPersonId);
        var partner = partners.selectById(partnerId);
        if (partner == null || !Objects.equals(partner.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        }
        links.bind(partnerId, studentPersonId, reason, userId);
    }

    private void requireStudent(Long id) {
        var student = people.selectById(id);
        if (student == null || !Objects.equals(student.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(PARTNER_STUDENT_REFERENCE_INVALID);
        }
    }
}

package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSelfSourcedArchiveService {
    @Resource private PersonMapper personMapper;
    @Resource private LeadSubmissionIdentityService identityService;

    // Authorized contact-matching projection, not general archive or historical-lead read access.
    public List<LeadArchiveCandidateRespVO> query(String mobile, String wechat, Long userId,
                                                 LeadSubmissionIdentityService.Identity identity) {
        if (identity == LeadSubmissionIdentityService.Identity.SALES) identityService.requireSales(userId);
        else if (identity == LeadSubmissionIdentityService.Identity.EDUCATION) identityService.requireEducationSubmitter(userId);
        else throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        mobile = StrUtil.trimToNull(mobile);
        wechat = normalizeWechat(wechat);
        validateContact(mobile, wechat);
        return personMapper.selectDuplicateCandidates(mobile, wechat).stream().map(this::candidate).toList();
    }

    /** Called inside the creation transaction: recheck membership and snapshot without modifying Person. */
    public PersonDO requireSelection(LeadCreateReqVO request) {
        if (!Boolean.TRUE.equals(request.getArchiveQueryConfirmed())) throw exception(LEAD_ARCHIVE_QUERY_REQUIRED);
        String mobile = StrUtil.trimToNull(request.getArchiveQueryMobile());
        String wechat = normalizeWechat(request.getArchiveQueryWechatId());
        validateContact(mobile, wechat);
        if (!Objects.equals(mobile, StrUtil.trimToNull(request.getMobile()))
                || !Objects.equals(wechat, normalizeWechat(request.getWechatId()))) {
            throw exception(LEAD_ARCHIVE_CHANGED);
        }
        // A new Person must reserve contacts before taking Person locks. createNew performs
        // the authoritative locking recheck after reservation, including concurrent new matches.
        List<PersonDO> matches = request.getSelectedPersonId() == null
                ? personMapper.selectDuplicateCandidates(mobile, wechat)
                : personMapper.selectDuplicateCandidatesForUpdate(mobile, wechat,
                    TenantContextHolder.getRequiredTenantId());
        if (matches.isEmpty()) {
            if (request.getSelectedPersonId() != null
                    || !Objects.equals(mobile, StrUtil.trimToNull(request.getMobile()))
                    || !Objects.equals(wechat, normalizeWechat(request.getWechatId()))) {
                throw exception(LEAD_ARCHIVE_CHANGED);
            }
            return null;
        }
        if (request.getSelectedPersonId() == null) throw exception(LEAD_ARCHIVE_SELECTION_REQUIRED);
        PersonDO selected = matches.stream().filter(p -> Objects.equals(p.getId(), request.getSelectedPersonId()))
                .findFirst().orElseThrow(() -> exception(LEAD_ARCHIVE_CHANGED));
        if (!Objects.equals(candidate(selected).getSnapshotKey(), request.getArchiveSnapshotKey())) {
            throw exception(LEAD_ARCHIVE_CHANGED);
        }
        // The selected Person is an association, not the source of this submission's snapshots.
        return selected;
    }

    private LeadArchiveCandidateRespVO candidate(PersonDO person) {
        LeadArchiveCandidateRespVO result = new LeadArchiveCandidateRespVO();
        result.setPersonId(person.getId()); result.setPersonNo(person.getPersonNo());
        result.setName(person.getName()); result.setMobile(person.getMobile()); result.setWechatId(person.getWechatId());
        result.setSnapshotKey(DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                person.getId(), person.getName(), person.getMobile(), person.getWechatId()))));
        return result;
    }

    private String normalizeWechat(String value) {
        String trimmed = StrUtil.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private void validateContact(String mobile, String wechat) {
        if (mobile == null && wechat == null) throw exception(LEAD_CONTACT_REQUIRED);
        if (mobile != null && !ValidationUtils.isMobile(mobile)) throw exception(LEAD_MOBILE_INVALID);
    }
}

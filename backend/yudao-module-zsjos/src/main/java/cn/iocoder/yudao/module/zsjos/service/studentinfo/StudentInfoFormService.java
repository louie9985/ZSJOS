package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Runtime;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_WON;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentInfoFormService {
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper values;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private StudentInfoConfigService configService;
    @Resource private StudentInfoFields fields;
    @Resource private LeadMapper leads;
    @Resource private StudentInfoPermissionProvider permission;
    @Resource private SecurityFrameworkService security;
    @Resource private TenantFrameworkService tenants;
    @Value("${zsjos.student-info.public-base-url:${ZSJOS_PUBLIC_H5_BASE_URL:}}") private String publicBaseUrl;
    private static final SecureRandom RANDOM=new SecureRandom();

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="create")
    @Transactional(rollbackFor=Exception.class)
    public Link generate(Long leadId) {
        lockLead(leadId,true);
        requireNotSubmitted(leadId);
        var existing=forms.byLead(leadId);
        // Retrying generation must not invalidate a link already delivered to the student.
        if (existing!=null) return link(existing);
        return newForm(leadId,configService.published().getId());
    }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="link-read")
    public Link getLink(Long leadId) {
        var form=forms.byLead(leadId);
        return form==null ? null : link(form);
    }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="regenerate")
    @Transactional(rollbackFor=Exception.class)
    public Link regenerate(Long leadId,Long expectedFormId) {
        lockLead(leadId,true); requireNotSubmitted(leadId);
        var old=requireLatest(leadId,expectedFormId);
        old.setStatus("EXPIRED"); old.setRevokedAt(LocalDateTime.now()); forms.updateById(old);
        // Link rotation does not silently replace the form that was sent with a newer template.
        return newForm(leadId,old.getConfigVersionId());
    }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="revoke")
    @Transactional(rollbackFor=Exception.class)
    public void revoke(Long leadId,Long expectedFormId) {
        lockLead(leadId,true); requireNotSubmitted(leadId);
        var old=requireLatest(leadId,expectedFormId);
        old.setStatus("REVOKED"); old.setRevokedAt(LocalDateTime.now()); forms.updateById(old);
    }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="read")
    public Detail detail(Long leadId) { return project(leadId,false); }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="sensitive-read")
    public Detail sensitiveDetail(Long leadId) { return project(leadId,true); }

    @ZsjosPermission(bizType="student-info",bizId="#leadId",action="export")
    public Detail exportDetail(Long leadId) { return project(leadId,security.hasPermission(SENSITIVE)); }

    private Detail project(Long leadId,boolean unmasked) {
        Detail result=new Detail();
        var form=forms.submitted(leadId);
        if (form==null) form=forms.byLead(leadId);
        result.setStatus(form==null ? "NONE" : state(form));
        result.setCanReadSensitive(security.hasPermission(SENSITIVE));
        result.setCanExport(security.hasPermission(EXPORT));
        if (form==null) return result;
        result.setId(form.getId()); result.setCreatedAt(form.getCreateTime()); result.setSubmittedAt(form.getSubmittedAt());
        result.setExpiresAt(form.getExpiresAt());
        var config=requireConfig(form); result.setConfigVersion(config.getVersionNo());
        result.setFields(fields.parse(config.getFieldsJson()).stream().filter(Field::getEnabled).toList());
        Map<String,String> content=new LinkedHashMap<>();
        if ("SUBMITTED".equals(form.getStatus())) for (var value : values.byForm(form.getId())) {
            String text=value.getValueText();
            if (text==null) text=value.getValueLabelSnapshot();
            if (text==null) text=value.getAreaLabelSnapshot();
            if (text==null) text="";
            if (Boolean.TRUE.equals(value.getSensitive()) && !unmasked && !text.isEmpty())
                text="mobile".equals(value.getFieldKey()) ? DesensitizedUtil.mobilePhone(text) : DesensitizedUtil.idCardNum(text,3,4);
            content.put(value.getFieldKey(),text);
        }
        result.setValues(content);
        return result;
    }

    public Runtime publicDetail(String token) {
        var located=locate(token);
        return inTenant(located.getTenantId(), () -> {
            var form=forms.byToken(hash(token));
            if (form==null) throw exception(LINK_INVALID);
            Runtime result=new Runtime(); result.setStatus(state(form));
            if (!"DRAFT".equals(result.getStatus())) return result;
            requireWon(form.getLeadId());
            var config=requireConfig(form);
            result.setConfigVersion(config.getVersionNo()); result.setTenantId(form.getTenantId());
            var definitions=fields.parse(config.getFieldsJson()).stream().filter(Field::getEnabled).toList();
            result.setFields(definitions);
            Map<String,List<Option>> options=new LinkedHashMap<>();
            for (Field f:definitions) if ("dict".equals(f.getType())) options.put(f.getKey(),fields.options(f));
            result.setOptions(options);
            return result;
        });
    }

    @Transactional(rollbackFor=Exception.class)
    public void submit(String token,Submit request) {
        var located=locate(token);
        inTenant(located.getTenantId(), () -> {
            // Use the same lock order as regeneration so an old token cannot commit after revocation.
            lockLead(located.getLeadId(),false);
            var form=forms.lock(located.getId());
            if (form==null || !hash(token).equals(form.getTokenHash())) throw exception(LINK_INVALID);
            if ("SUBMITTED".equals(form.getStatus())) throw exception(ALREADY_SUBMITTED);
            if (!"DRAFT".equals(state(form))) throw exception(LINK_EXPIRED);
            var config=requireConfig(form);
            var answers=fields.validateValues(fields.parse(config.getFieldsJson()),request.getValues());
            for (var answer:answers) {
                answer.setFormId(form.getId()); answer.setTenantId(form.getTenantId()); values.insert(answer);
            }
            int changed=forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>()
                    .eq(StudentInfoFormDO::getId,form.getId()).eq(StudentInfoFormDO::getStatus,"DRAFT")
                    .gt(StudentInfoFormDO::getExpiresAt,LocalDateTime.now()).isNull(StudentInfoFormDO::getRevokedAt)
                    .set(StudentInfoFormDO::getStatus,"SUBMITTED")
                    .set(StudentInfoFormDO::getSubmittedAt,LocalDateTime.now()).set(StudentInfoFormDO::getSubmitSource,"public"));
            if (changed!=1) throw exception(LINK_EXPIRED);
            return null;
        });
    }

    private StudentInfoFormDO locate(String token) {
        String hash=hash(token);
        Boolean ignore=TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            var found=forms.byToken(hash);
            if (found==null) throw exception(LINK_INVALID);
            tenants.validTenant(found.getTenantId());
            return found;
        } finally { TenantContextHolder.setIgnore(ignore); }
    }

    private <T> T inTenant(Long tenant,Supplier<T> action) {
        Long old=TenantContextHolder.getTenantId(); Boolean ignore=TenantContextHolder.isIgnore();
        try { TenantContextHolder.setTenantId(tenant); TenantContextHolder.setIgnore(false); return action.get(); }
        finally { TenantContextHolder.setTenantId(old); TenantContextHolder.setIgnore(ignore); }
    }
    private void lockLead(Long id,boolean manage) {
        var lead=leads.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
        if (lead==null) throw exception(LEAD_NOT_EXISTS);
        if (!STATUS_WON.equals(lead.getStatus())) throw exception(NOT_WON);
        if (manage) permission.check(id,"create",getLoginUserId());
    }
    private void requireWon(Long id) {
        var lead=leads.selectById(id);
        if (lead==null || !STATUS_WON.equals(lead.getStatus())) throw exception(NOT_WON);
    }
    private void requireNotSubmitted(Long leadId) {
        if (forms.submitted(leadId)!=null) throw exception(ALREADY_SUBMITTED);
    }
    private StudentInfoFormDO requireLatest(Long leadId,Long expected) {
        var row=forms.byLead(leadId);
        if (row==null || !row.getId().equals(expected)) throw exception(VERSION_CONFLICT);
        return forms.lock(row.getId());
    }
    private StudentInfoFormConfigDO requireConfig(StudentInfoFormDO form) {
        var config=configs.selectById(form.getConfigVersionId());
        if (config==null || "DRAFT".equals(config.getStatus())) throw exception(CONFIG_MISSING);
        return config;
    }
    private Link newForm(Long leadId,Long versionId) {
        baseUrl();
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var row=new StudentInfoFormDO();
        row.setLeadId(leadId); row.setSalesUserId(getLoginUserId()); row.setConfigVersionId(versionId);
        row.setTokenHash(hash(raw)); row.setTokenCiphertext(raw); row.setStatus("DRAFT");
        row.setExpiresAt(LocalDateTime.now().plusDays(30)); forms.insert(row);
        return link(row);
    }
    private Link link(StudentInfoFormDO row) {
        Link result=new Link(); result.setFormId(row.getId()); result.setStatus(state(row));
        result.setCreatedAt(row.getCreateTime());
        result.setExpiresAt(row.getExpiresAt());
        boolean pending=!"SUBMITTED".equals(row.getStatus());
        result.setCanRegenerate(pending && security.hasPermission(REGENERATE));
        result.setCanRevoke(pending && "DRAFT".equals(state(row)) && security.hasPermission(REVOKE));
        if ("DRAFT".equals(state(row))) result.setUrl(baseUrl()+"/student-info-form#token="+row.getTokenCiphertext());
        return result;
    }
    private String baseUrl() {
        try {
            String value=publicBaseUrl==null?"":publicBaseUrl.trim().replaceAll("/+$","");
            URI uri=URI.create(value);
            if (uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || uri.getQuery()!=null || uri.getFragment()!=null) throw exception(URL_INVALID);
            return value;
        } catch (IllegalArgumentException e) { throw exception(URL_INVALID); }
    }
    static String state(StudentInfoFormDO form) {
        return "DRAFT".equals(form.getStatus()) && (form.getRevokedAt()!=null || form.getExpiresAt()==null
                || !form.getExpiresAt().isAfter(LocalDateTime.now())) ? "EXPIRED" : form.getStatus();
    }
    static String hash(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw exception(LINK_INVALID);
        return DigestUtil.sha256Hex(token);
    }
}

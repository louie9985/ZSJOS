package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Runtime;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
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
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_IDEMPOTENCY_CONFLICT;

@Service
public class StudentInfoFormService {
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper values;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private StudentInfoConfigService configService;
    @Resource private StudentInfoFields fields;
    @Resource private StudentInfoProductRules productRules;
    @Resource private RegistrationCaseMapper cases;
    @Resource private SalesOrderMapper orders;
    @Resource private SalesOrderItemMapper orderItems;
    @Resource private ZsjosProductMapper products;
    @Resource private RegistrationCommandMapper commands;
    @Resource private SecurityFrameworkService security;
    @Resource private TenantFrameworkService tenants;
    @Resource private StudentInfoImageService images;
    @Value("${zsjos.student-info.public-base-url:${zsjos.positioning.public-base-url:${ZSJOS_PUBLIC_H5_BASE_URL:}}}") private String publicBaseUrl;
    private static final SecureRandom RANDOM=new SecureRandom();

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="create")
    @Transactional(rollbackFor=Exception.class)
    public Link generate(Long caseId,Command request) {
        var item=lockCase(caseId);
        if (replay(item,"generate",request)) return link(forms.byCase(caseId),item);
        editable(item,request);
        var existing=forms.byCase(caseId);
        if (existing!=null) return link(existing,item);
        var config=configService.published();
        var order=orders.selectById(item.getOrderId());
        var productItem=orderItems.selectById(item.getOrderItemId());
        if (productItem==null || !Objects.equals(productItem.getOrderId(),item.getOrderId()) || order.getPersonId()==null) throw exception(CONFIG_INVALID);
        var product=productItem.getProductId()==null ? products.selectByProductRef(productItem.getProductRef()) : products.selectById(productItem.getProductId());
        if (product==null) throw exception(CONFIG_INVALID);
        var path=productRules.path(product.getId());
        var row=new StudentInfoFormDO();
        row.setRegistrationCaseId(caseId); row.setPersonId(order.getPersonId()); row.setOrderItemId(item.getOrderItemId());
        row.setProductId(product.getId()); row.setConfigVersionId(config.getId()); row.setSalesUserId(getLoginUserId());
        row.setFieldsSnapshot(JsonUtils.toJsonString(fields.withAttachmentPolicy(productRules.resolve(fields.parse(config.getFieldsJson()),configService.categoryIds(config),path))));
        row.setCategoryPathSnapshot(JsonUtils.toJsonString(path));
        String productName=product.getName();
        if (productItem.getProductSnapshot()!=null) try { var snapshot=JsonUtils.getObjectMapper().readTree(productItem.getProductSnapshot()); if(snapshot.hasNonNull("name"))productName=snapshot.get("name").asText(); } catch(Exception ignored) { /* legacy snapshot may be absent */ }
        row.setProductName(productName);
        rotate(row); forms.insert(row); touch(item);
        return link(row,item);
    }

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="link-read")
    public Link getLink(Long caseId) { return link(forms.byCase(caseId),cases.selectById(caseId)); }

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="regenerate")
    @Transactional(rollbackFor=Exception.class)
    public Link regenerate(Long caseId,Command request) { return mutate(caseId,request,"regenerate"); }

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="revoke")
    @Transactional(rollbackFor=Exception.class)
    public Link revoke(Long caseId,Command request) { return mutate(caseId,request,"revoke"); }

    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="sent")
    @Transactional(rollbackFor=Exception.class)
    public Link confirmSent(Long caseId,Command request) {
        if (request.getSent()==null) throw exception(FIELD_INVALID,"发送确认");
        return mutate(caseId,request,"sent");
    }
    private Link mutate(Long id,Command req,String operation) {
        var item=lockCase(id);
        if (replay(item,operation,req)) return link(forms.byCase(id),item);
        editable(item,req);
        var form=forms.byCase(id);
        if (form==null || !Objects.equals(form.getId(),req.getFormId())) throw exception(VERSION_CONFLICT);
        form=forms.lock(form.getId());
        if ("sent".equals(operation)) {
            if (Boolean.TRUE.equals(req.getSent()) && !Set.of("DRAFT","SUBMITTED").contains(state(form))) throw exception(LINK_EXPIRED);
            forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>().eq(StudentInfoFormDO::getId,form.getId())
                    .set(StudentInfoFormDO::getSentAt,Boolean.TRUE.equals(req.getSent()) ? LocalDateTime.now() : null)
                    .set(StudentInfoFormDO::getSentByUserId,Boolean.TRUE.equals(req.getSent()) ? getLoginUserId() : null));
        } else {
            if ("SUBMITTED".equals(form.getStatus())) throw exception(ALREADY_SUBMITTED);
            if ("regenerate".equals(operation)) { rotate(form); forms.updateById(form); }
            else forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>().eq(StudentInfoFormDO::getId,form.getId())
                    .set(StudentInfoFormDO::getStatus,"REVOKED").set(StudentInfoFormDO::getRevokedAt,LocalDateTime.now()));
            forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>().eq(StudentInfoFormDO::getId,form.getId())
                    .set(StudentInfoFormDO::getSentAt,null).set(StudentInfoFormDO::getSentByUserId,null));
        }
        touch(item); return link(forms.byCase(id),item);
    }
    private boolean replay(RegistrationCaseDO item,String type,Command req) {
        var previous=commands.selectByIdempotencyKey(req.getIdempotencyKey());
        String fingerprint=String.valueOf(req.getFormId())+":"+req.getSent();
        if (previous!=null) {
            if (!Objects.equals(previous.getRegistrationCaseId(),item.getId()) || !Objects.equals(previous.getOperatorUserId(),getLoginUserId())
                    || !Objects.equals(previous.getCommandType(),"info-"+type) || !Objects.equals(previous.getRequestFingerprint(),fingerprint)) throw exception(REGISTRATION_IDEMPOTENCY_CONFLICT);
            return true;
        }
        var command=new RegistrationCommandDO(); command.setRegistrationCaseId(item.getId()); command.setOperatorUserId(getLoginUserId());
        command.setCommandType("info-"+type); command.setIdempotencyKey(req.getIdempotencyKey()); command.setRequestFingerprint(fingerprint); commands.insert(command); return false;
    }
    private void editable(RegistrationCaseDO item,Command req) {
        if (!Set.of("pending","processing").contains(item.getStatus())) throw exception(LINK_EXPIRED);
        if (!Objects.equals(item.getVersion(),req.getVersion())) throw exception(VERSION_CONFLICT);
        var order=orders.selectById(item.getOrderId());
        if (order==null || !Set.of("pending_approval","revision_required","effective").contains(order.getStatus())) throw exception(LINK_EXPIRED);
    }
    private void touch(RegistrationCaseDO item) { item.setVersion(item.getVersion()+1); item.setStatus("processing"); cases.updateById(item); }
    private void rotate(StudentInfoFormDO row) {
        baseUrl(); byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        row.setTokenHash(hash(raw)); row.setTokenCiphertext(raw); row.setStatus("DRAFT");
        row.setExpiresAt(LocalDateTime.now().plusDays(30));
        if (row.getId()!=null) forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>().eq(StudentInfoFormDO::getId,row.getId()).set(StudentInfoFormDO::getRevokedAt,null));
        row.setRevokedAt(null);
    }
    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="read")
    public Detail detail(Long caseId) { return project(caseId,false); }
    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="sensitive-read")
    public Detail sensitiveDetail(Long caseId) { return project(caseId,true); }
    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="export")
    public Detail exportDetail(Long caseId) { return project(caseId,canReadSensitive()); }
    public boolean canReadSensitive() { return security.hasPermission(SENSITIVE); }
    private Detail project(Long caseId,boolean unmasked) {
        var form=forms.byCase(caseId); var result=new Detail();
        result.setStatus(form==null ? "NONE" : effectiveState(form)); result.setCanReadSensitive(canReadSensitive()); result.setCanExport(security.hasPermission(EXPORT));
        if (form==null) return result;
        result.setId(form.getId()); result.setCreatedAt(form.getCreateTime()); result.setSubmittedAt(form.getSubmittedAt()); result.setExpiresAt(form.getExpiresAt());
        result.setConfigVersion(configs.selectById(form.getConfigVersionId()).getVersionNo()); result.setFields(definitions(form));
        Map<String,String> content=new LinkedHashMap<>();
        if ("SUBMITTED".equals(form.getStatus())) {
            for (var value:values.byForm(form.getId())) {
                String text=value.getValueText()!=null ? value.getValueText() : value.getValueLabelSnapshot()!=null ? value.getValueLabelSnapshot() : value.getAreaLabelSnapshot();
                if (text==null) text="";
                if (Boolean.TRUE.equals(value.getSensitive()) && !unmasked && !text.isEmpty()) text="mobile".equals(value.getFieldKey()) ? DesensitizedUtil.mobilePhone(text) : (value.getFieldKey().endsWith("shipping_address") ? "已隐藏" : DesensitizedUtil.idCardNum(text,3,4));
                content.put(value.getFieldKey(),text);
            }
            result.setImages(images.list(form.getId()));
        }
        result.setValues(content); return result;
    }
    public Runtime publicDetail(String token) {
        var located=locate(token);
        return inTenant(located.getTenantId(),()->{
            var form=forms.byToken(hash(token)); var result=new Runtime(); result.setStatus(effectiveState(form));
            if (!"DRAFT".equals(result.getStatus())) return result;
            result.setTenantId(form.getTenantId()); result.setProductName(form.getProductName());
            result.setConfigVersion(configs.selectById(form.getConfigVersionId()).getVersionNo()); result.setFields(definitions(form));
            Map<String,List<Option>> options=new LinkedHashMap<>();
            for (var f:result.getFields()) if ("dict".equals(f.getType())) options.put(f.getKey(),fields.options(f));
            var draft = draft(form);
            // A restored selection displays its saved label, including after dictionary renaming.
            for (var selection : draft.getSelections().entrySet()) if (options.containsKey(selection.getKey())) {
                var entries = new ArrayList<>(options.get(selection.getKey())); var saved = selection.getValue();
                int index = java.util.stream.IntStream.range(0,entries.size()).filter(i -> saved.getCode().equals(entries.get(i).getValue())).findFirst().orElse(-1);
                if (index < 0) entries.add(new Option(saved.getCode(),saved.getLabel()));
                else entries.set(index,new Option(saved.getCode(),saved.getLabel()));
                options.put(selection.getKey(), entries);
            }
            result.setDraft(draft); result.setOptions(options); result.setImages(images.list(form.getId())); return result;
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public Draft saveDraft(String token, DraftWrite request) {
        return withLockedToken(token,form -> {
            if (!"DRAFT".equals(effectiveState(form))) throw exception(LINK_EXPIRED);
            var previous = draft(form);
            // A timed-out response can hide a successful write; identical retries are safe.
            if (request.getRevision() != null && previous.getRevision() == request.getRevision()+1
                    && Objects.equals(request.getValues(),previous.getValues())) return previous;
            if (!Objects.equals(request.getRevision(),previous.getRevision())) throw exception(DRAFT_CONFLICT);
            var saved = StudentInfoDraftPolicy.build(definitions(form),previous,request.getValues(),fields::options);
            saved.setRevision(previous.getRevision()+1); saved.setSavedAt(LocalDateTime.now());
            var update = new StudentInfoFormDO(); update.setId(form.getId());
            update.setDraftSnapshot(JsonUtils.toJsonString(saved)); update.setDraftRevision(saved.getRevision()); update.setDraftSavedAt(saved.getSavedAt());
            forms.updateById(update); return saved;
        });
    }
    private Draft draft(StudentInfoFormDO form) {
        var saved = form.getDraftSnapshot() == null ? new Draft() : JsonUtils.parseObject(form.getDraftSnapshot(),Draft.class);
        saved.setRevision(form.getDraftRevision() == null ? 0 : form.getDraftRevision()); saved.setSavedAt(form.getDraftSavedAt()); return saved;
    }
    @Transactional(rollbackFor=Exception.class)
    public void submit(String token,Submit request) {
        withLockedToken(token,form->{
            if ("SUBMITTED".equals(form.getStatus())) return null;
            if (!"DRAFT".equals(effectiveState(form))) throw exception(LINK_EXPIRED);
            if (!Objects.equals(request.getRevision(), form.getDraftRevision() == null ? 0L : form.getDraftRevision())) throw exception(DRAFT_CONFLICT);
            var definitions=definitions(form); images.validateSubmission(form,definitions,request.getValues());
            var draft = draft(form);
            for (var answer:fields.validateCollectionValues(definitions,request.getValues())) {
                var saved = draft.getSelections().get(answer.getFieldKey());
                if (saved != null && Objects.equals(saved.getCode(),answer.getValueCode()) && Objects.equals(saved.getDictType(),answer.getDictType()))
                    answer.setValueLabelSnapshot(saved.getLabel());
                answer.setFormId(form.getId()); answer.setTenantId(form.getTenantId()); values.insert(answer);
            }
            forms.update(null,new LambdaUpdateWrapper<StudentInfoFormDO>().eq(StudentInfoFormDO::getId,form.getId()).eq(StudentInfoFormDO::getStatus,"DRAFT")
                    .set(StudentInfoFormDO::getStatus,"SUBMITTED").set(StudentInfoFormDO::getSubmittedAt,LocalDateTime.now()).set(StudentInfoFormDO::getSubmitSource,"public")
                    .set(StudentInfoFormDO::getDraftSnapshot,null).set(StudentInfoFormDO::getDraftSavedAt,null)); return null;
        });
    }
    public <T> T withLockedToken(String token,java.util.function.Function<StudentInfoFormDO,T> action) {
        var located=locate(token); return inTenant(located.getTenantId(),()->{
            lockCase(located.getRegistrationCaseId()); var form=forms.lock(located.getId());
            if (form==null || !hash(token).equals(form.getTokenHash())) throw exception(LINK_INVALID); return action.apply(form);
        });
    }
    public List<Field> definitions(StudentInfoFormDO form) {
        var definitions = fields.parse(form.getFieldsSnapshot());
        // Apply the approved format expansion to pending links without rewriting issued or submitted snapshots.
        return "DRAFT".equals(form.getStatus()) ? fields.withAttachmentPolicy(definitions) : definitions;
    }
    private StudentInfoFormDO locate(String token) {
        String digest=hash(token); Boolean old=TenantContextHolder.isIgnore();
        try { TenantContextHolder.setIgnore(true); var form=forms.byToken(digest); if (form==null) throw exception(LINK_INVALID); tenants.validTenant(form.getTenantId()); return form; }
        finally { TenantContextHolder.setIgnore(old); }
    }
    private <T> T inTenant(Long tenant,Supplier<T> action) {
        Long old=TenantContextHolder.getTenantId(); Boolean ignore=TenantContextHolder.isIgnore();
        try { TenantContextHolder.setTenantId(tenant); TenantContextHolder.setIgnore(false); return action.get(); }
        finally { TenantContextHolder.setTenantId(old); TenantContextHolder.setIgnore(ignore); }
    }
    private RegistrationCaseDO lockCase(Long id) {
        var item=cases.selectById(id); if (item==null) throw exception(LINK_INVALID);
        orders.selectByIdForUpdate(item.getOrderId(),TenantContextHolder.getRequiredTenantId());
        item=cases.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId()); if (item==null) throw exception(LINK_INVALID); return item;
    }
    public String effectiveState(StudentInfoFormDO form) {
        if (form==null) return "NONE"; if ("SUBMITTED".equals(form.getStatus())) return "SUBMITTED";
        var item=cases.selectById(form.getRegistrationCaseId());
        var order=item==null ? null : orders.selectById(item.getOrderId());
        return item==null || order==null || Set.of("cancelled","ended").contains(item.getStatus()) || !Set.of("pending_approval","revision_required","effective").contains(order.getStatus()) ? "REVOKED" : state(form);
    }
    private Link link(StudentInfoFormDO form,RegistrationCaseDO item) {
        if (form==null) return null; var result=new Link(); result.setFormId(form.getId()); result.setStatus(effectiveState(form));
        result.setCreatedAt(form.getCreateTime()); result.setExpiresAt(form.getExpiresAt()); result.setSentAt(form.getSentAt()); result.setSentByUserId(form.getSentByUserId()); result.setRegistrationVersion(item.getVersion());
        boolean editable=Set.of("pending","processing").contains(item.getStatus()) && !"SUBMITTED".equals(form.getStatus());
        result.setCanRegenerate(editable && security.hasPermission(REGENERATE)); result.setCanRevoke(editable && "DRAFT".equals(result.getStatus()) && security.hasPermission(REVOKE));
        if ("DRAFT".equals(result.getStatus())) result.setUrl(baseUrl()+"/student-info-form#token="+form.getTokenCiphertext()); return result;
    }
    private String baseUrl() {
        try { String value=publicBaseUrl==null ? "" : publicBaseUrl.trim().replaceAll("/+$",""); var uri=URI.create(value);
            if (uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw exception(URL_INVALID); return value;
        } catch (IllegalArgumentException error) { throw exception(URL_INVALID); }
    }
    static String state(StudentInfoFormDO form) { return "DRAFT".equals(form.getStatus()) && (form.getRevokedAt()!=null || form.getExpiresAt()==null || !form.getExpiresAt().isAfter(LocalDateTime.now())) ? "EXPIRED" : form.getStatus(); }
    static String hash(String token) { if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw exception(LINK_INVALID); return DigestUtil.sha256Hex(token); }
}

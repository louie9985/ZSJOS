package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentInfoConfigService {
    @Resource private StudentInfoFormConfigMapper mapper;
    @Resource private StudentInfoFields fields;
    @Resource private StudentInfoProductRules productRules;
    public Config get() {
        Config result = new Config();
        result.setDraft(convert(mapper.state("DRAFT")));
        result.setPublished(convert(mapper.state("PUBLISHED")));
        result.setPresets(fields.presets());
        return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public Version save(Save request) {
        lock();
        var definitions=fields.validateConfig(fields.upgradeConfig(request.getFields()),false);
        var categoryIds=productRules.validate(request.getEducationCategoryIds());
        StudentInfoFormConfigDO row=mapper.state("DRAFT");
        if (request.getId()==null) {
            if (row!=null || request.getRevision()!=0) throw exception(VERSION_CONFLICT);
            row=new StudentInfoFormConfigDO();
            var latest=mapper.latest();
            row.setVersionNo(latest==null?1:latest.getVersionNo()+1);
            row.setRevision(0); row.setStatus("DRAFT");
            row.setFieldsJson(JsonUtils.toJsonString(definitions));
            row.setEducationCategoryIdsJson(JsonUtils.toJsonString(categoryIds));
            mapper.insert(row);
        } else {
            if (row==null || !row.getId().equals(request.getId()) || !Objects.equals(row.getRevision(),request.getRevision()))
                throw exception(VERSION_CONFLICT);
            row.setFieldsJson(JsonUtils.toJsonString(definitions)); row.setRevision(row.getRevision()+1);
            row.setEducationCategoryIdsJson(JsonUtils.toJsonString(categoryIds));
            mapper.updateById(row);
        }
        return convert(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public void publish(Publish request) {
        lock();
        var row=mapper.state("DRAFT");
        if (row==null || !row.getId().equals(request.getId()) || !Objects.equals(row.getRevision(),request.getRevision()))
            throw exception(VERSION_CONFLICT);
        row.setFieldsJson(JsonUtils.toJsonString(fields.validateConfig(fields.upgradeConfig(fields.parse(row.getFieldsJson())),true)));
        validateConditions(row);
        var old=mapper.state("PUBLISHED");
        if (old!=null) { old.setStatus("ARCHIVED"); mapper.updateById(old); }
        row.setStatus("PUBLISHED"); row.setPublishedAt(LocalDateTime.now()); row.setRevision(row.getRevision()+1);
        mapper.updateById(row);
    }
    public StudentInfoFormConfigDO published() {
        var row=mapper.state("PUBLISHED");
        if (row==null) throw exception(CONFIG_MISSING);
        fields.validateConfig(fields.parse(row.getFieldsJson()),true);
        validateConditions(row);
        return row;
    }
    public Version preview(Save req) {
        Version result=new Version();
        var definitions=fields.validateConfig(req.getFields(),false);
        var roots=productRules.validate(req.getEducationCategoryIds());
        result.setFields(req.getProductId()==null ? definitions : productRules.resolve(definitions,roots,productRules.path(req.getProductId())));
        result.setEducationCategoryIds(roots); return result;
    }
    private void validateConditions(StudentInfoFormConfigDO row) {
        var ids=categoryIds(row); productRules.validate(ids);
        if (!ids.isEmpty() && fields.parse(row.getFieldsJson()).stream().anyMatch(f -> StudentInfoProductRules.CONDITIONAL.contains(f.getKey()) && !Boolean.TRUE.equals(f.getEnabled())))
            throw exception(CONFIG_INVALID);
    }
    public java.util.List<Long> categoryIds(StudentInfoFormConfigDO row) {
        return row.getEducationCategoryIdsJson()==null ? java.util.List.of() : JsonUtils.parseArray(row.getEducationCategoryIdsJson(),Long.class);
    }
    private void lock() {
        Long tenant=TenantContextHolder.getRequiredTenantId(); mapper.ensureLock(tenant); mapper.lockTenant(tenant);
    }
    public Version convert(StudentInfoFormConfigDO row) {
        if (row==null) return null;
        Version result=new Version(); result.setId(row.getId()); result.setVersionNo(row.getVersionNo());
        result.setRevision(row.getRevision()); result.setStatus(row.getStatus()); result.setPublishedAt(row.getPublishedAt());
        result.setFields(fields.upgradeConfig(fields.parse(row.getFieldsJson()))); result.setEducationCategoryIds(categoryIds(row)); return result;
    }
}

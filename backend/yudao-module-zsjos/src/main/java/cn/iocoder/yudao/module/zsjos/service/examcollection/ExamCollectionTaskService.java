package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormConfigMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.CONFIG_MISSING;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.URL_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Service
public class ExamCollectionTaskService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionBindingMapper bindings;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private PermissionApi permissions;
    @Resource private ExamCollectionTokens tokens;
    @Resource private ExamCollectionPermissionProvider objectPermissions;
    @Value("${zsjos.student-info.public-base-url:${zsjos.positioning.public-base-url:${ZSJOS_PUBLIC_H5_BASE_URL:}}}") private String publicBaseUrl;

    private void require(String permission) {
        TenantContextHolder.getRequiredTenantId();
        if (!permissions.hasAnyPermissions(getLoginUserId(), QUERY)
                || !permissions.hasAnyPermissions(getLoginUserId(), permission)) throw exception(DENIED);
    }
    public List<Field> fields() {
        require(QUERY);
        var config = configs.state("PUBLISHED");
        if (config == null) throw exception(CONFIG_MISSING);
        return ExamCollectionFields.selectable(JsonUtils.parseArray(config.getFieldsJson(), Field.class));
    }
    @Transactional(rollbackFor=Exception.class)
    public Detail create(Create request) {
        require(CREATE);
        var config = configs.state("PUBLISHED");
        if (config == null) throw exception(CONFIG_MISSING);
        var snapshot = ExamCollectionFields.snapshot(JsonUtils.parseArray(config.getFieldsJson(), Field.class), request.getSelectedFields());
        String token = tokens.create();
        publicUrl(token); // Fail configuration checks before creating an unusable task.
        tokens.identityHash("configuration-check");
        var row = new ExamCollectionTaskDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId()); row.setTitle(request.getTitle().trim());
        row.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        row.setStatus(OPEN); row.setConfigVersionId(config.getId()); row.setFieldsSnapshot(JsonUtils.toJsonString(snapshot));
        row.setTokenHash(tokens.hash(token)); row.setTokenCiphertext(token); row.setRevision(0); row.setSequenceNo(0L);
        tasks.insert(row);
        var detail = BeanUtils.toBean(row, Detail.class); detail.setCreatedAt(row.getCreateTime());
        detail.setFields(snapshot); detail.setUrl(publicUrl(token)); return detail;
    }
    public PageResult<Task> page(Query query) {
        require(QUERY);
        var result = tasks.selectPage(query, new LambdaQueryWrapperX<ExamCollectionTaskDO>()
                .likeIfPresent(ExamCollectionTaskDO::getTitle, query.getKeyword())
                .eqIfPresent(ExamCollectionTaskDO::getStatus, query.getStatus())
                .orderByDesc(ExamCollectionTaskDO::getId));
        return new PageResult<>(result.getList().stream().map(this::project).toList(), result.getTotal());
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#id", action="query")
    public Detail detail(Long id) {
        objectPermissions.check(id, "query", getLoginUserId());
        var row = tasks.selectById(id);
        var result = BeanUtils.toBean(project(row), Detail.class);
        result.setFields(JsonUtils.parseArray(row.getFieldsSnapshot(), Field.class));
        result.setClassIds(row.getClassIdsJson() == null ? List.of() : JsonUtils.parseArray(row.getClassIdsJson(), Long.class));
        result.setUrl(publicUrl(row.getTokenCiphertext())); return result;
    }
    private Task project(ExamCollectionTaskDO row) {
        var result = BeanUtils.toBean(row, Task.class); result.setCreatedAt(row.getCreateTime());
        var roster = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                .eq(ExamCollectionMemberDO::getTaskId, row.getId()).eq(ExamCollectionMemberDO::getActive, true));
        result.setExpected(roster.size());
        result.setCollected(roster.stream().filter(m -> m.getLastSubmissionId() != null).count());
        result.setExceptions(submissions.selectCount(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, row.getId()).eq(ExamCollectionSubmissionDO::getStatus, EXCEPTION)));
        var expected = roster.stream().map(ExamCollectionMemberDO::getServiceRelationId).collect(java.util.stream.Collectors.toSet());
        result.setAdditional(bindings.selectList(new LambdaQueryWrapperX<ExamCollectionBindingDO>()
                .eq(ExamCollectionBindingDO::getTaskId, row.getId()).isNotNull(ExamCollectionBindingDO::getLastSubmissionId))
                .stream().map(ExamCollectionBindingDO::getServiceRelationId).distinct().filter(id -> !expected.contains(id)).count());
        return result;
    }
    String publicUrl(String token) {
        try {
            var uri = URI.create(publicBaseUrl == null ? "" : publicBaseUrl.trim());
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw exception(URL_INVALID);
            return uri.toString().replaceAll("/+$", "") + "/exam-collection/" + token;
        } catch (IllegalArgumentException error) { throw exception(URL_INVALID); }
    }
}

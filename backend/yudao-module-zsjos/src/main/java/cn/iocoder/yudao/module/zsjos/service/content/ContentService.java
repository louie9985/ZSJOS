package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ContentService {
    @Resource private ContentMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ContentObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private DictDataApi dictDataApi;

    public PageResult<ContentRespVO> page(ContentPageReqVO req, Long userId) {
        MediaDataScopeService.Scope scope = dataScopeService.resolve(userId, "zsjos:content:query-all");
        PageResult<ContentDO> page = mapper.selectPage(req, scope.userIds(), scope.all());
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ContentSaveReqVO req, Long userId) {
        if (accountMapper.selectById(req.getAccountId()) == null) throw exception(CONTENT_ACCOUNT_INVALID);
        ContentDO content = new ContentDO();
        content.setContentNo("CT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        content.setAccountId(req.getAccountId());
        content.setTitle(req.getTitle());
        content.setTopic(req.getTopic());
        content.setContentClassValue(req.getContentClassValue());
        content.setContentClassLabelSnapshot(requireContentClassLabel(req.getContentClassValue()));
        content.setStatus(CONTENT_TOPIC);
        content.setCurrentVersionNo(1);
        content.setOwnerOperatorUserId(userId);
        content.setRejectCount(0);
        content.setVersion(0);
        mapper.insert(content);
        return content.getId();
    }

    private String requireContentClassLabel(String value) {
        dictDataApi.validateDictDataList("zsjos_content_class", List.of(value));
        return dictDataApi.getDictDataList("zsjos_content_class").stream()
                .filter(item -> java.util.Objects.equals(item.getValue(), value))
                .map(DictDataRespDTO::getLabel).findFirst()
                .orElseThrow(() -> exception(CONTENT_CLASS_INVALID));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "read")
    public ContentRespVO get(Long id, Long userId) {
        return toResp(require(id), userId);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "complete-topic")
    @Transactional(rollbackFor = Exception.class)
    public void completeTopic(Long id, Integer version) { transition(id, version, CONTENT_TOPIC, CONTENT_SCRIPT); }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "submit-production")
    @Transactional(rollbackFor = Exception.class)
    public void submitProduction(Long id, Integer version) { transition(id, version, CONTENT_SCRIPT, CONTENT_IN_PRODUCTION); }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "submit-acceptance")
    @Transactional(rollbackFor = Exception.class)
    public void submitAcceptance(Long id, Integer version) { transition(id, version, CONTENT_IN_PRODUCTION, CONTENT_ACCEPTANCE); }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "acceptance-review")
    @Transactional(rollbackFor = Exception.class)
    public void approveAcceptance(Long id, Integer version) { transition(id, version, CONTENT_ACCEPTANCE, CONTENT_PUBLISHED); }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "acceptance-review")
    @Transactional(rollbackFor = Exception.class)
    public void rejectAcceptance(Long id, Integer version, String reason) {
        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason == null || normalizedReason.isEmpty() || normalizedReason.length() > 500) {
            throw exception(CONTENT_REJECT_REASON_REQUIRED);
        }
        transition(id, version, CONTENT_ACCEPTANCE, CONTENT_REJECTED, normalizedReason);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "revise")
    @Transactional(rollbackFor = Exception.class)
    public void startRevision(Long id, Integer version) { transition(id, version, CONTENT_REJECTED, CONTENT_REVISING); }

    @ZsjosPermission(bizType = BIZ_TYPE_CONTENT, bizId = "#id", action = "resubmit-production")
    @Transactional(rollbackFor = Exception.class)
    public void resubmitProduction(Long id, Integer version) { transition(id, version, CONTENT_REVISING, CONTENT_IN_PRODUCTION); }

    public ContentDO require(Long id) {
        ContentDO content = mapper.selectById(id);
        if (content == null) throw exception(CONTENT_NOT_EXISTS);
        return content;
    }

    int advanceCurrentVersion(Long id, Integer version, Integer nextVersion) {
        return mapper.advanceCurrentVersion(id, version, nextVersion);
    }

    private void transition(Long id, Integer version, String expected, String target) {
        transition(id, version, expected, target, null);
    }

    private void transition(Long id, Integer version, String expected, String target, String reason) {
        ContentDO content = require(id);
        if (!expected.equals(content.getStatus())) throw exception(CONTENT_STATE_INVALID);
        int updated = CONTENT_REJECTED.equals(target)
                ? mapper.rejectTransition(id, version, expected, target)
                : mapper.transition(id, version, expected, target);
        if (updated == 0) throw exception(CONTENT_VERSION_CONFLICT);
        Long operator = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        workflowEventService.transition(BIZ_TYPE_CONTENT,id,operator,expected,target,reason,"content:"+id+":"+version+":"+target);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("bizNo", content.getContentNo());
        var linkedAccount = accountMapper.selectById(content.getAccountId());
        if (linkedAccount != null && linkedAccount.getStudentPersonId() != null) {
            payload.put("deepLink", "/zsjos/media-students?personId=" + linkedAccount.getStudentPersonId()
                    + "&tab=content&contentId=" + id);
        }
        if (CONTENT_ACCEPTANCE.equals(target)) {
            workflowEventService.createTaskAndNotify("media.content.pending_acceptance", "MEDIA_CONTENT_ACCEPTANCE",
                    BIZ_TYPE_CONTENT, id, content.getOwnerOperatorUserId(), "内容待验收", ACTION_APPROVE_CONTENT,
                    operator, "content-acceptance:" + id + ":" + version, payload);
        }
        if (CONTENT_PUBLISHED.equals(target) || CONTENT_REJECTED.equals(target)) {
            workflowEventService.completeTask("MEDIA_CONTENT_ACCEPTANCE", id, content.getOwnerOperatorUserId());
            Long recipient = resolveExecutionRecipient(content);
            String scene = CONTENT_PUBLISHED.equals(target) ? "media.content.approved" : "media.content.rejected";
            workflowEventService.notify(scene, BIZ_TYPE_CONTENT, id, recipient, operator,
                    "content-result:" + id + ":" + version + ":" + target, payload);
        }
    }

    private Long resolveExecutionRecipient(ContentDO content) {
        if (content.getFilmingEditorUserId() != null) return content.getFilmingEditorUserId();
        var account = accountMapper.selectById(content.getAccountId());
        if (account != null && account.getDirectorUserId() != null) return account.getDirectorUserId();
        return content.getOwnerOperatorUserId();
    }

    private ContentRespVO toResp(ContentDO content, Long userId) {
        ContentRespVO response = BeanUtils.toBean(content, ContentRespVO.class);
        if (!objectPermissionProvider.hasPermission(content.getId(), "read", userId)) {
            response.setAvailableActions(List.of()); return response;
        }
        response.setAvailableActions(availableActionsForVisible(content, userId, true));
        return response;
    }

    public List<String> availableActionsForVisible(ContentDO content, Long userId, boolean objectAuthorized) {
        if (!objectAuthorized) return List.of();
        String permission = switch (content.getStatus()) {
            case CONTENT_TOPIC -> "zsjos:content:complete-topic";
            case CONTENT_SCRIPT -> "zsjos:content:submit-production";
            case CONTENT_IN_PRODUCTION -> "zsjos:content:submit-acceptance";
            case CONTENT_ACCEPTANCE -> "zsjos:content:acceptance-review";
            case CONTENT_REJECTED -> "zsjos:content:revise";
            case CONTENT_REVISING -> "zsjos:content:resubmit-production";
            default -> null;
        };
        if (permission == null || !permissionApi.hasAnyPermissions(userId, permission)) {
            return List.of();
        }
        return switch (content.getStatus()) {
            case CONTENT_TOPIC -> List.of(ACTION_COMPLETE_TOPIC);
            case CONTENT_SCRIPT -> List.of(ACTION_SUBMIT_PRODUCTION);
            case CONTENT_IN_PRODUCTION -> List.of(ACTION_SUBMIT_ACCEPTANCE);
            case CONTENT_ACCEPTANCE -> List.of(ACTION_APPROVE_CONTENT, ACTION_REJECT_CONTENT);
            case CONTENT_REJECTED -> List.of(ACTION_START_CONTENT_REVISION);
            case CONTENT_REVISING -> List.of(ACTION_RESUBMIT_PRODUCTION);
            default -> List.of();
        };
    }
}

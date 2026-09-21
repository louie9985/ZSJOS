package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidatePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCompleteReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewPublishReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentDraftCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionFileRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.content.ContentObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.content.ContentPackageValidator;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_APPROVE;
import static cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_REJECT;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_ACCEPTANCE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_IN_PRODUCTION;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_READY_TO_PUBLISH;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_SCRIPT;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_TOPIC;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ContentReviewBatchService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ContentReviewBatchService.class);

    public static final String BUSINESS_KEY_PREFIX = "content-review-batch:";
    private static final int FILE_PREVIEW_SECONDS = 3600;
    /** SIMPLE 设计器为发起人生成的提交节点编码，由引擎自动通过，不是业务审批节点。 */
    private static final String SIMPLE_SUBMISSION_TASK_KEY = "StartUserNode";
    private static final int ACCOUNT_PROFILE_TEXT_MAX = 200;
    private static final String DICT_ACCOUNT_PLATFORM = "zsjos_account_platform";
    private static final String DICT_ACCOUNT_CURRENT_STATUS = "zsjos_media_account_current_status";
    private static final String DICT_ACCOUNT_STAGE = "zsjos_media_account_stage";

    @Resource private ContentReviewBatchMapper batchMapper;
    @Resource private ContentReviewBatchItemMapper itemMapper;
    @Resource private ContentReviewRelationMapper relationMapper;
    @Resource private ContentMapper contentMapper;
    @Resource private ContentVersionMapper contentVersionMapper;
    @Resource private ContentVersionFileMapper contentVersionFileMapper;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private ContentReviewConfigService configService;
    @Resource private ContentReviewMaterialService reviewMaterialService;
    @Resource private ContentReviewAccessService accessService;
    @Resource private ContentService contentService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.content.ContentVersionService contentVersionService;
    @Resource private ContentObjectPermissionProvider contentPermissionProvider;
    @Resource private MaterialService materialService;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private PermissionApi permissionApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;
    @Resource private ContentReviewNotifyPublisher notifyPublisher;

    public PageResult<ContentReviewBatchRespVO> page(ContentReviewBatchPageReqVO request, Long userId) {
        boolean seeAll = canSeeAll(userId);
        Set<String> currentTaskProcessInstanceIds = seeAll ? Set.of()
                : accessService.getCurrentTaskProcessInstanceIds(userId,
                batchMapper.selectActiveProcessDefinitionKeys(tenantId()));
        PageResult<ContentReviewBatchDO> page = batchMapper.selectPage(request, userId, seeAll,
                currentTaskProcessInstanceIds);
        if (page.getList().isEmpty()) return PageResult.empty(page.getTotal());
        Map<Long, List<ContentReviewBatchItemDO>> items = itemMapper.selectByBatchIds(
                        page.getList().stream().map(ContentReviewBatchDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ContentReviewBatchItemDO::getBatchId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, BpmTaskRespDTO> tasks = loadCurrentTasks(page.getList(), userId);
        return new PageResult<>(toResponses(page.getList(), items, tasks, userId), page.getTotal());
    }

    public PageResult<ContentReviewCandidateRespVO> candidatePage(ContentReviewCandidatePageReqVO request,
                                                                  Long userId) {
        return contentMapper.selectReviewCandidatePage(request, userId, tenantId());
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "read")
    public ContentReviewBatchRespVO get(Long batchId, Long userId) {
        ContentReviewBatchDO batch = requireBatch(batchId);
        List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batchId);
        Map<String, BpmTaskRespDTO> tasks = loadCurrentTasks(List.of(batch), userId);
        return toResponse(batch, items, loadContentRecords(items),
                tasks.get(batch.getProcessInstanceId()), userId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ContentReviewBatchCreateReqVO request, Long userId) {
        List<Long> versionIds = request.getContentVersionIds().stream().filter(Objects::nonNull).distinct().toList();
        if (versionIds.size() != request.getContentVersionIds().size() || versionIds.isEmpty()
                || versionIds.size() > 20) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        List<ContentVersionDO> versions = new ArrayList<>();
        for (Long versionId : versionIds.stream().sorted().toList()) {
            ContentVersionDO version = contentVersionMapper.selectByIdForUpdate(versionId, tenantId());
            if (version == null) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            versions.add(version);
        }
        if (versions.size() != versionIds.size()) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        Map<Long, ContentVersionDO> versionMap = versions.stream()
                .collect(Collectors.toMap(ContentVersionDO::getId, Function.identity()));
        List<ContentDO> contents = contentMapper.selectByIds(versions.stream()
                .map(ContentVersionDO::getContentId).collect(Collectors.toSet()));
        Map<Long, ContentDO> contentMap = contents.stream()
                .collect(Collectors.toMap(ContentDO::getId, Function.identity()));
        List<ContentVersionDO> orderedVersions = versionIds.stream().map(versionMap::get).toList();
        BatchSubjects subjects = validateSubjects(orderedVersions, contentMap, userId, null,
                request.getStudentPersonId() == null);
        if (itemMapper.countActiveByContentVersions(versionIds) > 0) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        MediaAccountDO account = subjects.account();
        ContentReviewBatchDO batch = new ContentReviewBatchDO();
        batch.setBatchNo(nextBatchNo());
        batch.setAccountId(account.getId());
        if (request.getStudentPersonId() != null) batch.setStudentPersonId(request.getStudentPersonId());
        List<Long> selectedAccountIds = request.getAccountIds() == null || request.getAccountIds().isEmpty() ? List.of(account.getId()) : request.getAccountIds().stream().filter(Objects::nonNull).distinct().toList();
        if (selectedAccountIds.size() > 20) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        batch.setAccountIdsJson(JsonUtils.toJsonString(selectedAccountIds));
        batch.setOperatorUserId(userId);
        batch.setDirectorUserId(null);
        batch.setRelationSnapshotJson(null);
        batch.setContextSnapshotJson(null);
        batch.setStatus(BATCH_DRAFT);
        batch.setCurrentStage(STAGE_DRAFT);
        batch.setVersion(0);
        batchMapper.insert(batch);

        int sortNo = 1;
        for (ContentVersionDO version : orderedVersions) {
            ContentDO content = contentMap.get(version.getContentId());
            ContentReviewBatchItemDO item = new ContentReviewBatchItemDO();
            item.setBatchId(batch.getId());
            item.setContentId(content.getId());
            item.setContentVersionId(version.getId());
            item.setSortNo(sortNo++);
            item.setContentSnapshotJson(JsonUtils.toJsonString(contentSnapshot(content, version)));
            item.setCollectMaterial(false);
            item.setVersion(0);
            itemMapper.insert(item);
        }
        return batch.getId();
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "read")
    public List<ContentReviewBatchRespVO> history(Long batchId, Long userId) {
        ContentReviewBatchDO current = requireBatch(batchId);
        List<ContentReviewBatchRespVO> result = new ArrayList<>();
        // Return the complete revision chain: ancestors first, then descendants.
        List<ContentReviewBatchDO> ancestors = new ArrayList<>();
        Long parentId = current.getRevisionOfBatchId();
        while (parentId != null && ancestors.size() < 20) {
            ContentReviewBatchDO parent = batchMapper.selectById(parentId);
            if (parent == null) break;
            ancestors.add(0, parent);
            parentId = parent.getRevisionOfBatchId();
        }
        List<ContentReviewBatchDO> queue = new ArrayList<>(ancestors);
        queue.add(current);
        queue.addAll(batchMapper.selectByRevisionOfBatchId(batchId));
        while (!queue.isEmpty() && result.size() < 20) {
            ContentReviewBatchDO row = queue.removeFirst();
            List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(row.getId());
            Map<String, BpmTaskRespDTO> tasks = loadCurrentTasks(List.of(row), userId);
            result.add(toResponse(row, items, loadContentRecords(items),
                    tasks.get(row.getProcessInstanceId()), userId, true));
            queue.addAll(batchMapper.selectByRevisionOfBatchId(row.getId()));
        }
        return result;
    }

    /**
     * Creates the content records and their first editable versions before creating a draft batch.
     * The operation deliberately does not freeze versions or start BPM; both happen in submit().
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromStudent(ContentReviewStudentDraftCreateReqVO request, Long userId) {
        List<Long> accountIds = request.getAccountIds().stream().filter(Objects::nonNull).distinct().toList();
        List<ContentReviewStudentDraftCreateReqVO.Work> works = request.getWorks().stream()
                .filter(Objects::nonNull).toList();
        if (accountIds.isEmpty() || accountIds.size() > 20 || works.isEmpty() || works.size() > 20) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        // Keep account selection within the student and current operator's data scope.  The director
        // is frozen later by submit(), after resolving the current enabled operator relation.
        List<MediaAccountDO> accounts = accountIds.stream().map(id ->
                accountMapper.selectByIdForUpdate(id, tenantId())).toList();
        if (accounts.stream().anyMatch(Objects::isNull)
                || accounts.stream().anyMatch(a -> !Objects.equals(a.getStudentPersonId(), request.getStudentPersonId())
                || !Objects.equals(a.getOwnerOperatorUserId(), userId))) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        Set<Long> directors = accounts.stream().map(MediaAccountDO::getDirectorUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (directors.size() != 1) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);

        Map<String, String> purposeLabels = dictionaryLabels("zsjos_content_purpose");
        Map<String, String> formatLabels = dictionaryLabels("zsjos_content_format");
        LocalDateTime now = LocalDateTime.now();

        // The current review schema has one account owner per content record.  We create the shared
        // works against the first selected account and retain the complete account set in the batch
        // snapshot; the selected account set is therefore still available to the review UI/BPM.
        Long primaryAccountId = accountIds.getFirst();
        List<Long> versionIds = new ArrayList<>(works.size());
        List<Long> contentIds = new ArrayList<>(works.size());
        for (int index = 0; index < works.size(); index++) {
            ContentReviewStudentDraftCreateReqVO.Work work = works.get(index);
            if (work.getCoverFileId() == null && blank(work.getCoverSnapshotJson())) {
                throw exception(CONTENT_VERSION_FILE_INVALID);
            }
            if (work.getPlannedPublishAt() == null || work.getPlannedPublishAt().isBefore(now)) {
                throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            }
            String purposeLabel = resolveDictionaryLabel(work.getPurposeValue(), purposeLabels);
            String formatLabel = resolveDictionaryLabel(work.getFormatValue(), formatLabels);
            if (work.getSourceContentId() != null && work.getSourceVersionId() != null) {
                ContentVersionDO source = contentVersionMapper.selectByIdForUpdate(work.getSourceVersionId(), tenantId());
                ContentDO sourceContent = source == null ? null : contentMapper.selectById(source.getContentId());
                if (source == null || sourceContent == null || !Objects.equals(source.getContentId(), work.getSourceContentId())
                        || !Objects.equals(sourceContent.getCurrentVersionNo(), source.getVersionNo())
                        || source.getFrozenAt() == null || source.getReviewDecision() == null) {
                    throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
                }
                ContentVersionSaveReqVO changes = new ContentVersionSaveReqVO();
                changes.setTitleSnapshot(work.getTitle()); changes.setScriptText(work.getScriptText());
                changes.setCoverSnapshotJson(resolveCoverSnapshot(work)); changes.setPurposeValue(work.getPurposeValue());
                changes.setPurposeLabelSnapshot(purposeLabel); changes.setFormatValue(work.getFormatValue());
                changes.setFormatLabelSnapshot(formatLabel); changes.setDetailUrl(work.getDetailUrl());
                changes.setLeadResourceUrl(work.getLeadResourceUrl()); changes.setCommentHook(work.getCommentHook());
                changes.setReferenceContentVersionId(work.getReferenceContentVersionId());
                changes.setReferenceWorkUrl(work.getReferenceWorkUrl());
                changes.setMaterialRefsJson(referenceMaterialRefsJson(work));
                changes.setDeliverableSnapshotJson(work.getDeliverableSnapshotJson());
                changes.setPlannedPublishAt(work.getPlannedPublishAt());
                Long versionId = contentVersionService.copyForReview(source, changes, userId);
                contentIds.add(source.getContentId()); versionIds.add(versionId);
                continue;
            }
            ContentSaveReqVO contentRequest = new ContentSaveReqVO();
            contentRequest.setAccountId(primaryAccountId);
            contentRequest.setTitle(work.getTitle());
            contentRequest.setTopic(work.getTopic());
            contentRequest.setContentClassValue(blank(work.getContentClassValue()) ? "daily" : work.getContentClassValue());
            contentRequest.setContentClassLabelSnapshot(work.getContentClassLabelSnapshot());
            contentRequest.setPurposeValue(work.getPurposeValue());
            contentRequest.setPurposeLabelSnapshot(purposeLabel);
            contentRequest.setFormatValue(work.getFormatValue());
            contentRequest.setFormatLabelSnapshot(formatLabel);
            contentRequest.setDetailUrl(work.getDetailUrl());
            contentRequest.setScriptText(work.getScriptText());
            contentRequest.setLeadResourceUrl(work.getLeadResourceUrl());
            contentRequest.setPlannedPublishAt(work.getPlannedPublishAt());
            Long contentId = contentService.create(contentRequest, userId);
            contentIds.add(contentId);

            ContentVersionSaveReqVO versionRequest = new ContentVersionSaveReqVO();
            versionRequest.setContentId(contentId);
            versionRequest.setTitleSnapshot(work.getTitle());
            versionRequest.setTopicSnapshot(work.getTopic());
            versionRequest.setCoverSnapshotJson(resolveCoverSnapshot(work));
            versionRequest.setMaterialRefsJson(referenceMaterialRefsJson(work));
            versionRequest.setReferenceContentVersionId(work.getReferenceContentVersionId());
            versionRequest.setReferenceWorkUrl(work.getReferenceWorkUrl());
            versionRequest.setDeliverableUrl(work.getDeliverableUrl());
            versionRequest.setDeliverableSnapshotJson(work.getDeliverableSnapshotJson());
            versionRequest.setScriptText(work.getScriptText());
            versionRequest.setPurposeValue(work.getPurposeValue());
            versionRequest.setPurposeLabelSnapshot(purposeLabel);
            versionRequest.setFormatValue(work.getFormatValue());
            versionRequest.setFormatLabelSnapshot(formatLabel);
            versionRequest.setDetailUrl(work.getDetailUrl());
            versionRequest.setCommentHook(work.getCommentHook());
            versionRequest.setLeadResourceUrl(work.getLeadResourceUrl());
            versionRequest.setPlannedPublishAt(work.getPlannedPublishAt());
            versionRequest.setIdempotencyKey(work.getIdempotencyKey());
            versionIds.add(contentVersionService.create(versionRequest, userId));
        }
        // A review batch consumes content in the acceptance stage.  The editor creates the
        // initial topic record above; advance the newly created records through the normal
        // state machine before adding them to the batch, while leaving versions editable.
        for (Long contentId : contentIds) {
            ContentDO existing = contentMapper.selectById(contentId);
            if (existing != null && CONTENT_ACCEPTANCE.equals(existing.getStatus())) continue;
            if (contentMapper.transition(contentId, 1, CONTENT_TOPIC, CONTENT_SCRIPT) != 1
                    || contentMapper.transition(contentId, 2, CONTENT_SCRIPT, CONTENT_IN_PRODUCTION) != 1
                    || contentMapper.transition(contentId, 3, CONTENT_IN_PRODUCTION, CONTENT_ACCEPTANCE) != 1) {
                throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
            }
        }
        ContentReviewBatchCreateReqVO batchRequest = new ContentReviewBatchCreateReqVO();
        batchRequest.setContentVersionIds(versionIds);
        batchRequest.setStudentPersonId(request.getStudentPersonId());
        batchRequest.setAccountIds(accountIds);
        Long batchId = create(batchRequest, userId);
        List<ContentReviewBatchItemDO> createdItems = itemMapper.selectByBatchId(batchId);
        for (int i = 0; i < Math.min(createdItems.size(), works.size()); i++) {
            Long sourceVersion = works.get(i).getSourceVersionId();
            if (sourceVersion == null) continue;
            ContentReviewBatchItemDO previous = itemMapper.selectByContentVersionId(sourceVersion);
            if (previous != null) {
                createdItems.get(i).setPreviousItemId(previous.getId());
                itemMapper.updateById(createdItems.get(i));
            }
        }
        ContentReviewBatchDO batch = lockBatch(batchId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("studentPersonId", request.getStudentPersonId());
        context.put("accountIds", accountIds);
        context.put("accountSnapshots", accounts.stream().map(account -> draftAccountSnapshot(account,
                request.getAccountSnapshots() == null ? null : request.getAccountSnapshots().get(String.valueOf(account.getId())))).toList());
        batch.setContextSnapshotJson(JsonUtils.toJsonString(context));
        batchMapper.updateById(batch);
        return batchId;
    }

    private String resolveCoverSnapshot(ContentReviewStudentDraftCreateReqVO.Work work) {
        if (!blank(work.getCoverSnapshotJson())) return work.getCoverSnapshotJson();
        if (work.getCoverFileId() == null) return null;
        return JsonUtils.toJsonString(List.of(work.getCoverFileId()));
    }

    /** 参考素材只保存审批展示所需的快照，避免审批期间素材被改动或停用影响查看。 */
    private String referenceMaterialRefsJson(ContentReviewStudentDraftCreateReqVO.Work work) {
        if (work.getReferenceMaterials() == null || work.getReferenceMaterials().isEmpty()) return null;
        List<Map<String, Object>> references = new ArrayList<>(work.getReferenceMaterials().size());
        for (ContentReviewStudentDraftCreateReqVO.ReferenceMaterial material : work.getReferenceMaterials()) {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("materialId", material.getMaterialId());
            reference.put("materialVersionId", material.getMaterialVersionId());
            reference.put("materialNo", material.getMaterialNo());
            reference.put("title", material.getTitle());
            reference.put("materialTypeName", material.getMaterialTypeName());
            reference.put("coverPreviewUrl", material.getCoverPreviewUrl());
            references.add(reference);
        }
        return JsonUtils.toJsonString(references);
    }

    private Map<String, Object> draftAccountSnapshot(MediaAccountDO account, Map<String, Object> overrides) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", account.getId());
        snapshot.put("accountNo", account.getAccountNo());
        snapshot.put("platformValue", account.getPlatformValue());
        snapshot.put("platformLabel", account.getPlatformLabelSnapshot());
        snapshot.put("platformAccountId", account.getPlatformAccountId());
        snapshot.put("nickname", account.getNickname());
        snapshot.put("ownerOperatorUserId", account.getOwnerOperatorUserId());
        snapshot.put("directorUserId", account.getDirectorUserId());
        // 责任运营姓名由服务端按归属解析，不接受前端传入：归属是权限字段，不能由客户端改写。
        Set<Long> profileUserIds = java.util.stream.Stream.of(
                        account.getOwnerOperatorUserId(), account.getDirectorUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> profileUsers = profileUserIds.isEmpty()
                ? Map.of() : adminUserApi.getUserMap(profileUserIds);
        // getUserMap 可能返回不可变 Map，get(null) 会抛 NPE；账号未绑定编导时 id 就是 null。
        snapshot.put("operatorName", nickname(profileUsers, account.getOwnerOperatorUserId()));
        snapshot.put("directorName", nickname(profileUsers, account.getDirectorUserId()));
        snapshot.put("currentStatusValue", account.getCurrentStatusValue());
        snapshot.put("currentStatusLabel", account.getCurrentStatusLabelSnapshot());
        snapshot.put("sStage", account.getSStage());
        snapshot.put("sStageLabel", account.getSStageLabelSnapshot());
        snapshot.put("primaryProblems", parseJsonValue(account.getPrimaryProblemsJson()));
        // 运营在发起审核时补充的账号档案。这些是纯文本描述、没有字典约束，接受填写；
        // 身份、归属和编导字段仍由服务端持有，客户端无法伪造历史快照。
        applyText(snapshot, overrides, "productGoal");
        applyText(snapshot, overrides, "productFormLabel");
        applyText(snapshot, overrides, "publishFrequency");
        applyText(snapshot, overrides, "bottleneckLabel");
        applyText(snapshot, overrides, "nickname");
        // 字典字段只接受 value，label 一律由服务端按字典解析后写入，避免客户端伪造展示文案。
        applyDictionary(snapshot, overrides, "platformValue", "platformLabel", DICT_ACCOUNT_PLATFORM,
                account.getPlatformValue());
        applyDictionary(snapshot, overrides, "stageValue", "sStageLabel", DICT_ACCOUNT_STAGE,
                account.getSStage());
        applyDictionary(snapshot, overrides, "currentStatusValue", "currentStatusLabel",
                DICT_ACCOUNT_CURRENT_STATUS, account.getCurrentStatusValue());
        return snapshot;
    }

    private String nickname(Map<Long, AdminUserRespDTO> users, Long userId) {
        if (userId == null) return null;
        AdminUserRespDTO user = users.get(userId);
        return user == null ? null : user.getNickname();
    }

    /** 覆盖纯文本档案字段；空白视为未填写，保留服务端原值。 */
    private void applyText(Map<String, Object> snapshot, Map<String, Object> overrides, String key) {
        if (overrides == null) return;
        String value = trimToNull(text(overrides.get(key)));
        if (value == null) return;
        if (value.length() > ACCOUNT_PROFILE_TEXT_MAX) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        snapshot.put(key, value);
    }

    /**
     * 覆盖字典字段：只接受字典内的 value，并按字典解析 label 一起写入快照。
     * 未填写时沿用服务端原值对应的 label，保证快照始终自带可展示文案。
     */
    private void applyDictionary(Map<String, Object> snapshot, Map<String, Object> overrides,
                                 String valueKey, String labelKey, String dictType, String serverValue) {
        Map<String, String> labels = dictionaryLabels(dictType);
        String value = overrides == null ? null : trimToNull(text(overrides.get(valueKey)));
        if (value == null) {
            // 服务端原值也要补上 label，前端不必再查字典。
            if (serverValue != null && labels.containsKey(serverValue)) {
                snapshot.put(valueKey, serverValue);
                snapshot.put(labelKey, labels.get(serverValue));
            }
            return;
        }
        if (!labels.containsKey(value)) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        snapshot.put(valueKey, value);
        snapshot.put(labelKey, labels.get(value));
    }

    private Map<String, String> dictionaryLabels(String type) {
        return dictDataApi.getDictDataList(type).stream().collect(Collectors.toMap(
                DictDataRespDTO::getValue, DictDataRespDTO::getLabel, (left, right) -> right));
    }

    private String resolveDictionaryLabel(String value, Map<String, String> labels) {
        if (blank(value) || !labels.containsKey(value)) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        return labels.get(value);
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long batchId, ContentReviewBatchSubmitReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        if (!BATCH_DRAFT.equals(batch.getStatus()) || !STAGE_DRAFT.equals(batch.getCurrentStage())
                || !Objects.equals(batch.getVersion(), request.getExpectedVersion())
                || !Objects.equals(batch.getOperatorUserId(), userId)) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batchId);
        if (items.isEmpty() || items.size() > 20) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        Map<Long, ContentDO> contentMap = lockContents(items);
        List<ContentVersionDO> versions = lockVersions(items);
        BatchSubjects subjects = validateSubjects(versions, contentMap, userId, batch.getAccountId(),
                batch.getStudentPersonId() == null);
        if (itemMapper.countActiveByContentVersionsExcludingBatch(
                versions.stream().map(ContentVersionDO::getId).toList(), batchId) > 0) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        RelationContext relation = requireDirector(userId);
        validateSelectedAccountsAtSubmit(batch, relation.director().getId(), userId);
        ContentReviewConfigDO config = configService.requireReadyConfig(userId);
        BpmProcessDefinitionMetadataRespDTO definition = configService.requireCurrentDefinition(config);
        var materialSchema = configService.requireProductionMaterialSchema(config);
        String relationSnapshotJson = JsonUtils.toJsonString(relationSnapshot(relation, userId));
        Map<String, Object> frozenContext = contextSnapshot(config, subjects.account(), materialSchema);
        // Preserve the student and multi-account draft snapshot when submit freezes the
        // configuration context. Re-resolving account data here would rewrite history.
        Map<String, Object> draftContext = parseMap(batch.getContextSnapshotJson());
        if (draftContext.containsKey("studentPersonId")) frozenContext.put("studentPersonId", draftContext.get("studentPersonId"));
        if (draftContext.containsKey("accountIds")) frozenContext.put("accountIds", draftContext.get("accountIds"));
        if (draftContext.containsKey("accountSnapshots")) frozenContext.put("accountSnapshots", draftContext.get("accountSnapshots"));
        String contextSnapshotJson = JsonUtils.toJsonString(frozenContext);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ContentVersionDO> lockedVersionMap = versions.stream()
                .collect(Collectors.toMap(ContentVersionDO::getId, Function.identity()));
        for (ContentReviewBatchItemDO item : items) {
            ContentVersionDO version = lockedVersionMap.get(item.getContentVersionId());
            ContentDO content = contentMap.get(item.getContentId());
            item.setContentSnapshotJson(JsonUtils.toJsonString(contentSnapshot(content, version)));
            itemMapper.updateById(item);
            if (version.getFrozenAt() != null || contentVersionMapper.freeze(version.getId(), now) != 1) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
        }

        String businessKey = BUSINESS_KEY_PREFIX + batchId;
        String processInstanceId = UUID.randomUUID().toString().replace("-", "");
        Map<String, List<Long>> assignees = Map.of(ContentReviewConfigService.DIRECTOR_TASK_KEY,
                List.of(relation.director().getId()));
        BpmProcessInstanceCreateReqDTO processRequest = new BpmProcessInstanceCreateReqDTO();
        processRequest.setProcessDefinitionId(definition.getId());
        processRequest.setProcessDefinitionKey(definition.getKey());
        processRequest.setBusinessKey(businessKey);
        processRequest.setPredefinedProcessInstanceId(processInstanceId);
        processRequest.setStartUserSelectAssignees(assignees);
        processRequest.setVariables(Map.of("contentReviewBatchId", batchId,
                "contentReviewBatchNo", batch.getBatchNo(), "accountId", batch.getAccountId(),
                "accountIds", parseLongList(batch.getAccountIdsJson()),
                "operatorUserId", batch.getOperatorUserId(), "directorUserId", relation.director().getId()));
        if (batchMapper.markSubmitted(batch, request.getExpectedVersion(), processInstanceId,
                definition.getId(), definition.getKey(), definition.getVersion(), businessKey,
                relation.director().getId(), relationSnapshotJson, contextSnapshotJson, now) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        try {
            String createdProcessInstanceId = processInstanceApi.createProcessInstance(userId, processRequest);
            if (!Objects.equals(processInstanceId, createdProcessInstanceId)) {
                throw exception(CONTENT_REVIEW_PROCESS_UNAVAILABLE);
            }
            // 不在此处校验编导待办是否已生成：SIMPLE 流程先停在发起人提交节点，由引擎的任务分配事件
            // 异步自动通过后才流转到编导审核。在同一事务内必然查不到该待办，早期实现因此每次提交都
            // 抛"流程不可用"并回滚掉刚创建的实例。实例创建成功即视为启动成功。
        } catch (RuntimeException error) {
            // 统一对外返回"流程不可用"，但必须留下真实原因：这里吞掉的异常曾让排查只能看到一行调用帧。
            log.warn("[submit][批次({}) 启动流程失败 definitionKey={} definitionId={} director={}]",
                    batchId, definition.getKey(), definition.getId(), relation.director().getId(), error);
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceError
                    && serviceError.getCode() == CONTENT_REVIEW_PROCESS_UNAVAILABLE.getCode()) throw error;
            throw exception(CONTENT_REVIEW_PROCESS_UNAVAILABLE);
        }

        // 发送批次已提交通知
        notifyPublisher.publishBatchSubmitted(batchId, userId, now);
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public void cancelDraft(Long batchId, Integer expectedVersion, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        if (!BATCH_DRAFT.equals(batch.getStatus()) || !STAGE_DRAFT.equals(batch.getCurrentStage())
                || !Objects.equals(batch.getOperatorUserId(), userId)) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        if (batchMapper.cancelDraft(batch, expectedVersion, LocalDateTime.now()) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "director-review")
    @Transactional(rollbackFor = Exception.class)
    public void saveDirectorDecision(Long batchId, Long itemId, ContentReviewDecisionReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        requireStageAndTask(batch, BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR, "directorTaskKey",
                request.getTaskId(), userId);
        if (!Objects.equals(batch.getDirectorUserId(), userId)) throw exception(CONTENT_REVIEW_PERMISSION_DENIED);
        String decision = normalizeDecision(request.getDecision(), request.getComment());
        ContentReviewBatchItemDO item = lockItem(batchId, itemId);
        LocalDateTime now = LocalDateTime.now();
        if (itemMapper.updateDirectorDecision(itemId, batchId, request.getExpectedVersion(), decision,
                trimToNull(request.getComment()), userId, now) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }

        // 发送编导逐条决策通知
        notifyPublisher.publishDirectorItemDecision(batchId, itemId, decision,
                trimToNull(request.getComment()), userId, now);
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "final-review")
    @Transactional(rollbackFor = Exception.class)
    public void saveFinalDecision(Long batchId, Long itemId, ContentReviewDecisionReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        requireStageAndTask(batch, BATCH_FINAL_REVIEW, STAGE_FINAL, "finalTaskKey",
                request.getTaskId(), userId);
        String decision = normalizeDecision(request.getDecision(), request.getComment());
        ContentReviewBatchItemDO item = lockItem(batchId, itemId);
        if (!DECISION_APPROVED.equals(item.getDirectorDecision())) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        boolean collect = DECISION_APPROVED.equals(decision) && Boolean.TRUE.equals(request.getCollectMaterial());
        MaterialService.AutoCollectionSnapshot collectionSnapshot = collect ? prepareCollection(batch, item) : null;
        LocalDateTime now = LocalDateTime.now();
        if (itemMapper.updateFinalDecision(itemId, batchId, request.getExpectedVersion(), decision,
                trimToNull(request.getComment()), collect,
                collectionSnapshot == null ? null : JsonUtils.toJsonString(collectionSnapshot),
                userId, now) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }

        // 发送终审逐条决策通知
        notifyPublisher.publishFinalItemDecision(batchId, itemId, decision,
                trimToNull(request.getComment()), userId, now);
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "director-review")
    @Transactional(rollbackFor = Exception.class)
    public void completeDirector(Long batchId, ContentReviewCompleteReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        if (!Objects.equals(batch.getVersion(), request.getExpectedVersion())) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        requireStageAndTask(batch, BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR, "directorTaskKey",
                request.getTaskId(), userId);

        // 检查是否有退回的内容
        List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batchId);
        boolean hasReturned = items.stream()
                .anyMatch(item -> DECISION_RETURNED.equals(item.getDirectorDecision()));

        if (hasReturned) {
            // 有退回内容，直接驳回整个批次，不进入终审
            processTaskApi.rejectTask(userId, new BpmTaskDecisionReqDTO()
                    .setTaskId(request.getTaskId()).setReason(request.getReason()));
            // 发送编导驳回通知
            notifyPublisher.publishDirectorRejected(batchId, userId, request.getReason(), LocalDateTime.now());
        } else {
            // 全部通过，进入终审环节
            processTaskApi.approveTask(userId, new BpmTaskDecisionReqDTO()
                    .setTaskId(request.getTaskId()).setReason(request.getReason()));
            // 发送编导审核通过通知
            notifyPublisher.publishDirectorCompleted(batchId, userId, LocalDateTime.now());
        }
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "final-review")
    @Transactional(rollbackFor = Exception.class)
    public void completeFinal(Long batchId, ContentReviewCompleteReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        if (!Objects.equals(batch.getVersion(), request.getExpectedVersion())) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        requireStageAndTask(batch, BATCH_FINAL_REVIEW, STAGE_FINAL, "finalTaskKey",
                request.getTaskId(), userId);

        // 检查是否有退回的内容
        List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batchId);
        boolean hasReturned = items.stream()
                .anyMatch(item -> DECISION_RETURNED.equals(item.getFinalDecision()));

        LocalDateTime now = LocalDateTime.now();
        if (hasReturned) {
            // 有退回内容，驳回批次
            processTaskApi.rejectTask(userId, new BpmTaskDecisionReqDTO()
                    .setTaskId(request.getTaskId()).setReason(request.getReason()));
            // 发送终审驳回通知
            notifyPublisher.publishFinalRejected(batchId, userId, request.getReason(), now);
        } else {
            // 全部通过
            processTaskApi.approveTask(userId, new BpmTaskDecisionReqDTO()
                    .setTaskId(request.getTaskId()).setReason(request.getReason()));
            // 发送终审通过通知
            notifyPublisher.publishFinalCompleted(batchId, userId, now);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void validateTaskAction(BpmTaskActionContext context) {
        if (context.getBusinessKey() == null || !context.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)) return;
        // SIMPLE 设计器的发起人提交节点由引擎在启动后自动通过，它不是业务审批动作：既没有逐条
        // 结论要校验，也不推进批次阶段。不放行会让流程卡在发起人节点上，批次永远进不到编导审核。
        if (SIMPLE_SUBMISSION_TASK_KEY.equals(context.getTaskDefinitionKey())) return;
        ContentReviewBatchDO batch = batchMapper.selectByProcessInstanceId(context.getProcessInstanceId());
        if (batch == null || !matchesProcess(batch, context)) throw exception(CONTENT_REVIEW_TASK_INVALID);
        batch = lockBatch(batch.getId());
        Map<String, Object> frozen = parseMap(batch.getContextSnapshotJson());
        String directorTaskKey = text(frozen.get("directorTaskKey"));
        String finalTaskKey = text(frozen.get("finalTaskKey"));
        if (BATCH_DIRECTOR_REVIEW.equals(batch.getStatus()) && STAGE_DIRECTOR.equals(batch.getCurrentStage())
                && Objects.equals(directorTaskKey, context.getTaskDefinitionKey())) {
            if (!Objects.equals(batch.getDirectorUserId(), context.getUserId())) {
                throw exception(CONTENT_REVIEW_PERMISSION_DENIED);
            }
            List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batch.getId());
            requireDirectorDecisions(items);
            // 编导节点支持通过和驳回两种操作
            if (ACTION_APPROVE.equals(context.getAction())) {
                // 通过：必须全部通过才能进入终审
                boolean allApproved = items.stream()
                        .allMatch(item -> DECISION_APPROVED.equals(item.getDirectorDecision()));
                if (!allApproved) {
                    throw exception(CONTENT_REVIEW_TASK_INVALID);
                }
                if (batchMapper.markDirectorCompleted(batch, LocalDateTime.now()) != 1) {
                    throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
                }
            } else if (ACTION_REJECT.equals(context.getAction())) {
                // 驳回：有退回内容时允许驳回
                boolean hasReturned = items.stream()
                        .anyMatch(item -> DECISION_RETURNED.equals(item.getDirectorDecision()));
                if (!hasReturned) {
                    throw exception(CONTENT_REVIEW_TASK_INVALID);
                }
                // 批次状态更新由 handleProcessResult 处理
            } else {
                throw exception(CONTENT_REVIEW_TASK_INVALID);
            }
            return;
        }
        if (BATCH_FINAL_REVIEW.equals(batch.getStatus()) && STAGE_FINAL.equals(batch.getCurrentStage())
                && Objects.equals(finalTaskKey, context.getTaskDefinitionKey())) {
            if (!ACTION_APPROVE.equals(context.getAction())) throw exception(CONTENT_REVIEW_TASK_INVALID);
            List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batch.getId());
            requireFinalDecisions(items);
            for (ContentReviewBatchItemDO item : items) {
                if (Boolean.TRUE.equals(item.getCollectMaterial())) readPreparedCollection(batch, item);
            }
            return;
        }
        throw exception(CONTENT_REVIEW_TASK_INVALID);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(BpmProcessInstanceStatusEvent event) {
        if (event.getBusinessKey() == null || !event.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)
                || !BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus())) return;
        ContentReviewBatchDO located = batchMapper.selectByProcessInstanceId(event.getId());
        if (located == null) return;
        ContentReviewBatchDO batch = lockBatch(located.getId());
        if (!matchesProcess(batch, event) || Objects.equals(batch.getLastEventKey(), event.getEventKey())
                || BATCH_COMPLETED.equals(batch.getStatus()) || BATCH_REJECTED.equals(batch.getStatus())
                || BATCH_NEED_MODIFY.equals(batch.getStatus())) return;
        List<ContentReviewBatchItemDO> items = itemMapper.selectByBatchId(batch.getId());
        LocalDateTime now = LocalDateTime.now();

        // 处理驳回情况（编导有退回内容，或终审驳回）
        if (!BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            // 解冻版本，允许运营修改后重新提交
            for (ContentReviewBatchItemDO item : items) {
                if (contentVersionMapper.unfreeze(item.getContentVersionId()) != 1) {
                    throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
                }
            }
            // 对每条内容落地退回结论
            for (ContentReviewBatchItemDO item : items) {
                ContentDO content = contentMapper.selectByIdForUpdate(item.getContentId(), tenantId());
                ContentVersionDO version = contentVersionMapper.selectByIdForUpdate(item.getContentVersionId(), tenantId());
                if (content == null || version == null || !Objects.equals(version.getContentId(), content.getId())
                        || !Objects.equals(content.getCurrentVersionNo(), version.getVersionNo())
                        || !CONTENT_ACCEPTANCE.equals(content.getStatus())) {
                    throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
                }
                // 优先使用编导的退回意见，如果编导通过了但终审退回，则使用终审意见
                String comment = DECISION_RETURNED.equals(item.getDirectorDecision())
                        ? firstText(item.getDirectorComment(), "编导审核退回")
                        : firstText(item.getFinalComment(), "终审审核退回");
                Long reviewer = item.getDirectorReviewedByUserId();
                contentService.applyBatchReview(content, content.getVersion(), false, comment, reviewer);
                if (contentVersionMapper.finishReview(version.getId(), "rejected", comment, reviewer, now) != 1) {
                    throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
                }
                if (itemMapper.finalizeItem(item, RESULT_RETURNED, null, null) != 1) {
                    throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
                }
            }
            if (batchMapper.finalizeBatch(batch, BATCH_NEED_MODIFY, event.getEventKey(), now) != 1) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
            return;
        }

        // 处理终审通过情况
        if (!BATCH_FINAL_REVIEW.equals(batch.getStatus()) || !STAGE_FINAL.equals(batch.getCurrentStage())) {
            throw exception(CONTENT_REVIEW_TASK_INVALID);
        }
        requireFinalDecisions(items);
        Map<Long, MaterialService.AutoCollectionSnapshot> preparedCollections = new HashMap<>();
        for (ContentReviewBatchItemDO item : items) {
            if (Boolean.TRUE.equals(item.getCollectMaterial())) {
                preparedCollections.put(item.getId(), readPreparedCollection(batch, item));
            }
        }
        boolean allApproved = true;
        for (ContentReviewBatchItemDO item : items) {
            boolean approved = DECISION_APPROVED.equals(item.getDirectorDecision())
                    && DECISION_APPROVED.equals(item.getFinalDecision());
            allApproved &= approved;
            ContentDO content = contentMapper.selectByIdForUpdate(item.getContentId(), tenantId());
            ContentVersionDO version = contentVersionMapper.selectByIdForUpdate(item.getContentVersionId(), tenantId());
            if (content == null || version == null || !Objects.equals(version.getContentId(), content.getId())
                    || !Objects.equals(content.getCurrentVersionNo(), version.getVersionNo())
                    || !CONTENT_ACCEPTANCE.equals(content.getStatus()) || version.getFrozenAt() == null) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
            String comment = approved ? trimToNull(item.getFinalComment())
                    : firstText(item.getFinalComment(), item.getDirectorComment(), "审核退回");
            Long reviewer = item.getFinalReviewedByUserId() != null
                    ? item.getFinalReviewedByUserId() : item.getDirectorReviewedByUserId();
            contentService.applyBatchReview(content, content.getVersion(), approved, comment, reviewer);
            if (contentVersionMapper.finishReview(version.getId(), approved ? "approved" : "rejected",
                    comment, reviewer, now) != 1) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
            MaterialService.AutoCollectedMaterial collected = null;
            if (approved && Boolean.TRUE.equals(item.getCollectMaterial())) {
                collected = materialService.createEffectiveFromContentReview(
                        preparedCollections.get(item.getId()), content.getId(), version.getId(),
                        batch.getOperatorUserId());
            }
            if (itemMapper.finalizeItem(item, approved ? RESULT_READY_TO_PUBLISH : RESULT_RETURNED,
                    collected == null ? null : collected.materialId(),
                    collected == null ? null : collected.materialVersionId()) != 1) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
        }
        String finalStatus = allApproved ? BATCH_COMPLETED : BATCH_NEED_MODIFY;
        if (batchMapper.finalizeBatch(batch, finalStatus, event.getEventKey(), now) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
    }

    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "publish")
    @Transactional(rollbackFor = Exception.class)
    public void registerPublished(Long batchId, Long itemId, ContentReviewPublishReqVO request, Long userId) {
        ContentReviewBatchDO batch = lockBatch(batchId);
        if (!BATCH_COMPLETED.equals(batch.getStatus()) || !Objects.equals(batch.getOperatorUserId(), userId)
                || !validHttps(request.getPlatformUrl())) {
            throw exception(CONTENT_REVIEW_PUBLISH_INVALID);
        }
        ContentReviewBatchItemDO item = lockItem(batchId, itemId);
        String platformUrl = request.getPlatformUrl().trim();
        if (RESULT_PUBLISHED.equals(item.getResultStatus())) {
            if (Objects.equals(item.getPublishedPlatformUrl(), platformUrl)
                    && Objects.equals(item.getPublishedAt(), request.getPublishedAt())) {
                return;
            }
            throw exception(CONTENT_REVIEW_PUBLISH_INVALID);
        }
        if (!RESULT_READY_TO_PUBLISH.equals(item.getResultStatus())) {
            throw exception(CONTENT_REVIEW_PUBLISH_INVALID);
        }
        ContentDO content = contentMapper.selectByIdForUpdate(item.getContentId(), tenantId());
        ContentVersionDO contentVersion = contentVersionMapper.selectByIdForUpdate(
                item.getContentVersionId(), tenantId());
        if (content == null || contentVersion == null
                || !Objects.equals(contentVersion.getContentId(), content.getId())
                || !Objects.equals(content.getCurrentVersionNo(), contentVersion.getVersionNo())
                || !"approved".equals(contentVersion.getReviewDecision())
                || !CONTENT_READY_TO_PUBLISH.equals(content.getStatus())
                || !Objects.equals(content.getVersion(), request.getExpectedContentVersion())) {
            throw exception(CONTENT_REVIEW_PUBLISH_INVALID);
        }
        contentService.registerPublished(content, request.getExpectedContentVersion(),
                platformUrl, request.getPublishedAt(), userId);
        if (itemMapper.markPublished(item, platformUrl, request.getPublishedAt(), userId) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        List<ContentReviewBatchItemDO> remaining = itemMapper.selectByBatchId(batchId);
        if (!remaining.isEmpty() && remaining.stream().allMatch(row -> RESULT_PUBLISHED.equals(row.getResultStatus()))) {
            ContentReviewBatchDO refreshed = lockBatch(batchId);
            if (batchMapper.markPublished(refreshed, LocalDateTime.now()) != 1) {
                throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
            }
        }
    }

    /** Starts a new approval round from an editable rejected batch while retaining the old round. */
    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public Long resubmit(Long batchId, ContentReviewStudentDraftCreateReqVO request, Long userId) {
        ContentReviewBatchDO previous = lockBatch(batchId);
        if ((!BATCH_NEED_MODIFY.equals(previous.getStatus()) && !BATCH_REJECTED.equals(previous.getStatus()))
                || !Objects.equals(previous.getOperatorUserId(), userId)) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        if (!Objects.equals(previous.getStudentPersonId(), request.getStudentPersonId())) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        Long nextBatchId = createFromStudent(request, userId);
        ContentReviewBatchDO nextBatch = lockBatch(nextBatchId);
        nextBatch.setRevisionOfBatchId(previous.getId());
        batchMapper.updateById(nextBatch);
        return nextBatchId;
    }

    /** Saves a revised student draft as a new draft round, keeping the previous draft auditable. */
    @ZsjosPermission(bizType = "content-review-batch", bizId = "#batchId", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public Long saveStudentDraft(Long batchId, ContentReviewStudentDraftCreateReqVO request, Long userId) {
        ContentReviewBatchDO previous = lockBatch(batchId);
        if (!BATCH_DRAFT.equals(previous.getStatus()) || !STAGE_DRAFT.equals(previous.getCurrentStage())
                || !Objects.equals(previous.getOperatorUserId(), userId)
                || !Objects.equals(previous.getStudentPersonId(), request.getStudentPersonId())) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        if (batchMapper.cancelDraft(previous, previous.getVersion(), LocalDateTime.now()) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        Long nextBatchId = createFromStudent(request, userId);
        ContentReviewBatchDO nextBatch = lockBatch(nextBatchId);
        nextBatch.setRevisionOfBatchId(previous.getId());
        batchMapper.updateById(nextBatch);
        return nextBatchId;
    }

    private BatchSubjects validateSubjects(List<ContentVersionDO> versions, Map<Long, ContentDO> contentMap,
                                           Long userId, Long expectedAccountId) {
        return validateSubjects(versions, contentMap, userId, expectedAccountId, true);
    }

    private BatchSubjects validateSubjects(List<ContentVersionDO> versions, Map<Long, ContentDO> contentMap,
                                           Long userId, Long expectedAccountId, boolean validatePackage) {
        if (versions.isEmpty() || versions.size() > 20 || contentMap.size() != versions.size()) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        Set<Long> accountIds = new LinkedHashSet<>();
        for (ContentVersionDO version : versions) {
            ContentDO content = contentMap.get(version.getContentId());
            if (content == null || !contentPermissionProvider.hasPermission(content.getId(), "read", userId)
                    || !CONTENT_ACCEPTANCE.equals(content.getStatus())
                    || !Objects.equals(content.getCurrentVersionNo(), version.getVersionNo())
                    || version.getFrozenAt() != null || version.getReviewDecision() != null) {
                throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            }
            if (validatePackage) ContentPackageValidator.validate(content, version);
            accountIds.add(content.getAccountId());
        }
        if (accountIds.size() != 1 || expectedAccountId != null && !accountIds.contains(expectedAccountId)) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        MediaAccountDO account = accountMapper.selectById(accountIds.iterator().next());
        if (account == null || !Objects.equals(account.getOwnerOperatorUserId(), userId)) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
        return new BatchSubjects(account);
    }

    private void validateSelectedAccountsAtSubmit(ContentReviewBatchDO batch, Long directorUserId, Long userId) {
        List<Long> selected = parseLongList(batch.getAccountIdsJson());
        if (selected.isEmpty()) selected = List.of(batch.getAccountId());
        for (Long accountId : selected) {
            MediaAccountDO account = accountMapper.selectByIdForUpdate(accountId, tenantId());
            if (account == null || !Objects.equals(account.getOwnerOperatorUserId(), userId)
                    || batch.getStudentPersonId() != null && !Objects.equals(account.getStudentPersonId(), batch.getStudentPersonId())
                    || !Objects.equals(account.getDirectorUserId(), directorUserId)) {
                throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            }
        }
    }

    private List<ContentVersionDO> lockVersions(List<ContentReviewBatchItemDO> items) {
        List<ContentVersionDO> versions = new ArrayList<>();
        for (ContentReviewBatchItemDO item : items.stream()
                .sorted(Comparator.comparing(ContentReviewBatchItemDO::getContentVersionId)).toList()) {
            ContentVersionDO version = contentVersionMapper.selectByIdForUpdate(item.getContentVersionId(), tenantId());
            if (version == null) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            versions.add(version);
        }
        return versions;
    }

    private Map<Long, ContentDO> lockContents(List<ContentReviewBatchItemDO> items) {
        Map<Long, ContentDO> contents = new LinkedHashMap<>();
        for (Long contentId : items.stream().map(ContentReviewBatchItemDO::getContentId)
                .distinct().sorted().toList()) {
            ContentDO content = contentMapper.selectByIdForUpdate(contentId, tenantId());
            if (content == null) throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
            contents.put(contentId, content);
        }
        return contents;
    }

    private RelationContext requireDirector(Long operatorUserId) {
        List<LeadAssignmentRelationDO> relations = relationMapper.selectEnabledDirectorsForOperator(
                RELATION_DIRECTOR_OPERATOR, operatorUserId, tenantId());
        if (relations.size() != 1 || relations.getFirst().getSourceUserId() == null) {
            throw exception(CONTENT_REVIEW_DIRECTOR_INVALID);
        }
        AdminUserRespDTO director = adminUserApi.getUser(relations.getFirst().getSourceUserId());
        AdminUserRespDTO operator = adminUserApi.getUser(operatorUserId);
        if (director == null || operator == null
                || !CommonStatusEnum.ENABLE.getStatus().equals(director.getStatus())
                || !CommonStatusEnum.ENABLE.getStatus().equals(operator.getStatus())) {
            throw exception(CONTENT_REVIEW_DIRECTOR_INVALID);
        }
        return new RelationContext(relations.getFirst(), director, operator);
    }

    private void requireStageAndTask(ContentReviewBatchDO batch, String status, String stage,
                                     String taskKeyField, String taskId, Long userId) {
        if (!status.equals(batch.getStatus()) || !stage.equals(batch.getCurrentStage())) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        Map<String, Object> frozen = parseMap(batch.getContextSnapshotJson());
        String expectedTaskKey = text(frozen.get(taskKeyField));
        BpmTaskRespDTO task;
        try {
            task = processTaskApi.getTodoTask(userId, taskId);
        } catch (RuntimeException error) {
            throw exception(CONTENT_REVIEW_TASK_INVALID);
        }
        if (task == null || !Objects.equals(task.getProcessInstanceId(), batch.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), batch.getBusinessKey())
                || !Objects.equals(task.getProcessDefinitionKey(), batch.getProcessDefinitionKey())
                || !Objects.equals(task.getTaskDefinitionKey(), expectedTaskKey)
                || Boolean.TRUE.equals(task.getSignTask())) {
            throw exception(CONTENT_REVIEW_TASK_INVALID);
        }
    }

    private void requireDirectorDecisions(List<ContentReviewBatchItemDO> items) {
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getDirectorDecision() == null
                || !DECISIONS.contains(item.getDirectorDecision()))) {
            throw exception(CONTENT_REVIEW_DECISION_INCOMPLETE);
        }
    }

    private void requireFinalDecisions(List<ContentReviewBatchItemDO> items) {
        requireDirectorDecisions(items);
        if (items.stream().anyMatch(item -> DECISION_APPROVED.equals(item.getDirectorDecision())
                && (item.getFinalDecision() == null || !DECISIONS.contains(item.getFinalDecision())))) {
            throw exception(CONTENT_REVIEW_DECISION_INCOMPLETE);
        }
    }

    private MaterialService.AutoCollectionSnapshot prepareCollection(ContentReviewBatchDO batch,
                                                                     ContentReviewBatchItemDO item) {
        FrozenContext frozen = frozenContext(batch);
        return reviewMaterialService.buildAndValidate(frozen.materialTypeCode(), frozen.schemaVersionId(),
                frozen.schemaHash(), frozen.mapping(), frozen.defaults(), parseMap(item.getContentSnapshotJson()),
                frozen.accountSnapshot(), contentVersionFileMapper.selectByVersionId(item.getContentVersionId())
                        .stream().map(file -> file.getInfraFileId()).collect(Collectors.toUnmodifiableSet()),
                batch.getOperatorUserId());
    }

    private MaterialService.AutoCollectionSnapshot readPreparedCollection(ContentReviewBatchDO batch,
                                                                          ContentReviewBatchItemDO item) {
        FrozenContext frozen = frozenContext(batch);
        return reviewMaterialService.readAndValidate(item.getCollectionSnapshotJson(), frozen.materialTypeCode(),
                frozen.schemaVersionId(), frozen.schemaHash());
    }

    private FrozenContext frozenContext(ContentReviewBatchDO batch) {
        Map<String, Object> context = parseMap(batch.getContextSnapshotJson());
        return new FrozenContext(text(context.get("productionMaterialTypeCode")),
                longValue(context.get("productionMaterialSchemaVersionId")),
                text(context.get("productionMaterialSchemaHash")),
                stringMap(context.get("materialFieldMapping")), objectMap(context.get("materialDefaultValues")),
                objectMap(context.get("account")));
    }

    private boolean matchesProcess(ContentReviewBatchDO batch, BpmTaskActionContext context) {
        return Objects.equals(batch.getProcessInstanceId(), context.getProcessInstanceId())
                && Objects.equals(batch.getBusinessKey(), context.getBusinessKey())
                && Objects.equals(batch.getProcessDefinitionId(), context.getProcessDefinitionId())
                && Objects.equals(batch.getProcessDefinitionKey(), context.getProcessDefinitionKey())
                && Objects.equals(batch.getProcessDefinitionVersion(), context.getProcessDefinitionVersion())
                && Objects.equals(String.valueOf(tenantId()), context.getTenantId());
    }

    private boolean matchesProcess(ContentReviewBatchDO batch, BpmProcessInstanceStatusEvent event) {
        return Objects.equals(batch.getProcessInstanceId(), event.getId())
                && Objects.equals(batch.getBusinessKey(), event.getBusinessKey())
                && Objects.equals(batch.getProcessDefinitionId(), event.getProcessDefinitionId())
                && Objects.equals(batch.getProcessDefinitionKey(), event.getProcessDefinitionKey())
                && Objects.equals(batch.getProcessDefinitionVersion(), event.getProcessDefinitionVersion());
    }

    private Map<String, BpmTaskRespDTO> loadCurrentTasks(List<ContentReviewBatchDO> batches, Long userId) {
        Map<TaskGroup, List<ContentReviewBatchDO>> groups = batches.stream()
                .filter(batch -> batch.getProcessInstanceId() != null
                        && (STAGE_DIRECTOR.equals(batch.getCurrentStage()) || STAGE_FINAL.equals(batch.getCurrentStage())))
                .collect(Collectors.groupingBy(batch -> new TaskGroup(batch.getProcessDefinitionKey(),
                        expectedTaskKey(batch)), LinkedHashMap::new, Collectors.toList()));
        Map<String, BpmTaskRespDTO> result = new HashMap<>();
        for (Map.Entry<TaskGroup, List<ContentReviewBatchDO>> entry : groups.entrySet()) {
            if (blank(entry.getKey().definitionKey()) || blank(entry.getKey().taskKey())) continue;
            BpmTaskPageReqDTO request = new BpmTaskPageReqDTO();
            request.setPageNo(1);
            request.setPageSize(Math.min(200, Math.max(1, entry.getValue().size())));
            request.setProcessDefinitionKey(entry.getKey().definitionKey());
            request.setTaskDefinitionKey(entry.getKey().taskKey());
            request.setProcessInstanceIds(entry.getValue().stream()
                    .map(ContentReviewBatchDO::getProcessInstanceId).toList());
            processTaskApi.getTodoTaskPage(userId, request).getList().forEach(task ->
                    result.put(task.getProcessInstanceId(), task));
        }
        return result;
    }

    private List<ContentReviewBatchRespVO> toResponses(List<ContentReviewBatchDO> batches,
                                                        Map<Long, List<ContentReviewBatchItemDO>> items,
                                                        Map<String, BpmTaskRespDTO> tasks, Long userId) {
        Map<Long, ContentDO> contents = loadContentRecords(items.values().stream()
                .flatMap(Collection::stream).toList());
        return batches.stream().map(batch -> toResponse(batch, items.getOrDefault(batch.getId(), List.of()),
                contents, tasks.get(batch.getProcessInstanceId()), userId, false)).toList();
    }

    private ContentReviewBatchRespVO toResponse(ContentReviewBatchDO batch,
                                                 List<ContentReviewBatchItemDO> items,
                                                 Map<Long, ContentDO> contents,
                                                 BpmTaskRespDTO task, Long userId, boolean includeFiles) {
        ContentReviewBatchRespVO response = BeanUtils.toBean(batch, ContentReviewBatchRespVO.class);
        response.setRelationSnapshot(parseMap(batch.getRelationSnapshotJson()));
        Map<String, Object> context = parseMap(batch.getContextSnapshotJson());
        response.setAccountIds(parseLongList(batch.getAccountIdsJson()));
        // 账号快照里存的是用户编号，姓名按编号解析后回填，避免页面退化成展示内部 ID。
        // 早期冻结的快照没有姓名字段，同样在这里补齐；快照本身不改写。
        List<Map<String, Object>> accountSnapshots = objectMapList(context.get("accountSnapshots"));
        Set<Long> userIds = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(batch.getOperatorUserId(), batch.getDirectorUserId()),
                        accountSnapshots.stream().flatMap(snapshot -> java.util.stream.Stream.of(
                                longValue(snapshot.get("ownerOperatorUserId")),
                                longValue(snapshot.get("directorUserId")))))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> users = userIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(userIds);
        if (!accountSnapshots.isEmpty()) {
            context.put("accountSnapshots", accountSnapshots.stream().map(snapshot -> {
                Map<String, Object> resolved = new LinkedHashMap<>(snapshot);
                // 快照里可能存在显式 null，putIfAbsent 不会覆盖，这里按空值判断补齐。
                if (blank(text(resolved.get("operatorName")))) {
                    resolved.put("operatorName", nickname(users, longValue(snapshot.get("ownerOperatorUserId"))));
                }
                if (blank(text(resolved.get("directorName")))) {
                    resolved.put("directorName", nickname(users, longValue(snapshot.get("directorUserId"))));
                }
                return resolved;
            }).toList());
        }
        response.setContextSnapshot(context);
        response.setOperatorName(nickname(users, batch.getOperatorUserId()));
        response.setDirectorName(nickname(users, batch.getDirectorUserId()));
        if (task != null) {
            response.setCurrentTaskId(task.getId());
            response.setCurrentTaskKey(task.getTaskDefinitionKey());
        }
        response.setAvailableActions(availableActions(batch, items, task, userId));
        response.setItems(items.stream()
                .map(item -> toItemResponse(item, contents.get(item.getContentId()), includeFiles)).toList());
        return response;
    }

    private ContentReviewBatchItemRespVO toItemResponse(ContentReviewBatchItemDO item, ContentDO content,
                                                        boolean includeFiles) {
        ContentReviewBatchItemRespVO response = BeanUtils.toBean(item, ContentReviewBatchItemRespVO.class);
        response.setContentRecordVersion(content == null ? null : content.getVersion());
        response.setContentSnapshot(parseMap(item.getContentSnapshotJson()));
        response.setFiles(includeFiles ? contentVersionFileMapper.selectByVersionId(item.getContentVersionId())
                .stream().map(file -> {
                    ContentVersionFileRespVO result = BeanUtils.toBean(file, ContentVersionFileRespVO.class);
                    try {
                        result.setPreviewUrl(fileApi.presignGetUrl(file.getInfraFileId(), FILE_PREVIEW_SECONDS));
                    } catch (RuntimeException ignored) {
                        result.setPreviewUrl(file.getFileUrlSnapshot());
                    }
                    return result;
                }).toList() : List.of());
        return response;
    }

    private Map<Long, ContentDO> loadContentRecords(Collection<ContentReviewBatchItemDO> items) {
        Set<Long> ids = items.stream().map(ContentReviewBatchItemDO::getContentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return contentMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ContentDO::getId, Function.identity()));
    }

    private List<String> availableActions(ContentReviewBatchDO batch, List<ContentReviewBatchItemDO> items,
                                          BpmTaskRespDTO task, Long userId) {
        List<String> actions = new ArrayList<>();
        if (BATCH_DRAFT.equals(batch.getStatus()) && Objects.equals(batch.getOperatorUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:content-review:submit")) {
            actions.add("SUBMIT");
            actions.add("CANCEL");
        }
        if ((BATCH_NEED_MODIFY.equals(batch.getStatus()) || BATCH_REJECTED.equals(batch.getStatus()))
                && batch.getStudentPersonId() != null && Objects.equals(batch.getOperatorUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:content-review:submit")) {
            actions.add("RESUBMIT");
        }
        if (task != null && STAGE_DIRECTOR.equals(batch.getCurrentStage())
                && Objects.equals(batch.getDirectorUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:content-review:director-review")) {
            actions.add("DIRECTOR_DECIDE");
            // 尚未暂存结论时 decision 为 null，DECISIONS 是 Set.of，contains(null) 会抛 NPE。
            if (items.stream().allMatch(item -> item.getDirectorDecision() != null
                    && DECISIONS.contains(item.getDirectorDecision()))) {
                actions.add("DIRECTOR_COMPLETE");
            }
        }
        if (task != null && STAGE_FINAL.equals(batch.getCurrentStage())
                && permissionApi.hasAnyPermissions(userId, "zsjos:content-review:final-review")) {
            actions.add("FINAL_DECIDE");
            if (items.stream().filter(item -> DECISION_APPROVED.equals(item.getDirectorDecision()))
                    .allMatch(item -> item.getFinalDecision() != null
                            && DECISIONS.contains(item.getFinalDecision()))) {
                actions.add("FINAL_COMPLETE");
            }
        }
        if (BATCH_COMPLETED.equals(batch.getStatus()) && Objects.equals(batch.getOperatorUserId(), userId)
                && items.stream().anyMatch(item -> RESULT_READY_TO_PUBLISH.equals(item.getResultStatus()))
                && permissionApi.hasAnyPermissions(userId, "zsjos:content-review:publish-register")) {
            actions.add("REGISTER_PUBLISH");
        }
        return List.copyOf(actions);
    }

    private String expectedTaskKey(ContentReviewBatchDO batch) {
        Map<String, Object> context = parseMap(batch.getContextSnapshotJson());
        return STAGE_DIRECTOR.equals(batch.getCurrentStage()) ? text(context.get("directorTaskKey"))
                : STAGE_FINAL.equals(batch.getCurrentStage()) ? text(context.get("finalTaskKey")) : null;
    }

    private Map<String, Object> relationSnapshot(RelationContext relation, Long operatorUserId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scene", RELATION_DIRECTOR_OPERATOR);
        snapshot.put("relationId", relation.relation().getId());
        snapshot.put("directorUserId", relation.director().getId());
        snapshot.put("directorName", relation.director().getNickname());
        snapshot.put("operatorUserId", operatorUserId);
        snapshot.put("operatorName", relation.operator().getNickname());
        return snapshot;
    }

    private Map<String, Object> contextSnapshot(ContentReviewConfigDO config, MediaAccountDO account,
                                                cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO schema) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configVersion", config.getVersion());
        snapshot.put("directorTaskKey", ContentReviewConfigService.DIRECTOR_TASK_KEY);
        snapshot.put("finalTaskKey", ContentReviewConfigService.FINAL_TASK_KEY);
        snapshot.put("productionMaterialTypeCode", ContentReviewConfigService.PRODUCTION_MATERIAL_TYPE_CODE);
        snapshot.put("productionMaterialSchemaVersionId", schema.getId());
        snapshot.put("productionMaterialSchemaHash", schema.getSchemaHash());
        snapshot.put("materialFieldMapping", configService.mapping(config));
        snapshot.put("materialDefaultValues", configService.defaults(config));
        Map<String, Object> accountSnapshot = new LinkedHashMap<>();
        accountSnapshot.put("id", account.getId());
        accountSnapshot.put("accountNo", account.getAccountNo());
        accountSnapshot.put("nickname", account.getNickname());
        accountSnapshot.put("accountTypePrimaryValue", account.getAccountTypePrimaryValue());
        accountSnapshot.put("accountTypePrimaryLabel", account.getAccountTypePrimaryLabelSnapshot());
        accountSnapshot.put("accountTypeSecondaryValue", account.getAccountTypeSecondaryValue());
        accountSnapshot.put("accountTypeSecondaryLabel", account.getAccountTypeSecondaryLabelSnapshot());
        accountSnapshot.put("trackPrimaryValue", account.getTrackPrimaryValue());
        accountSnapshot.put("trackPrimaryLabel", account.getTrackPrimaryLabelSnapshot());
        accountSnapshot.put("trackSecondaryValue", account.getTrackSecondaryValue());
        accountSnapshot.put("trackSecondaryLabel", account.getTrackSecondaryLabelSnapshot());
        accountSnapshot.put("sStage", account.getSStage());
        accountSnapshot.put("sStageLabel", account.getSStageLabelSnapshot());
        snapshot.put("account", accountSnapshot);
        return snapshot;
    }

    private Map<String, Object> contentSnapshot(ContentDO content, ContentVersionDO version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contentId", content.getId());
        snapshot.put("contentNo", content.getContentNo());
        snapshot.put("accountId", content.getAccountId());
        snapshot.put("ownerOperatorUserId", content.getOwnerOperatorUserId());
        snapshot.put("contentVersionId", version.getId());
        snapshot.put("contentVersionNo", version.getVersionNo());
        snapshot.put("stage", version.getStage());
        snapshot.put("title", content.getTitle());
        snapshot.put("topic", content.getTopic());
        snapshot.put("titleSnapshot", version.getTitleSnapshot());
        snapshot.put("topicSnapshot", version.getTopicSnapshot());
        snapshot.put("purposeValue", version.getPurposeValue());
        snapshot.put("purposeLabelSnapshot", version.getPurposeLabelSnapshot());
        snapshot.put("formatValue", version.getFormatValue());
        snapshot.put("formatLabelSnapshot", version.getFormatLabelSnapshot());
        snapshot.put("coverSnapshot", parseJsonValue(version.getCoverSnapshotJson()));
        snapshot.put("scriptText", version.getScriptText());
        snapshot.put("detailUrl", version.getDetailUrl());
        snapshot.put("commentHook", version.getCommentHook());
        snapshot.put("referenceContentVersionId", version.getReferenceContentVersionId());
        snapshot.put("referenceWorkUrl", version.getReferenceWorkUrl());
        snapshot.put("deliverableUrl", version.getDeliverableUrl());
        snapshot.put("deliverableSnapshot", parseJsonValue(version.getDeliverableSnapshotJson()));
        snapshot.put("leadResourceUrl", version.getLeadResourceUrl());
        snapshot.put("plannedPublishAt", version.getPlannedPublishAt() == null
                ? null : version.getPlannedPublishAt().toString());
        snapshot.put("materialRefs", parseJsonValue(version.getMaterialRefsJson()));
        return snapshot;
    }

    private boolean canSeeAll(Long userId) {
        return permissionApi.hasTenantReadAllAccess(userId)
                || permissionApi.hasAnyPermissions(userId, "zsjos:content-review:query-all");
    }

    private String normalizeDecision(String decision, String comment) {
        String normalized = decision == null ? null : decision.trim().toUpperCase();
        // DECISIONS 是 Set.of，contains(null) 抛 NPE 而非返回 false；缺结论应返回业务错误。
        if (normalized == null || !DECISIONS.contains(normalized)) {
            throw exception(CONTENT_REVIEW_BATCH_STATE_INVALID);
        }
        if (DECISION_RETURNED.equals(normalized) && blank(comment)) {
            throw exception(CONTENT_REVIEW_DECISION_INCOMPLETE);
        }
        return normalized;
    }

    private ContentReviewBatchDO requireBatch(Long batchId) {
        ContentReviewBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) throw exception(CONTENT_REVIEW_BATCH_NOT_EXISTS);
        return batch;
    }

    private ContentReviewBatchDO lockBatch(Long batchId) {
        ContentReviewBatchDO batch = batchMapper.selectByIdForUpdate(batchId, tenantId());
        if (batch == null) throw exception(CONTENT_REVIEW_BATCH_NOT_EXISTS);
        return batch;
    }

    private ContentReviewBatchItemDO lockItem(Long batchId, Long itemId) {
        ContentReviewBatchItemDO item = itemMapper.selectByIdForUpdate(itemId, tenantId());
        if (item == null || !Objects.equals(item.getBatchId(), batchId)) {
            throw exception(CONTENT_REVIEW_BATCH_NOT_EXISTS);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Map<String, Object> value = JsonUtils.parseObject(json, Map.class);
        return value == null ? Map.of() : value;
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var node = JsonUtils.parseTree(json);
            if (node == null || !node.isArray()) return List.of();
            List<Long> result = new ArrayList<>();
            node.forEach(item -> { if (item.isNumber()) result.add(item.longValue()); });
            return List.copyOf(result);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> objectMapList(Object value) {
        if (!(value instanceof java.util.List<?> list)) return List.of();
        return list.stream().filter(item -> item instanceof Map<?, ?>).map(this::objectMap).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, String> stringMap(Object value) {
        return objectMap(value).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                item -> String.valueOf(item.getValue()), (left, right) -> right, LinkedHashMap::new));
    }

    private Object parseJsonValue(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JsonUtils.parseTree(json);
        } catch (RuntimeException error) {
            throw exception(CONTENT_REVIEW_BATCH_ITEMS_INVALID);
        }
    }

    private boolean validHttps(String value) {
        if (blank(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        return null;
    }

    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private String nextBatchNo() {
        return "CRB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private record BatchSubjects(MediaAccountDO account) {}
    private record RelationContext(LeadAssignmentRelationDO relation, AdminUserRespDTO director,
                                   AdminUserRespDTO operator) {}
    private record FrozenContext(String materialTypeCode, Long schemaVersionId, String schemaHash,
                                 Map<String, String> mapping,
                                 Map<String, Object> defaults, Map<String, Object> accountSnapshot) {}
    private record TaskGroup(String definitionKey, String taskKey) {}
}




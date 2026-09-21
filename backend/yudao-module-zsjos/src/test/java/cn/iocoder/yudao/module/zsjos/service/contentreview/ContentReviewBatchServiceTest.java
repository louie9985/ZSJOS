package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import org.springframework.test.util.ReflectionTestUtils;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewPublishReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.content.ContentObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_APPROVE;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_ACCEPTANCE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_READY_TO_PUBLISH;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReviewBatchServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OPERATOR_ID = 7L;

    @InjectMocks private ContentReviewBatchService service;
    @Mock private ContentReviewNotifyPublisher notifyPublisher;
    @Mock private ContentReviewBatchMapper batchMapper;
    @Mock private ContentReviewBatchItemMapper itemMapper;
    @Mock private ContentReviewRelationMapper relationMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ContentVersionMapper contentVersionMapper;
    @Mock private ContentVersionFileMapper contentVersionFileMapper;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private ContentReviewConfigService configService;
    @Mock private ContentReviewMaterialService reviewMaterialService;
    @Mock private ContentReviewAccessService accessService;
    @Mock private ContentService contentService;
    @Mock private ContentObjectPermissionProvider contentPermissionProvider;
    @Mock private MaterialService materialService;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private FileApi fileApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createRejectsTwentyOneItemsBeforeDatabaseAccess() {
        ContentReviewBatchCreateReqVO request = new ContentReviewBatchCreateReqVO();
        request.setContentVersionIds(LongStream.rangeClosed(1, 21).boxed().toList());

        assertServiceCode(CONTENT_REVIEW_BATCH_ITEMS_INVALID,
                () -> service.create(request, OPERATOR_ID));

        verifyNoInteractions(contentVersionMapper, contentMapper, itemMapper, batchMapper);
    }

    @Test
    void createAcceptsTwentyCompleteCurrentVersionsForOneAccount() {
        List<Long> versionIds = LongStream.rangeClosed(1, 20).boxed().toList();
        List<ContentDO> contents = new ArrayList<>();
        for (Long versionId : versionIds) {
            long contentId = 1000L + versionId;
            ContentVersionDO version = completeVersion(versionId, contentId);
            ContentDO content = reviewableContent(contentId, 90L, 1, 0);
            contents.add(content);
            when(contentVersionMapper.selectByIdForUpdate(versionId, TENANT_ID)).thenReturn(version);
        }
        // 历史内容保留原运营快照；组批资格以账号当前运营为准。
        contents.getFirst().setOwnerOperatorUserId(999L);
        when(contentMapper.selectByIds(any())).thenReturn(contents);
        when(contentPermissionProvider.hasPermission(anyLong(), eq("read"), eq(OPERATOR_ID))).thenReturn(true);
        when(accountMapper.selectById(90L)).thenReturn(new MediaAccountDO().setId(90L)
                .setOwnerOperatorUserId(OPERATOR_ID));
        doAnswer(invocation -> {
            ((ContentReviewBatchDO) invocation.getArgument(0)).setId(50L);
            return 1;
        }).when(batchMapper).insert(any(ContentReviewBatchDO.class));

        ContentReviewBatchCreateReqVO request = new ContentReviewBatchCreateReqVO();
        request.setContentVersionIds(versionIds);
        Long batchId = service.create(request, OPERATOR_ID);

        assertEquals(50L, batchId);
        verify(itemMapper, times(20)).insert(any(ContentReviewBatchItemDO.class));
    }

    @Test
    void replacingDraftItemsRetainsBatchNumberAndExistingItemIdentity() {
        ContentReviewBatchDO draft = reviewBatch(BATCH_DRAFT, STAGE_DRAFT);
        ContentDO content = reviewableContent(1001L, 90L, 1, 0);
        ContentVersionDO version = completeVersion(11L, 1001L);
        ContentReviewBatchItemDO retained = reviewItem(1L, null, null, false)
                .setContentId(1001L).setContentVersionId(10L).setPreviousItemId(99L);
        ContentReviewBatchItemDO removed = reviewItem(2L, null, null, false).setContentId(1002L);
        when(contentVersionMapper.selectByIdForUpdate(11L, TENANT_ID)).thenReturn(version);
        when(contentMapper.selectByIds(any())).thenReturn(List.of(content));
        when(contentPermissionProvider.hasPermission(1001L, "read", OPERATOR_ID)).thenReturn(true);
        when(accountMapper.selectById(90L)).thenReturn(new MediaAccountDO().setId(90L).setOwnerOperatorUserId(OPERATOR_ID));
        when(itemMapper.selectByBatchId(50L)).thenReturn(List.of(retained, removed));
        ContentReviewBatchCreateReqVO request = new ContentReviewBatchCreateReqVO();
        request.setContentVersionIds(List.of(11L));
        Long savedId = ReflectionTestUtils.invokeMethod(service, "create", request, OPERATOR_ID, draft);
        assertEquals(50L, savedId);
        assertEquals("CRB-1", draft.getBatchNo());
        assertEquals(1, draft.getVersion());
        assertEquals(11L, retained.getContentVersionId());
        assertEquals(99L, retained.getPreviousItemId());
        verify(batchMapper, never()).insert(any(ContentReviewBatchDO.class));
        verify(batchMapper).updateById(draft);
        verify(itemMapper).updateById(retained);
        verify(itemMapper).deleteById(removed.getId());
    }

    @Test
    void savingStaleDraftFailsBeforeEditingContents() {
        ContentReviewBatchDO draft = reviewBatch(BATCH_DRAFT, STAGE_DRAFT).setStudentPersonId(77L).setVersion(3);
        when(batchMapper.selectByIdForUpdate(50L, TENANT_ID)).thenReturn(draft);
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentDraftCreateReqVO();
        request.setStudentPersonId(77L); request.setExpectedVersion(2);
        assertServiceCode(CONTENT_REVIEW_VERSION_CONFLICT, () -> service.saveStudentDraft(50L, request, OPERATOR_ID));
        verifyNoInteractions(itemMapper, contentMapper, accountMapper);
    }

    @Test
    void historicalRejectedRoundIsDisplayedAsResubmittedWithoutChangingStoredResult() {
        ContentReviewBatchDO old = reviewBatch(BATCH_NEED_MODIFY, STAGE_DONE);
        when(batchMapper.selectByRevisionOfBatchId(50L)).thenReturn(List.of(
                new ContentReviewBatchDO().setId(51L).setStatus(BATCH_DIRECTOR_REVIEW)));
        ContentReviewBatchRespVO response = ReflectionTestUtils.invokeMethod(service, "toResponse",
                old, List.of(), Map.of(), null, OPERATOR_ID, false);
        assertEquals("RESUBMITTED", response.getStatus());
        assertEquals(BATCH_NEED_MODIFY, old.getStatus());
        assertTrue(response.getAvailableActions().isEmpty());
    }

    @Test
    void cancelDraftReleasesBatchWithoutTouchingContentVersions() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DRAFT, STAGE_DRAFT);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        when(batchMapper.cancelDraft(eq(batch), eq(0), any())).thenReturn(1);

        service.cancelDraft(batch.getId(), 0, OPERATOR_ID);

        verify(batchMapper).cancelDraft(eq(batch), eq(0), any());
        verifyNoInteractions(contentVersionMapper, contentService);
    }

    @Test
    void directorCompletionRejectsMissingItemDecision() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, null, null, false)));

        assertServiceCode(CONTENT_REVIEW_DECISION_INCOMPLETE,
                () -> service.validateTaskAction(taskContext("director", OPERATOR_ID)));

        verify(batchMapper, never()).markDirectorCompleted(any(), any());
    }

    /**
     * 编导有退回内容时，批次必须走驳回（needs-modify）而不是推进终审——否则运营看不到退回意见，
     * 得等终审走完才知道。所以「通过」动作只接受全票通过，混合结论一律拒绝。
     */
    @Test
    void directorApprovalRejectsMixedApprovedAndReturnedItems() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, DECISION_RETURNED, null, false)));

        assertServiceCode(CONTENT_REVIEW_TASK_INVALID,
                () -> service.validateTaskAction(taskContext("director", OPERATOR_ID)));

        verify(batchMapper, never()).markDirectorCompleted(any(), any());
    }

    @Test
    void directorApprovalAcceptsBatchWhereEveryItemPassed() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, DECISION_APPROVED, null, false)));
        when(batchMapper.markDirectorCompleted(eq(batch), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.validateTaskAction(taskContext("director", OPERATOR_ID)));

        verify(batchMapper).markDirectorCompleted(eq(batch), any());
    }

    @Test
    void finalApprovalRejectsLegacyBatchWhereDirectorReturnedEveryItem() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_RETURNED, null, false),
                reviewItem(2L, DECISION_RETURNED, null, false)));

        assertServiceCode(CONTENT_REVIEW_TASK_INVALID, () -> service.validateTaskAction(taskContext("final", 9L)));

        verifyNoInteractions(reviewMaterialService);
    }

    /**
     * 早期冻结的账号快照只存了用户编号，没有姓名。响应组装时必须按编号解析补齐，
     * 否则审核页的责任运营和责任编导会退化成展示内部 ID。
     */
    @Test
    void responseBackfillsAccountSnapshotNamesForLegacySnapshots() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        batch.setDirectorUserId(248L);
        batch.setContextSnapshotJson("{\"accountSnapshots\":[{\"id\":3,"
                + "\"ownerOperatorUserId\":230,\"directorUserId\":248}]}");
        when(adminUserApi.getUserMap(Set.of(OPERATOR_ID, 230L, 248L))).thenReturn(Map.of(
                OPERATOR_ID, new AdminUserRespDTO().setId(OPERATOR_ID).setNickname("提交运营"),
                230L, new AdminUserRespDTO().setId(230L).setNickname("新媒体一部运营1"),
                248L, new AdminUserRespDTO().setId(248L).setNickname("编导1")));

        ContentReviewBatchRespVO response = ReflectionTestUtils.invokeMethod(service, "toResponse",
                batch, List.of(), Map.of(), null, OPERATOR_ID, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshots =
                (List<Map<String, Object>>) response.getContextSnapshot().get("accountSnapshots");
        assertEquals("新媒体一部运营1", snapshots.getFirst().get("operatorName"));
        assertEquals("编导1", snapshots.getFirst().get("directorName"));
    }

    /**
     * 运营发起审核时填写的账号档案必须写入快照。这些字段曾因不在 editable 白名单里被静默丢弃，
     * 导致审核页头部区始终空白。
     */
    @Test
    void draftAccountSnapshotKeepsOperatorSuppliedProfileText() {
        stubProfileDictionaries();
        MediaAccountDO account = profileAccount();

        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(service, "draftAccountSnapshot",
                account, Map.of("productGoal", "私域成交", "publishFrequency", "3条/周",
                        "productFormLabel", "短视频", "bottleneckLabel", "B3 内容同质化"));

        assertEquals("私域成交", snapshot.get("productGoal"));
        assertEquals("3条/周", snapshot.get("publishFrequency"));
        assertEquals("短视频", snapshot.get("productFormLabel"));
        assertEquals("B3 内容同质化", snapshot.get("bottleneckLabel"));
    }

    /** 字典字段只接受 value，label 由服务端解析，避免客户端伪造展示文案。 */
    @Test
    void draftAccountSnapshotResolvesDictionaryLabelsOnServer() {
        stubProfileDictionaries();
        MediaAccountDO account = profileAccount();

        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(service, "draftAccountSnapshot",
                account, Map.of("stageValue", "S3", "currentStatusLabel", "伪造的状态"));

        assertEquals("S3", snapshot.get("stageValue"));
        assertEquals("内容验证期", snapshot.get("sStageLabel"));
        // 客户端直接传 label 不生效，仍是服务端按原值解析出的文案。
        assertEquals("状态B", snapshot.get("currentStatusLabel"));
    }

    /** 字典外的取值必须拒绝，不能写进历史快照。 */
    @Test
    void draftAccountSnapshotRejectsValueOutsideDictionary() {
        // platform 先于 stage 解析，两个字典都要 stub。
        when(dictDataApi.getDictDataList("zsjos_account_platform")).thenReturn(List.of(
                dictData("douyin", "抖音")));
        when(dictDataApi.getDictDataList("zsjos_media_account_stage")).thenReturn(List.of(
                dictData("S2", "冷启动期")));
        MediaAccountDO account = profileAccount();

        assertServiceCode(CONTENT_REVIEW_BATCH_ITEMS_INVALID, () ->
                ReflectionTestUtils.invokeMethod(service, "draftAccountSnapshot",
                        account, Map.of("stageValue", "S99")));
    }

    /** 责任运营姓名由服务端按归属解析，不接受前端传入。 */
    @Test
    void draftAccountSnapshotResolvesOperatorNameFromOwnership() {
        stubProfileDictionaries();
        when(adminUserApi.getUserMap(Set.of(OPERATOR_ID))).thenReturn(Map.of(
                OPERATOR_ID, new AdminUserRespDTO().setId(OPERATOR_ID).setNickname("运营甲")));
        MediaAccountDO account = profileAccount();
        account.setDirectorUserId(null);

        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(service, "draftAccountSnapshot",
                account, Map.of("operatorName", "伪造的运营"));

        assertEquals("运营甲", snapshot.get("operatorName"));
    }

    private void stubProfileDictionaries() {
        when(dictDataApi.getDictDataList("zsjos_account_platform")).thenReturn(List.of(
                dictData("douyin", "抖音")));
        when(dictDataApi.getDictDataList("zsjos_media_account_stage")).thenReturn(List.of(
                dictData("S2", "冷启动期"), dictData("S3", "内容验证期")));
        when(dictDataApi.getDictDataList("zsjos_media_account_current_status")).thenReturn(List.of(
                dictData("B", "状态B")));
    }

    private MediaAccountDO profileAccount() {
        MediaAccountDO account = new MediaAccountDO();
        account.setId(3L);
        account.setAccountNo("ACC-3");
        account.setOwnerOperatorUserId(OPERATOR_ID);
        account.setPlatformValue("douyin");
        account.setSStage("S2");
        account.setCurrentStatusValue("B");
        return account;
    }

    private DictDataRespDTO dictData(String value, String label) {
        return new DictDataRespDTO().setValue(value).setLabel(label);
    }

    /**
     * 刚进入编导审核时条目还没有结论，decision 为 null。DECISIONS 是 Set.of，
     * contains(null) 抛 NPE，曾导致编导打开内容审核列表直接 500。
     */
    @Test
    void directorListExposesDecideActionBeforeAnyDecisionIsSaved() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        batch.setDirectorUserId(OPERATOR_ID);
        when(permissionApi.hasAnyPermissions(OPERATOR_ID, "zsjos:content-review:director-review"))
                .thenReturn(true);
        BpmTaskRespDTO task = new BpmTaskRespDTO();
        task.setId("task-1");
        task.setTaskDefinitionKey("directorReview");

        List<String> actions = ReflectionTestUtils.invokeMethod(service, "availableActions",
                batch, List.of(reviewItem(1L, null, null, false)), task, OPERATOR_ID);

        assertTrue(actions.contains("DIRECTOR_DECIDE"));
        assertFalse(actions.contains("DIRECTOR_COMPLETE"));
    }

    /** 结论缺失应返回业务错误，而不是 Set.of 的 NPE。 */
    @Test
    void decisionWithoutValueFailsAsBusinessError() {
        assertServiceCode(CONTENT_REVIEW_BATCH_STATE_INVALID, () ->
                ReflectionTestUtils.invokeMethod(service, "normalizeDecision", null, "备注"));
    }

    /**
     * SIMPLE 流程启动后先停在发起人提交节点，由引擎自动通过。该动作必须放行且不推进批次阶段，
     * 否则流程卡在发起人节点，批次永远进不到编导审核。
     */
    @Test
    void taskActionAllowsEngineAutoApprovalOfSubmissionNode() {
        service.validateTaskAction(taskContext("StartUserNode", OPERATOR_ID));

        verifyNoInteractions(batchMapper, itemMapper);
    }

    @Test
    void processResultIgnoresRepeatedEventKey() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL).setLastEventKey("event-1");
        mockLocatedEventBatch(batch);

        service.handleProcessResult(processEvent("event-1"));

        verifyNoInteractions(itemMapper, contentMapper, contentVersionMapper, contentService, materialService);
    }

    @Test
    void nonApprovedProcessResultFailsWhenFrozenVersionCannotBeReleased() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, null, null, false).setContentVersionId(201L);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        when(contentVersionMapper.unfreeze(201L)).thenReturn(0);

        BpmProcessInstanceStatusEvent event = processEvent("event-rejected");
        event.setStatus(BpmProcessInstanceStatusEnum.CANCEL.getStatus());
        assertServiceCode(CONTENT_REVIEW_VERSION_CONFLICT,
                () -> service.handleProcessResult(event));

        verify(itemMapper, never()).finalizeItem(any(), anyString(), any(), any());
        verify(batchMapper, never()).finalizeBatch(any(), anyString(), anyString(), any());
    }

    @Test
    void processResultValidatesEveryCollectionSnapshotBeforeWritingContent() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, true)
                .setCollectionSnapshotJson("{}");
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        when(reviewMaterialService.readAndValidate(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new ServiceException(CONTENT_REVIEW_COLLECTION_INVALID));

        assertServiceCode(CONTENT_REVIEW_COLLECTION_INVALID,
                () -> service.handleProcessResult(processEvent("event-2")));

        verifyNoInteractions(contentMapper, contentVersionMapper, contentService, materialService);
        verify(itemMapper, never()).finalizeItem(any(), anyString(), any(), any());
        verify(batchMapper, never()).finalizeBatch(any(), anyString(), anyString(), any());
    }

    @Test
    void rejectedRoundKeepsFrozenSnapshotsAndReturnsWholeBatch() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_RETURNED, false)
                .setContentId(101L).setContentVersionId(201L).setFinalComment("需要修改")
                .setFinalReviewedByUserId(9L).setVersion(0);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        ContentDO content = reviewableContent(101L, 90L, 1, 3);
        ContentVersionDO version = frozenVersion(201L, 101L);
        when(contentMapper.selectByIdForUpdate(101L, TENANT_ID)).thenReturn(content);
        when(contentVersionMapper.selectByIdForUpdate(201L, TENANT_ID)).thenReturn(version);
        when(contentVersionMapper.finishReview(eq(201L), eq("rejected"), eq("需要修改"), eq(9L), any())).thenReturn(1);
        when(itemMapper.finalizeItem(eq(item), eq(RESULT_RETURNED), eq(null), eq(null))).thenReturn(1);
        when(batchMapper.finalizeBatch(eq(batch), eq(BATCH_NEED_MODIFY), eq("event-return"), any())).thenReturn(1);
        BpmProcessInstanceStatusEvent event = processEvent("event-return");
        event.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus());
        service.handleProcessResult(event);
        verify(contentVersionMapper, never()).unfreeze(anyLong());
        verify(contentService).applyBatchReview(content, 3, false, "需要修改", 9L);
        verifyNoInteractions(materialService);
        org.junit.jupiter.api.Assertions.assertNotNull(version.getFrozenAt());
    }

    @Test
    void finalApprovalMakesContentReadyToPublishWithoutUnfreezingHistory() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, false)
                .setContentId(101L).setContentVersionId(201L).setFinalComment("通过")
                .setFinalReviewedByUserId(9L).setVersion(0);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        ContentDO content = reviewableContent(101L, 90L, 1, 3);
        when(contentMapper.selectByIdForUpdate(101L, TENANT_ID)).thenReturn(content);
        when(contentVersionMapper.selectByIdForUpdate(201L, TENANT_ID)).thenReturn(frozenVersion(201L, 101L));
        when(contentVersionMapper.finishReview(eq(201L), eq("approved"), eq("通过"), eq(9L), any())).thenReturn(1);
        when(itemMapper.finalizeItem(eq(item), eq(RESULT_READY_TO_PUBLISH), eq(null), eq(null))).thenReturn(1);
        when(batchMapper.finalizeBatch(eq(batch), eq(BATCH_COMPLETED), eq("approved-event"), any())).thenReturn(1);
        service.handleProcessResult(processEvent("approved-event"));
        verify(contentService).applyBatchReview(content, 3, true, "通过", 9L);
        verify(contentVersionMapper, never()).unfreeze(anyLong());
        verifyNoInteractions(materialService);
    }

    @Test
    void finalRejectionRequiresCompleteDecisionsAndDoesNotValidateCollection() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, true),
                reviewItem(2L, DECISION_APPROVED, DECISION_RETURNED, false)));
        BpmTaskActionContext context = taskContext("final", 9L);
        context.setAction(cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_REJECT);
        assertDoesNotThrow(() -> service.validateTaskAction(context));
        verifyNoInteractions(reviewMaterialService);
        context.setAction(cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_APPROVE);
        assertServiceCode(CONTENT_REVIEW_TASK_INVALID, () -> service.validateTaskAction(context));
    }

    @Test
    void finalRejectionCannotSkipUnreviewedItems() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, DECISION_APPROVED, DECISION_RETURNED, false)));
        BpmTaskActionContext context = taskContext("final", 9L);
        context.setAction(cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_REJECT);
        assertServiceCode(CONTENT_REVIEW_DECISION_INCOMPLETE, () -> service.validateTaskAction(context));
    }

    @Test
    void explicitFinalDecisionCannotContradictSavedItems() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        when(processTaskApi.getTodoTask(9L, "task-1")).thenReturn(new BpmTaskRespDTO()
                .setProcessInstanceId("process-1").setBusinessKey("content-review-batch:50")
                .setProcessDefinitionKey("content-review").setTaskDefinitionKey("final"));
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, DECISION_RETURNED, false)));
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCompleteReqVO();
        request.setExpectedVersion(0); request.setTaskId("task-1"); request.setReason("整批退回");
        request.setDecision(DECISION_APPROVED);
        assertServiceCode(CONTENT_REVIEW_TASK_INVALID, () -> service.completeFinal(batch.getId(), request, 9L));
        verify(processTaskApi, never()).approveTask(anyLong(), any());
        verify(processTaskApi, never()).rejectTask(anyLong(), any());
        request.setDecision(DECISION_RETURNED);
        service.completeFinal(batch.getId(), request, 9L);
        verify(processTaskApi).rejectTask(eq(9L), any());
    }

    @Test
    void revisionRejectsSourcesFromAnotherBatchBeforeCreatingAnything() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_NEED_MODIFY, STAGE_DONE).setStudentPersonId(77L);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_RETURNED, null, false).setContentId(10L).setContentVersionId(20L)));
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentDraftCreateReqVO();
        request.setStudentPersonId(77L); request.setAccountIds(List.of(90L));
        var work = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentDraftCreateReqVO.Work();
        work.setSourceContentId(999L); work.setSourceVersionId(20L); request.setWorks(List.of(work));
        assertServiceCode(CONTENT_REVIEW_BATCH_ITEMS_INVALID, () -> service.resubmit(batch.getId(), request, OPERATOR_ID));
        verifyNoInteractions(accountMapper, contentService);
    }

    @Test
    void revisionRejectsDuplicateChildBeforeCreatingAnything() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_NEED_MODIFY, STAGE_DONE).setStudentPersonId(77L);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        when(batchMapper.selectByRevisionOfBatchId(batch.getId())).thenReturn(List.of(new ContentReviewBatchDO().setId(51L)));
        var request = new cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentDraftCreateReqVO();
        request.setStudentPersonId(77L);
        assertServiceCode(CONTENT_REVIEW_VERSION_CONFLICT, () -> service.resubmit(batch.getId(), request, OPERATOR_ID));
        verifyNoInteractions(accountMapper, contentService);
    }

    @Test
    void registerPublishedRejectsStaleContentRecordVersion() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_COMPLETED, STAGE_DONE);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, false)
                .setContentId(101L).setContentVersionId(201L).setResultStatus(RESULT_READY_TO_PUBLISH);
        when(itemMapper.selectByIdForUpdate(item.getId(), TENANT_ID)).thenReturn(item);
        ContentDO content = reviewableContent(101L, 90L, 1, 4).setStatus(CONTENT_READY_TO_PUBLISH);
        ContentVersionDO version = frozenVersion(201L, 101L).setReviewDecision("approved");
        when(contentMapper.selectByIdForUpdate(101L, TENANT_ID)).thenReturn(content);
        when(contentVersionMapper.selectByIdForUpdate(201L, TENANT_ID)).thenReturn(version);
        ContentReviewPublishReqVO request = new ContentReviewPublishReqVO();
        request.setPlatformUrl("https://example.com/published/101");
        request.setPublishedAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        request.setExpectedContentVersion(3);

        assertServiceCode(CONTENT_REVIEW_PUBLISH_INVALID,
                () -> service.registerPublished(batch.getId(), item.getId(), request, OPERATOR_ID));

        verify(contentService, never()).registerPublished(any(), any(), anyString(), any(), anyLong());
        verify(itemMapper, never()).markPublished(any(), anyString(), any(), anyLong());
    }

    private void mockLockedBatch(ContentReviewBatchDO batch) {
        when(batchMapper.selectByProcessInstanceId("process-1")).thenReturn(batch);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
    }

    private void mockLocatedEventBatch(ContentReviewBatchDO batch) {
        when(batchMapper.selectByProcessInstanceId("process-1")).thenReturn(batch);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
    }

    private ContentReviewBatchDO reviewBatch(String status, String stage) {
        return new ContentReviewBatchDO().setId(50L).setBatchNo("CRB-1").setAccountId(90L)
                .setOperatorUserId(OPERATOR_ID).setDirectorUserId(OPERATOR_ID)
                .setStatus(status).setCurrentStage(stage).setProcessDefinitionId("definition-1")
                .setProcessDefinitionKey("content-review").setProcessDefinitionVersion(3)
                .setProcessInstanceId("process-1").setBusinessKey("content-review-batch:50")
                .setContextSnapshotJson(JsonUtils.toJsonString(Map.of(
                        "directorTaskKey", "director",
                        "finalTaskKey", "final",
                        "productionMaterialTypeCode", MATERIAL_TYPE_PRODUCTION_CONTENT,
                        "productionMaterialSchemaVersionId", 99L,
                        "productionMaterialSchemaHash", "schema-hash",
                        "materialFieldMapping", Map.of(),
                        "materialDefaultValues", Map.of(),
                        "account", Map.of())))
                .setVersion(0);
    }

    private ContentReviewBatchItemDO reviewItem(Long id, String directorDecision, String finalDecision,
                                                boolean collectMaterial) {
        return new ContentReviewBatchItemDO().setId(id).setBatchId(50L)
                .setDirectorDecision(directorDecision).setFinalDecision(finalDecision)
                .setCollectMaterial(collectMaterial).setVersion(0);
    }

    private BpmTaskActionContext taskContext(String taskKey, Long userId) {
        return new BpmTaskActionContext().setUserId(userId).setAction(ACTION_APPROVE)
                .setTaskId("task-1").setTaskDefinitionKey(taskKey).setProcessInstanceId("process-1")
                .setProcessDefinitionId("definition-1").setProcessDefinitionKey("content-review")
                .setProcessDefinitionVersion(3).setBusinessKey("content-review-batch:50")
                .setTenantId(String.valueOf(TENANT_ID));
    }

    private BpmProcessInstanceStatusEvent processEvent(String eventKey) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent(this);
        event.setId("process-1");
        event.setProcessDefinitionId("definition-1");
        event.setProcessDefinitionKey("content-review");
        event.setProcessDefinitionVersion(3);
        event.setBusinessKey("content-review-batch:50");
        event.setStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        event.setEventKey(eventKey);
        return event;
    }

    private ContentDO reviewableContent(Long id, Long accountId, Integer currentVersionNo, Integer version) {
        return new ContentDO().setId(id).setContentNo("CT-" + id).setAccountId(accountId)
                .setOwnerOperatorUserId(OPERATOR_ID).setStatus(CONTENT_ACCEPTANCE)
                .setCurrentVersionNo(currentVersionNo).setVersion(version);
    }

    private ContentVersionDO completeVersion(Long id, Long contentId) {
        return new ContentVersionDO().setId(id).setContentId(contentId).setVersionNo(1)
                .setTitleSnapshot("标题 " + id).setScriptText("正文 " + id)
                .setCoverSnapshotJson("[{\"fileId\":1}]")
                .setDeliverableSnapshotJson("[{\"fileId\":2}]")
                .setLeadResourceUrl("https://example.com/lead/" + id)
                .setPlannedPublishAt(LocalDateTime.of(2026, 9, 8, 12, 0));
    }

    private ContentVersionDO frozenVersion(Long id, Long contentId) {
        return new ContentVersionDO().setId(id).setContentId(contentId).setVersionNo(1)
                .setFrozenAt(LocalDateTime.of(2026, 9, 8, 9, 0));
    }

    private void assertServiceCode(ErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(expected.getCode(), error.getCode());
    }
}

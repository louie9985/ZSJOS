package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.ZsjosApprovalAttachmentSupport;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackDynamicFormService;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackObjectPermissionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 反馈需求审批卡。
 *
 * <p>覆盖三处容易静默出错的点：
 * <ul>
 *   <li><b>可见性</b>——审批人此前按 read-admin 判定，看到的是「无权查看」，
 *       而审批按钮仍在，等于逼人盲签。现在按流程参与人放行，但不能放成人人可见。</li>
 *   <li><b>轮次</b>——已办里点开的是当时那一轮。只按 workOrderId 取数会把历史轮次
 *       显示成最新一轮，且不会报错。</li>
 *   <li><b>附件 MIME</b>——不传 MIME 时前端把所有图片降级成下载链接。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FeedbackContentProviderTest {

    private static final String BUSINESS_KEY = "feedback:100:round:2";
    private static final long SUBMITTER_ID = 11L;
    private static final long DEPARTMENT_LEADER_ID = 22L;
    private static final long CHAIRMAN_ID = 33L;
    private static final long OUTSIDER_ID = 44L;
    private static final long MANAGER_ID = 55L;

    @Mock private FeedbackMapper feedbackMapper;
    @Mock private FeedbackRoundMapper roundMapper;
    @Mock private FeedbackDynamicFormService dynamicFormService;
    @Mock private FileApi fileApi;
    @Mock private FeedbackObjectPermissionProvider permissionProvider;

    private FeedbackContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FeedbackContentProvider();
        ZsjosApprovalAttachmentSupport support = new ZsjosApprovalAttachmentSupport();
        ReflectionTestUtils.setField(support, "fileApi", fileApi);
        ReflectionTestUtils.setField(provider, "feedbackMapper", feedbackMapper);
        ReflectionTestUtils.setField(provider, "roundMapper", roundMapper);
        ReflectionTestUtils.setField(provider, "dynamicFormService", dynamicFormService);
        ReflectionTestUtils.setField(provider, "attachmentSupport", support);
        ReflectionTestUtils.setField(provider, "permissionProvider", permissionProvider);
    }

    // ------------------------------------------------------------------ 可见性

    @Test
    void departmentLeaderSeesTheCard() {
        stubFeedback();
        stubRounds();
        stubFormFieldsAndValues();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        assertNotNull(card);
        assertNull(card.getMessage(), "部门负责人不应看到无权提示");
        assertEquals(fieldValue(card, "申请内容", "需求标题"), "需要增加批量导出");
    }

    @Test
    void chairmanSeesTheCard() {
        stubFeedback();
        stubRounds();
        stubFormFieldsAndValues();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, CHAIRMAN_ID);

        assertNull(card.getMessage(), "董事长不应看到无权提示");
        assertEquals(fieldValue(card, "申请内容", "需求标题"), "需要增加批量导出");
    }

    @Test
    void submitterSeesTheCard() {
        stubFeedback();
        stubRounds();
        stubFormFieldsAndValues();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, SUBMITTER_ID);

        assertNull(card.getMessage());
    }

    @Test
    void managerSeesTheCard() {
        stubFeedback();
        stubRounds();
        stubFormFieldsAndValues();
        when(permissionProvider.hasPermission(anyLong(), eq("read-admin"), eq(MANAGER_ID))).thenReturn(true);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, MANAGER_ID);

        assertNull(card.getMessage());
    }

    @Test
    void unrelatedViewerGetsNoAccess() {
        stubFeedback();
        stubRounds();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, OUTSIDER_ID);

        assertNotNull(card.getMessage(), "无关账号必须拿到无权提示，放宽不能放宽成人人可见");
    }

    @Test
    void approverOfAnotherRoundDoesNotSeeThisRound() {
        stubFeedback();
        // 第 1 轮的审批人是 22，第 2 轮换成了别人——第 1 轮的审批人不应再看第 2 轮。
        FeedbackRoundDO first = round(1, FeedbackConstants.STATUS_APPROVAL_REJECTED,
                context(DEPARTMENT_LEADER_ID, CHAIRMAN_ID));
        FeedbackRoundDO second = round(2, FeedbackConstants.STATUS_APPROVING, context(66L, 77L));
        when(roundMapper.selectByFeedbackId(1L)).thenReturn(List.of(first, second));

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        assertNotNull(card.getMessage(), "审批人判定按轮次，不能跨轮开闸");
    }

    @Test
    void briefForApproverCarriesNoRoute() {
        stubFeedback();
        stubRounds();

        BpmApprovalBriefVO brief = provider.brief(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        assertNotNull(brief);
        // 反馈页按 read-own 放行，审批人点进去只会看到无权页；内容就在卡片里。
        assertNull(brief.getRoute(), "审批人不该拿到反馈页深链");
        assertNull(brief.getQuery());
    }

    @Test
    void briefForSubmitterCarriesFeedbackId() {
        stubFeedback();
        stubRounds();

        BpmApprovalBriefVO brief = provider.brief(BUSINESS_KEY, SUBMITTER_ID);

        assertNotNull(brief.getRoute());
        assertEquals(1L, brief.getQuery().get("feedbackId"));
    }

    // ------------------------------------------------------------------ 轮次

    @Test
    void historicalRoundRendersItsOwnSnapshot() {
        stubFeedback();
        FeedbackRoundDO first = round(1, FeedbackConstants.STATUS_APPROVAL_REJECTED,
                context(DEPARTMENT_LEADER_ID, CHAIRMAN_ID));
        first.setRejectReason("预算口径不清晰");
        first.setValueSnapshotJson(JsonUtils.toJsonString(Map.of("title", "第一轮内容")));
        FeedbackRoundDO second = round(2, FeedbackConstants.STATUS_APPROVING,
                context(DEPARTMENT_LEADER_ID, CHAIRMAN_ID));
        second.setValueSnapshotJson(JsonUtils.toJsonString(Map.of("title", "第二轮内容")));
        when(roundMapper.selectByFeedbackId(1L)).thenReturn(List.of(first, second));
        stubFormFieldsAndValues(Map.of("title", "第一轮内容"));

        BpmApprovalDetailVO card = provider.detail("feedback:100:round:1", DEPARTMENT_LEADER_ID);

        // 看第 1 轮的已办任务，必须拿到第 1 轮的字段值与驳回原因。
        assertEquals(fieldValue(card, "申请内容", "需求标题"), "第一轮内容");
        assertEquals(fieldValue(card, "审批", "轮次"), "第 1 轮");
        assertEquals(fieldValue(card, "驳回原因", "驳回原因"), "预算口径不清晰");
    }

    @Test
    void missingRoundNumberFallsBackToLatest() {
        stubFeedback();
        stubRounds();
        stubFormFieldsAndValues();

        // businessId 只带 workOrderId 时退回最新一轮，而不是"什么都没有"。
        BpmApprovalDetailVO card = provider.detail("feedback:100", DEPARTMENT_LEADER_ID);

        assertEquals(fieldValue(card, "审批", "轮次"), "第 2 轮");
    }

    // ------------------------------------------------------------------ 附件

    @Test
    void attachmentCarriesMimeTypeSoImagesPreview() {
        stubFeedback();
        FeedbackRoundDO current = round(2, FeedbackConstants.STATUS_APPROVING,
                context(DEPARTMENT_LEADER_ID, CHAIRMAN_ID));
        current.setValueSnapshotJson(JsonUtils.toJsonString(Map.of(
                "title", "需要增加批量导出",
                "evidence", List.of(Map.of("id", 7L, "name", "截图.png",
                        "type", "image/png", "size", 2048L, "url", "https://tmp/7")))));
        when(roundMapper.selectByFeedbackId(1L)).thenReturn(List.of(current));
        stubFormFieldsAndValues(Map.of(
                "title", "需要增加批量导出",
                "evidence", List.of(Map.of("id", 7L, "name", "截图.png",
                        "type", "image/png", "size", 2048L, "url", "https://signed/7"))));
        stubSignedUrl();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        BpmApprovalFieldVO attachmentField = attachments(card, "申请附件");
        assertNotNull(attachmentField, "申请附件分组不应整块消失");
        BpmApprovalFieldVO.Attachment attachment = attachmentField.getAttachments().get(0);
        // MIME 决定前端走 Image.PreviewGroup 还是下载链接，丢了就只剩链接。
        assertEquals("image/png", attachment.getContentType());
        assertTrue(BpmApprovalFieldVO.isImage(attachment));
        assertEquals("https://signed/7", attachment.getUrl());
    }

    @Test
    void resultAttachmentsResolveMetadataAndSkipMissingFiles() {
        FeedbackDO feedback = stubFeedback();
        feedback.setResultAttachmentIdsJson(JsonUtils.toJsonString(List.of(8L, 9L)));
        stubRounds();
        stubNoFields();
        // 8 号文件签名成功，9 号已不可读——只丢 9 号，不能整块消失。
        when(fileApi.presignGetUrl(8L, 600)).thenReturn("https://signed/8");
        when(fileApi.presignGetUrl(9L, 600)).thenThrow(new IllegalStateException("gone"));
        FileInfoRespDTO file = file();
        file.setName("处理结果.pdf");
        file.setType("application/pdf");
        when(fileApi.getFileInfo(8L)).thenReturn(file);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        BpmApprovalFieldVO group = attachments(card, "处理结果附件");
        assertNotNull(group);
        assertEquals(1, group.getAttachments().size());
        assertEquals("处理结果.pdf", group.getAttachments().get(0).getName());
        assertEquals("application/pdf", group.getAttachments().get(0).getContentType());
    }

    @Test
    void noAttachmentsLeavesNoEmptyGroup() {
        stubFeedback();
        stubRounds();
        stubNoFields();

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        assertNull(attachments(card, "申请附件"));
        assertNull(attachments(card, "处理结果附件"));
    }

    // ------------------------------------------------------------------ 兜底

    @Test
    void missingFeedbackReturnsNotFound() {
        when(feedbackMapper.selectByWorkOrderId(100L)).thenReturn(null);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_KEY, DEPARTMENT_LEADER_ID);

        assertNotNull(card.getMessage());
        assertTrue(card.getMessage().contains("未找到"));
    }

    // ------------------------------------------------------------------ 夹具

    private FeedbackDO stubFeedback() {
        FeedbackDO feedback = new FeedbackDO();
        feedback.setId(1L);
        feedback.setWorkOrderId(100L);
        feedback.setFeedbackType(FeedbackConstants.TYPE_REQUIREMENT);
        feedback.setFeedbackNo("REQ-2026-0001");
        feedback.setTitle("需要增加批量导出");
        feedback.setStatus(FeedbackConstants.STATUS_APPROVING);
        feedback.setSubmitterSubjectType(FeedbackConstants.SUBJECT_ADMIN);
        feedback.setSubmitterUserId(SUBMITTER_ID);
        feedback.setSubmitterNameSnapshot("张三");
        feedback.setApprovalRoundNo(2);
        lenient().when(feedbackMapper.selectByWorkOrderId(100L)).thenReturn(feedback);
        return feedback;
    }

    private void stubRounds() {
        lenient().when(roundMapper.selectByFeedbackId(1L)).thenReturn(List.of(
                round(2, FeedbackConstants.STATUS_APPROVING, context(DEPARTMENT_LEADER_ID, CHAIRMAN_ID))));
    }

    /**
     * 表单定义与值。字典值给的是提交时冻结的快照（含 label），
     * 而不是字典当前的值——审批人看的是当时提交了什么。
     */
    private void stubFormFieldsAndValues() {
        stubFormFieldsAndValues(Map.of(
                "title", "需要增加批量导出",
                "priority", Map.of("type", "zsjos_priority", "value", "high", "label", "高")));
    }

    private void stubFormFieldsAndValues(Map<String, Object> values) {
        lenient().when(dynamicFormService.parseSnapshot(anyString())).thenReturn(List.of(
                field("title", "需求标题", "text"),
                field("priority", "优先级", "dictionary"),
                field("evidence", "佐证材料", "image")));
        lenient().when(dynamicFormService.readDisplayValues(anyString(), anyList()))
                .thenReturn(new LinkedHashMap<>(values));
    }

    /** 表单没有可展示字段的轮次（例如仅上传附件）。 */
    private void stubNoFields() {
        lenient().when(dynamicFormService.parseSnapshot(anyString())).thenReturn(List.of());
        lenient().when(dynamicFormService.readDisplayValues(anyString(), anyList()))
                .thenReturn(new LinkedHashMap<>());
    }

    private void stubSignedUrl() {
        lenient().when(fileApi.presignGetUrl(anyLong(), eq(600)))
                .thenAnswer(invocation -> "https://signed/" + invocation.getArgument(0, Long.class));
    }

    private static FeedbackRoundDO round(int roundNo, String status, Map<String, Object> context) {
        FeedbackRoundDO row = new FeedbackRoundDO();
        row.setId((long) roundNo);
        row.setFeedbackId(1L);
        row.setRoundNo(roundNo);
        row.setStatus(status);
        row.setApprovalContextJson(JsonUtils.toJsonString(context));
        row.setFormSnapshotJson("[]");
        row.setValueSnapshotJson("{}");
        return row;
    }

    private static Map<String, Object> context(Long departmentLeaderId, Long chairmanId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("departmentLeaderUserId", departmentLeaderId);
        context.put("departmentLeaderName", departmentLeaderId == null ? null : "部门负责人");
        context.put("chairmanUserId", chairmanId);
        context.put("chairmanName", chairmanId == null ? null : "董事长");
        return context;
    }

    private static FeedbackFormRespVO.Field field(String key, String label, String type) {
        FeedbackFormRespVO.Field field = new FeedbackFormRespVO.Field();
        field.setKey(key);
        field.setLabel(label);
        field.setType(type);
        return field;
    }

    private static FileInfoRespDTO file() {
        FileInfoRespDTO file = new FileInfoRespDTO();
        file.setId(7L);
        file.setName("截图.png");
        file.setType("image/png");
        file.setSize(2048L);
        return file;
    }

    /** 取某个分组里某个标签的字段值；分组或字段不存在时返回 null。 */
    private static String fieldValue(BpmApprovalDetailVO card, String groupTitle, String label) {
        for (BpmApprovalDetailVO.Group group : card.getGroups()) {
            if (!groupTitle.equals(group.getTitle())) {
                continue;
            }
            for (BpmApprovalFieldVO field : group.getFields()) {
                if (label.equals(field.getLabel())) {
                    return field.getValue();
                }
            }
        }
        return null;
    }

    /** 取某个附件分组里的附件字段；不存在时返回 null。 */
    private static BpmApprovalFieldVO attachments(BpmApprovalDetailVO card, String groupTitle) {
        for (BpmApprovalDetailVO.Group group : new ArrayList<>(card.getGroups())) {
            if (!groupTitle.equals(group.getTitle())) {
                continue;
            }
            for (BpmApprovalFieldVO field : group.getFields()) {
                if (field.getAttachments() != null && !field.getAttachments().isEmpty()) {
                    return field;
                }
            }
        }
        return null;
    }
}

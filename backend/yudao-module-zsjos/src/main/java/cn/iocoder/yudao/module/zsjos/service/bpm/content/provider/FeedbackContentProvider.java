package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.ZsjosApprovalAttachmentSupport;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackApprovalContext;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackDynamicFormService;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackObjectPermissionProvider;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 反馈需求审批的业务内容。
 *
 * <p>businessKey 是**四段式** {@code feedback:{workOrderId}:round:{roundNo}}
 * （{@code FeedbackServiceImpl.createRequirementRound}）。
 *
 * <p>⚠️ 第二段是 **workOrderId 而不是 feedbackId**——写成 {@code parseLong(key.split(":")[1])}
 * 然后当成 feedbackId 去 selectById，会静默取到另一条无关的反馈。
 * 这里先用 workOrderId 换出唯一的 FeedbackDO（有 uk_tenant_work_order 唯一约束），
 * 再用第四段的 roundNo 定位具体轮次。
 *
 * <p>同一工单可能有多轮审批：历史轮次的 processInstanceId 不在 FeedbackDO 上，
 * 而在 FeedbackRoundDO，因此轮次字段从 round 表取。
 *
 * <h3>审批人为什么能看</h3>
 *
 * <p>审批人（部门负责人、董事长）既不是提交人，也通常不持有
 * {@code zsjos:feedback:requirement:manage}——那是「反馈管理员」的口径。
 * 早先这里按 {@code read-admin} 判定，导致审批人打开待办只看到一句「无权查看」，
 * 而审批按钮仍在，等于逼人盲签。现在按 <b>流程参与人</b> 判定：
 * 该轮次 {@code approval_context_json} 里记录的指定审批人即可查看。
 * 上游 {@code BpmApprovalContentServiceImpl.resolve} 已按 userId 校验过任务归属，
 * 这里是第二道闸，只放行这一笔审批需要的那条单据。
 *
 * <h3>为什么按轮次快照渲染</h3>
 *
 * <p>审批人审的是「当时提交了什么」。字段值与字典标签一律取自
 * {@code round.valueSnapshotJson}（提交时冻结），不按当前字典重新解析——
 * 管理员事后改了字典选项时，按当前字典渲染会和提交人对不上。
 */
@Slf4j
@Component
public class FeedbackContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "feedback:";

    /** 审批上下文快照里的审批人姓名键（@see FeedbackApprovalContext）。 */
    private static final String CONTEXT_DEPARTMENT_LEADER_NAME = FeedbackApprovalContext.DEPARTMENT_LEADER_NAME;
    private static final String CONTEXT_CHAIRMAN_NAME = FeedbackApprovalContext.CHAIRMAN_NAME;

    @Resource
    private FeedbackMapper feedbackMapper;
    @Resource
    private FeedbackRoundMapper roundMapper;
    @Resource
    private FeedbackDynamicFormService dynamicFormService;
    @Resource
    private ZsjosApprovalAttachmentSupport attachmentSupport;
    @Resource
    private FeedbackObjectPermissionProvider permissionProvider;

    @Override
    public String bizType() {
        return "feedback";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    /**
     * 返回 {@code workOrderId:roundNo}；剥离前缀后缺轮次段时只返回 {@code workOrderId}。
     *
     * <p>必须带上轮次：契约只把 businessKey 交到这里，{@link #detail} 拿不到原始 key。
     * 同一工单多轮审批，只按 workOrderId 取数会让历史轮次的任务显示成最新一轮的内容，
     * 而且不会报错——审批记录和单据对不上，没人会发现。
     */
    @Override
    public String parseBusinessId(String businessKey) {
        // 剥离前缀后是 workOrderId:round:roundNo；第二段是字面量 "round"。
        String rest = BpmApprovalBusinessKey.strip(PREFIX, businessKey);
        if (rest == null) {
            return null;
        }
        String workOrderId = BpmApprovalBusinessKey.segment(rest, 0);
        if (workOrderId == null || !workOrderId.matches("\\d+")) {
            return null;
        }
        String roundNo = BpmApprovalBusinessKey.segment(rest, 2);
        return roundNo != null && roundNo.matches("\\d+") ? workOrderId + ":" + roundNo : workOrderId;
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        FeedbackDO feedback = load(businessId);
        if (feedback == null) {
            return null;
        }
        Integer roundNo = parseRoundNo(businessId);
        FeedbackRoundDO round = findRound(feedback.getId(), roundNo);
        if (!canView(feedback, round, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(feedback.getFeedbackNo() + " · " + feedback.getTitle());
        brief.setSubtitle(BpmApprovalFormat.truncate(
                round != null && round.getRejectReason() != null
                        ? round.getRejectReason() : feedback.getRejectReason(), 40));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("类型", typeText(feedback.getFeedbackType())),
                BpmApprovalFieldVO.of("提交人", feedback.getSubmitterNameSnapshot()),
                BpmApprovalFieldVO.of("轮次", roundText(round, feedback.getApprovalRoundNo())),
                BpmApprovalFieldVO.of("状态", statusText(feedback.getStatus()))));
        // 只有提交人能跳反馈页：它按 read-own 放行，审批人和管理员进去只会看到无权。
        // 审批人要看的内容就在下面这张卡里，不需要另一个入口。
        if (isSubmitter(feedback, viewerId)) {
            brief.setRoute("/zsjos/feedback");
            brief.setQuery(new LinkedHashMap<>());
            brief.getQuery().put("feedbackId", feedback.getId());
        }
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        FeedbackDO feedback = load(businessId);
        if (feedback == null) {
            return BpmApprovalDetailVO.notFound(bizType(), "反馈需求");
        }
        Integer roundNo = parseRoundNo(businessId);
        FeedbackRoundDO round = findRound(feedback.getId(), roundNo);
        if (!canView(feedback, round, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "反馈需求");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(feedback.getFeedbackNo() + " · " + feedback.getTitle());
        card.setStatusText(statusText(feedback.getStatus()));

        card.getGroups().add(group("反馈信息", List.of(
                BpmApprovalFieldVO.of("反馈单号", feedback.getFeedbackNo()),
                BpmApprovalFieldVO.of("类型", typeText(feedback.getFeedbackType())),
                BpmApprovalFieldVO.of("标题", feedback.getTitle()),
                BpmApprovalFieldVO.of("状态", statusText(feedback.getStatus())),
                BpmApprovalFieldVO.of("提交人", feedback.getSubmitterNameSnapshot()),
                BpmApprovalFieldVO.of("处理人", feedback.getAssigneeNameSnapshot()))));

        card.getGroups().add(group("审批", roundFields(feedback, round)));

        // 申请内容：审批人要审的就是这块。此前只列了元数据，用户填的内容一个字都没有。
        // 按轮次快照渲染，字典标签用提交时冻结的那份。
        if (round != null) {
            List<BpmApprovalFieldVO> content = contentFields(round);
            if (!content.isEmpty()) {
                card.getGroups().add(group("申请内容", content));
            }
        }

        String rejectReason = round != null && round.getRejectReason() != null
                ? round.getRejectReason() : feedback.getRejectReason();
        if (rejectReason != null) {
            card.getGroups().add(wideGroup("驳回原因", rejectReason));
        }
        if (feedback.getLastReplySummary() != null) {
            card.getGroups().add(wideGroup("最近回复", feedback.getLastReplySummary()));
        }
        if (feedback.getCompletedResult() != null) {
            card.getGroups().add(wideGroup("处理结果", feedback.getCompletedResult()));
        }
        // 需求反馈的审批对象常常是附件里的图片/文档，必须一并展示。
        addAttachments(card, feedback, round);
        return card;
    }

    /**
     * 审批分组：本轮的轮次、状态、提交时间，以及审批人快照。
     *
     * <p>轮次信息全部取自 round 而非 FeedbackDO——多轮审批时 FeedbackDO 上的
     * 是「最新一轮」，用它渲染历史轮次会张冠李戴。
     */
    private List<BpmApprovalFieldVO> roundFields(FeedbackDO feedback, FeedbackRoundDO round) {
        List<BpmApprovalFieldVO> fields = new ArrayList<>();
        fields.add(BpmApprovalFieldVO.of("轮次", roundText(round, feedback.getApprovalRoundNo())));
        if (round != null) {
            fields.add(BpmApprovalFieldVO.of("轮次状态", roundStatusText(round.getStatus())));
            fields.add(BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(round.getSubmittedAt())));
        }
        fields.add(BpmApprovalFieldVO.of("最近动态", BpmApprovalFormat.dateTime(feedback.getLastActivityAt())));
        Map<String, Object> context = approvalContext(round);
        String departmentLeader = FeedbackApprovalContext.approverName(context, CONTEXT_DEPARTMENT_LEADER_NAME);
        if (departmentLeader != null) {
            fields.add(BpmApprovalFieldVO.of("部门负责人", departmentLeader));
        }
        String chairman = FeedbackApprovalContext.approverName(context, CONTEXT_CHAIRMAN_NAME);
        if (chairman != null) {
            fields.add(BpmApprovalFieldVO.of("董事长", chairman));
        }
        if (feedback.getSupportTypeLabelSnapshot() != null) {
            fields.add(BpmApprovalFieldVO.of("支持类型", feedback.getSupportTypeLabelSnapshot()));
        }
        return fields;
    }

    /**
     * 把本轮提交的动态表单值渲染成审批字段。
     *
     * <p>复用 {@code FeedbackDynamicFormService.readDisplayValues}：它按字段类型
     * 把字典快照、评分、附件快照解析成可展示的值。字典标签来自 value 快照本身
     * （提交时写入），不查当前字典。
     */
    private List<BpmApprovalFieldVO> contentFields(FeedbackRoundDO round) {
        List<FeedbackFormRespVO.Field> fields = dynamicFormService.parseSnapshot(round.getFormSnapshotJson());
        if (fields.isEmpty()) {
            return List.of();
        }
        Map<String, Object> values = dynamicFormService.readDisplayValues(
                round.getValueSnapshotJson(), fields);
        List<BpmApprovalFieldVO> result = new ArrayList<>();
        for (FeedbackFormRespVO.Field field : fields) {
            Object value = values.get(field.getKey());
            // 附件字段单独成组展示，这里只出文本类字段，避免同一份附件出现两次。
            if ("upload".equals(field.getType()) || "image".equals(field.getType())) {
                continue;
            }
            String text = displayValue(value);
            if (text == null || text.isBlank()) {
                continue;
            }
            // 需求描述这类长文本整行占满，否则挤在半列里读不了。
            BpmApprovalFieldVO item = BpmApprovalFieldVO.of(field.getLabel(), text);
            if ("textarea".equals(field.getType()) || text.length() > 60) {
                item.setSpan(2);
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 挂上申请时上传的附件与处理结果附件。
     *
     * <p><b>不</b>走 {@code FeedbackService.getAdmin}：它带
     * {@code @ZsjosPermission(action = "read-admin")}，审批人没有该权限，
     * 切面抛异常会被上层 catch 吞掉，结果是附件整块消失且不留日志。
     * 这里直接读轮次快照，自己解析 URL 与 MIME。
     */
    private void addAttachments(BpmApprovalDetailVO card, FeedbackDO feedback, FeedbackRoundDO round) {
        List<BpmApprovalFieldVO.Attachment> submitted = new ArrayList<>();
        if (round != null) {
            List<FeedbackFormRespVO.Field> fields = dynamicFormService.parseSnapshot(round.getFormSnapshotJson());
            Map<String, Object> values = dynamicFormService.readDisplayValues(
                    round.getValueSnapshotJson(), fields);
            for (FeedbackFormRespVO.Field field : fields) {
                if (!"upload".equals(field.getType()) && !"image".equals(field.getType())) {
                    continue;
                }
                collectAttachments(submitted, field.getLabel(), values.get(field.getKey()));
            }
        }
        List<BpmApprovalFieldVO.Attachment> results = new ArrayList<>();
        for (Long id : parseLongs(feedback.getResultAttachmentIdsJson())) {
            BpmApprovalFieldVO.Attachment attachment = attachmentSupport.resolveAttachment(id);
            if (attachment != null) {
                results.add(attachment);
            }
        }
        attachmentSupport.addGroup(card, "申请附件", submitted, true);
        attachmentSupport.addGroup(card, "处理结果附件", results, true);
    }

    /**
     * 从一个动态表单字段值里挑出附件。upload/image 字段的值是附件快照数组，
     * 每项形如 {id, name, type, size, url}；url 为空说明签名失败，跳过而不是显示死链。
     */
    private static void collectAttachments(List<BpmApprovalFieldVO.Attachment> target,
                                           String label, Object value) {
        if (!(value instanceof Collection<?> items)) {
            return;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> snapshot)) {
                continue;
            }
            Object url = snapshot.get("url");
            if (url == null || String.valueOf(url).isBlank()) {
                continue;
            }
            Object name = snapshot.get("name") != null ? snapshot.get("name") : snapshot.get("originalName");
            // 第 3 参是 MIME，前端据此决定走图片预览还是下载链接。快照里的键是 type。
            target.add(BpmApprovalFieldVO.attachment(
                    label + "：" + (name == null ? "附件" : String.valueOf(name)),
                    String.valueOf(url),
                    stringValue(snapshot.get("type")),
                    parseSize(snapshot.get("size"))));
        }
    }

    /**
     * 可见性：管理口径 ∨ 提交人 ∨ 本轮指定审批人。
     *
     * <p>审批人判定只看**该轮次**的审批人快照。上游已按 userId 校验过任务归属，
     * 因此这里不会因为放宽而让无关账号看到单据。
     */
    private boolean canView(FeedbackDO feedback, FeedbackRoundDO round, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (isSubmitter(feedback, viewerId)) {
            return true;
        }
        try {
            if (permissionProvider.hasPermission(feedback.getId(), "read-admin", viewerId)
                    || permissionProvider.hasPermission(feedback.getId(), "read-approver", viewerId)) {
                return true;
            }
        } catch (Exception ex) {
            // 管理口径解析失败不影响审批人判定，继续往下走。
            log.debug("[canView][反馈({}) 管理权限解析失败：{}]", feedback.getId(), ex.toString());
        }
        Map<String, Object> context = approvalContext(round);
        return FeedbackApprovalContext.isApprover(context, viewerId);
    }

    /** 提交人本人（仅员工主体；兼职端账号走 feedbackId 的另一套入口）。 */
    private static boolean isSubmitter(FeedbackDO feedback, Long viewerId) {
        return FeedbackConstants.SUBJECT_ADMIN.equals(feedback.getSubmitterSubjectType())
                && Objects.equals(feedback.getSubmitterUserId(), viewerId);
    }

    /**
     * 轮次的审批人快照。
     *
     * <p>解析口径与 {@code FeedbackObjectPermissionProvider} 共用一份实现——
     * 两处判定必须一致，否则会出现「能打开详情页却看不到内容」的自相矛盾状态。
     */
    private static Map<String, Object> approvalContext(FeedbackRoundDO round) {
        return FeedbackApprovalContext.parse(round);
    }

    private static Long parseSize(Object size) {
        if (size == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(size));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /** 字段值转展示文本。字典快照取冻结的 label，评分直接出数字。 */
    private static String displayValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> snapshot) {
            Object label = snapshot.get("label");
            return label != null ? String.valueOf(label) : stringValue(snapshot.get("value"));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(FeedbackContentProvider::displayValue)
                    .filter(Objects::nonNull).reduce((a, b) -> a + "、" + b).orElse(null);
        }
        return String.valueOf(value);
    }

    /**
     * businessId 形如 {@code workOrderId:roundNo}；只有 workOrderId 时返回 null，
     * 由 {@link #findRound} 退回最新一轮。
     */
    /**
     * 规整 businessId 为 {@code workOrderId}（无轮次）或 {@code workOrderId:roundNo}。
     *
     * <p>两种入参都要吃：{@link #parseBusinessId} 的产物是已解析的
     * {@code workOrderId:roundNo}，而解析失败时调用方可能直接把原始 businessKey
     * 透传进来。按冒号取首段会把 {@code feedback} 当成 id，因此先剥前缀。
     */
    private static String normalizeBusinessId(String businessId) {
        if (businessId == null) {
            return null;
        }
        return businessId.startsWith(PREFIX)
                ? BpmApprovalBusinessKey.strip(PREFIX, businessId)
                : businessId;
    }

    /**
     * 取轮次。
     *
     * <p>两种入参要吃：{@link #parseBusinessId} 的产物是 {@code workOrderId:roundNo}，
     * 而解析失败时调用方可能把原始 key 透传进来（{@code workOrderId:round:roundNo}）。
     * 按固定下标取会在两种形态间取到 {@code round} 这个字面量段，因此逐个候选试，
     * 只认数字段。
     */
    private static Integer parseRoundNo(String businessId) {
        String rest = normalizeBusinessId(businessId);
        if (rest == null) {
            return null;
        }
        for (int index = 1; index <= 2; index++) {
            Integer candidate = positiveInt(BpmApprovalBusinessKey.segment(rest, index));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Integer positiveInt(String value) {
        if (value == null || !value.matches("\\d+")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** businessId 的首段是 workOrderId；用唯一约束换出唯一的一条反馈。 */
    private FeedbackDO load(String businessId) {
        String rest = normalizeBusinessId(businessId);
        String workOrderId = rest == null ? null : BpmApprovalBusinessKey.segment(rest, 0);
        if (workOrderId == null || !workOrderId.matches("\\d+")) {
            return null;
        }
        try {
            return feedbackMapper.selectByWorkOrderId(Long.valueOf(workOrderId));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 定位轮次。指定轮次不存在时退回最新一轮而不是返回 null——
     * 审批要能看到内容才有意义，退化成「什么都没有」比退化成「最新一轮」更糟。
     */
    private FeedbackRoundDO findRound(Long feedbackId, Integer roundNo) {
        if (feedbackId == null) {
            return null;
        }
        try {
            List<FeedbackRoundDO> rounds = roundMapper.selectByFeedbackId(feedbackId);
            if (rounds == null || rounds.isEmpty()) {
                return null;
            }
            if (roundNo == null) {
                return rounds.get(rounds.size() - 1);
            }
            return rounds.stream().filter(item -> Objects.equals(item.getRoundNo(), roundNo))
                    .findFirst().orElse(rounds.get(rounds.size() - 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<Long> parseLongs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = JsonUtils.parseArray(json, Long.class);
            return ids == null ? List.of() : ids;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String typeText(String type) {
        return type == null ? null : FeedbackConstants.TYPE_LABEL.getOrDefault(type, type);
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case FeedbackConstants.STATUS_APPROVING -> "审批中";
            case FeedbackConstants.STATUS_APPROVAL_REJECTED -> "审批驳回";
            case FeedbackConstants.STATUS_WAITING -> "待处理";
            case FeedbackConstants.STATUS_IN_PROGRESS -> "处理中";
            case FeedbackConstants.STATUS_COMPLETED -> "已完成";
            default -> status;
        };
    }

    private static String roundStatusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case FeedbackConstants.STATUS_APPROVING -> "审批中";
            case FeedbackConstants.STATUS_APPROVAL_REJECTED -> "已驳回";
            default -> status;
        };
    }

    /** 轮次展示文本：优先用 round 上的轮次号，退回归属单据上的当前轮次。 */
    private static String roundText(FeedbackRoundDO round, Integer fallbackRoundNo) {
        Integer roundNo = round != null && round.getRoundNo() != null ? round.getRoundNo() : fallbackRoundNo;
        return roundNo == null ? null : "第 " + roundNo + " 轮";
    }

    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(fields));
        return group;
    }

    private static BpmApprovalDetailVO.Group wideGroup(String title, String value) {
        BpmApprovalDetailVO.Group group = group(title, List.of(BpmApprovalFieldVO.of(title, value)));
        group.setSpan(true);
        return group;
    }
}

package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops.MediaStudentTalkRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentops.MediaStudentTalkRecordMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentops.GraduationApplicationMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountService;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_STUDENT_INVALID;

@Service
public class MediaStudentService {
    @Resource private MyStudentService myStudentService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private PositioningCardMapper positioningMapper;
    @Resource private ContentMapper contentMapper;
    @Resource private ProductionTicketMapper ticketMapper;
    @Resource private MediaAccountService accountService;
    @Resource private ContentService contentService;
    @Resource private PositioningCardService positioningService;
    @Resource private MediaStudentTalkRecordMapper talkRecordMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private GraduationApplicationMapper graduationMapper;
    @Resource private PermissionApi permissionApi;

    public MediaStudentDetailRespVO getDetail(Long userId, Long personId) {
        MediaStudentDetailRespVO result = new MediaStudentDetailRespVO();
        result.setStudent(myStudentService.getMediaStudent(userId, personId));
        boolean serviceParticipant = result.getStudent().getServices().stream().anyMatch(service ->
                userId.equals(service.getContentDirectorUserId()) || userId.equals(service.getOperatorUserId()));
        List<MediaAccountDO> accounts = serviceParticipant
                ? accountMapper.selectByStudent(personId)
                : accountMapper.selectByParticipantAndStudent(userId, personId);
        List<Long> accountIds = accounts.stream().map(MediaAccountDO::getId).toList();
        Map<Long, MediaAccountDO> accountById = accounts.stream()
                .collect(java.util.stream.Collectors.toMap(MediaAccountDO::getId, row -> row));
        var positioningCards = positioningMapper.selectByStudentAndAccountIds(personId, accountIds);
        Map<Long, cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO> latestCardByAccount
                = new java.util.LinkedHashMap<>();
        positioningCards.forEach(card -> latestCardByAccount.putIfAbsent(card.getAccountId(), card));
        result.setAccounts(accounts.stream().map(account -> {
            MediaStudentDetailRespVO.AccountVO row = BeanUtils.toBean(account, MediaStudentDetailRespVO.AccountVO.class);
            row.setPlatformLabel(account.getPlatformLabelSnapshot());
            row.setStage(account.getSStage());
            var accountDetail = accountService.projectStudentReadOnly(account);
            // Keep compatibility with callers that mock the legacy detail projection;
            // production uses the non-authorizing read-only projection above.
            if (accountDetail == null) accountDetail = accountService.get(account.getId(), userId);
            row.setAvailableActions(accountDetail == null || accountDetail.getAvailableActions() == null
                    ? List.of() : accountDetail.getAvailableActions());
            row.setDetailSnapshots(accountDetail == null || accountDetail.getDetailSnapshots() == null
                    ? List.of() : accountDetail.getDetailSnapshots());
            var latestCard = latestCardByAccount.get(account.getId());
            row.setTaskLine(buildAccountTaskLine(latestCard == null ? null : latestCard.getStatus()));
            row.setLastActivityAt(account.getUpdateTime());
            return row;
        }).toList());
        result.setPositioningCards(positioningCards.stream()
                .map(row -> {
                    MediaStudentDetailRespVO.PositioningVO value = BeanUtils.toBean(row, MediaStudentDetailRespVO.PositioningVO.class);
                    boolean positioningReadAuthorized = permissionApi.hasAnyPermissions(userId,
                            "zsjos:positioning-card:query-all")
                            || userId.equals(row.getDirectorUserId())
                            || userId.equals(row.getOperatorReviewedByUserId())
                            || accountById.get(row.getAccountId()) != null
                            && userId.equals(accountById.get(row.getAccountId()).getOwnerOperatorUserId());
                    value.setAvailableActions(positioningService.availableActionsForVisible(row, userId,
                            positioningReadAuthorized));
                    value.setLastActivityAt(row.getUpdateTime());
                    return value;
                }).toList());
        var contents = contentMapper.selectByAccountIds(accountIds);
        result.setContents(contents.stream()
                .map(row -> {
                    MediaStudentDetailRespVO.ContentVO value = BeanUtils.toBean(row, MediaStudentDetailRespVO.ContentVO.class);
                    MediaAccountDO account = accountById.get(row.getAccountId());
                    boolean objectAuthorized = permissionApi.hasAnyPermissions(userId, "zsjos:content:query-all")
                            || userId.equals(row.getOwnerOperatorUserId())
                            || userId.equals(row.getFilmingEditorUserId())
                            || account != null && (userId.equals(account.getDirectorUserId())
                            || userId.equals(account.getOwnerOperatorUserId()));
                    value.setAvailableActions(contentService.availableActionsForVisible(row, userId, objectAuthorized));
                    value.setLastActivityAt(row.getUpdateTime());
                    return value;
                }).toList());
        var tickets = ticketMapper.selectByAccountIds(accountIds);
        result.setProductionTickets(tickets.stream().map(row -> {
            MediaStudentDetailRespVO.TicketVO value = BeanUtils.toBean(row, MediaStudentDetailRespVO.TicketVO.class);
            value.setLastActivityAt(row.getUpdateTime());
            return value;
        }).toList());
        var graduations = graduationMapper.selectByStudent(personId);
        var talks = talkRecordMapper.selectRecentByStudent(personId);
        result.setOperationTimeline(buildOperationTimeline(
                accountMapper.selectRecentByParticipantAndStudent(userId, personId),
                positioningMapper.selectRecentByStudentAndAccountIds(personId, accountIds),
                contentMapper.selectRecentByAccountIds(accountIds), ticketMapper.selectRecentByAccountIds(accountIds),
                graduations, talks));
        result.setStudentTaskLine(buildStudentTaskLine(result.getStudent()));
        result.setTaskLine(result.getStudentTaskLine());
        MediaStudentDetailRespVO.PendingStatsVO pending = new MediaStudentDetailRespVO.PendingStatsVO();
        pending.setAccountCount((int) result.getAccounts().stream()
                .filter(row -> row.getAvailableActions() != null && !row.getAvailableActions().isEmpty()).count());
        pending.setPositioningCount((int) result.getPositioningCards().stream()
                .filter(row -> row.getAvailableActions() != null && !row.getAvailableActions().isEmpty()).count());
        pending.setContentCount((int) result.getContents().stream()
                .filter(row -> row.getAvailableActions() != null && !row.getAvailableActions().isEmpty()).count());
        pending.setProductionCount((int) tickets.stream().filter(row -> !"completed".equals(row.getStatus())).count());
        result.setPendingStats(pending);
        return result;
    }

    private List<MediaStudentDetailRespVO.OperationVO> buildOperationTimeline(
            List<MediaAccountDO> accounts,
            List<cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO> positioningCards,
            List<cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO> contents,
            List<cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO> tickets,
            List<cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops.GraduationApplicationDO> graduations,
            List<MediaStudentTalkRecordDO> talks) {
        Set<Long> operatorIds = new java.util.HashSet<>();
        accounts.forEach(row -> { operatorIds.add(row.getDirectorUserId()); operatorIds.add(row.getOwnerOperatorUserId()); });
        positioningCards.forEach(row -> { operatorIds.add(row.getDirectorUserId()); operatorIds.add(row.getOperatorReviewedByUserId()); });
        contents.forEach(row -> { operatorIds.add(row.getOwnerOperatorUserId()); operatorIds.add(row.getFilmingEditorUserId()); });
        tickets.forEach(row -> { operatorIds.add(row.getOwnerOperatorUserId()); operatorIds.add(row.getAssigneeFilmingEditorUserId()); operatorIds.add(row.getReviewerUserId()); });
        graduations.forEach(row -> { operatorIds.add(row.getDirectorUserId()); operatorIds.add(row.getOperatorUserId()); operatorIds.add(row.getReviewerUserId()); });
        talks.forEach(row -> operatorIds.add(row.getOperatorUserId()));
        accounts.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        positioningCards.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        contents.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        tickets.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        graduations.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        talks.forEach(row -> operatorIds.add(parseUserId(row.getUpdater())));
        operatorIds.remove(null);
        Map<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO> users = operatorIds.isEmpty()
                ? Map.of() : adminUserApi.getUserMap(operatorIds);
        List<MediaStudentDetailRespVO.OperationVO> timeline = new ArrayList<>();
        accounts.forEach(row -> timeline.add(operation("account-" + row.getId(), "account", "第三方账号更新",
                row.getNickname() == null ? row.getAccountNo() : row.getNickname(),
                userName(users, row.getUpdater(), row.getDirectorUserId()), row.getUpdateTime())));
        positioningCards.forEach(row -> timeline.add(operation("positioning-" + row.getId(), "positioning", "账号定位更新",
                row.getCardNo() + " · " + row.getStatus(),
                userName(users, row.getUpdater(), row.getDirectorUserId()), row.getUpdateTime())));
        contents.forEach(row -> timeline.add(operation("content-" + row.getId(), "content", "内容生产更新",
                (row.getTitle() == null ? row.getContentNo() : row.getTitle()) + " · " + row.getStatus(),
                userName(users, row.getUpdater(), row.getOwnerOperatorUserId()), row.getUpdateTime())));
        tickets.forEach(row -> timeline.add(operation("ticket-" + row.getId(), "production", "拍剪工单更新",
                row.getTicketNo() + " · " + row.getStatus(),
                userName(users, row.getUpdater(), row.getAssigneeFilmingEditorUserId()), row.getUpdateTime())));
        graduations.forEach(row -> timeline.add(operation("graduation-" + row.getId(), "graduation", "结业流程更新",
                row.getApplicationNo() + " · " + row.getStatus(),
                userName(users, row.getUpdater(), row.getReviewerUserId()), row.getUpdateTime())));
        talks.forEach(row -> timeline.add(operation("talk-" + row.getId(), "talk", "交谈记录",
                row.getContent(), userName(users, row.getUpdater(), row.getOperatorUserId()), row.getOccurredAt())));
        return timeline.stream().filter(row -> row.getOccurredAt() != null)
                .sorted(Comparator.comparing(MediaStudentDetailRespVO.OperationVO::getOccurredAt).reversed()
                        .thenComparing(MediaStudentDetailRespVO.OperationVO::getKey, Comparator.reverseOrder()))
                .limit(20).toList();
    }

    private List<MediaStudentDetailRespVO.TaskStageVO> buildStudentTaskLine(MyStudentRespVO student) {
        MyStudentRespVO.ServiceVO service = student.getServices().isEmpty() ? null : student.getServices().get(0);
        String directorStage = service == null || service.getDirectorStage() == null ? "precheck" : service.getDirectorStage();
        boolean interviewStarted = !"precheck".equals(directorStage);
        return List.of(
                taskStage("precheck", "资料预审", stageStatus(interviewStarted, true), interviewStarted ? "资料预审已提交" : "待编导审核资料并预约访谈"),
                taskStage("interview", "学员采访", stageStatus("positioning_ready".equals(directorStage), interviewStarted), "采集学员级基础信息")
        );
    }

    private List<MediaStudentDetailRespVO.TaskStageVO> buildAccountTaskLine(String positioningStatus) {
        boolean positioningStarted = positioningStatus != null;
        boolean operatorConfirmed = positioningStatus != null && Set.of("student_confirm", "trial_14d", "confirmed", "archived").contains(positioningStatus);
        boolean studentConfirmed = positioningStatus != null && Set.of("trial_14d", "confirmed", "archived").contains(positioningStatus);
        boolean trialDone = positioningStatus != null && Set.of("confirmed", "archived").contains(positioningStatus);
        return List.of(
                taskStage("positioning", "账号定位", stageStatus(operatorConfirmed, positioningStarted), "各账号独立填写定位卡"),
                taskStage("operator_confirm", "运营确认", stageStatus(operatorConfirmed, "operator_feasibility".equals(positioningStatus)), "运营逐账号确认"),
                taskStage("student_confirm", "学员确认", stageStatus(studentConfirmed, "student_confirm".equals(positioningStatus)), "学员通过安全链接确认"),
                taskStage("trial", "试运行", stageStatus(trialDone, "trial_14d".equals(positioningStatus)), "各账号独立试运行"),
                taskStage("formal", "正式定位", stageStatus("archived".equals(positioningStatus), "confirmed".equals(positioningStatus)), "正式定位完成后解锁内容生产")
        );
    }

    private static String stageStatus(boolean done, boolean started) {
        return done ? "done" : started ? "current" : "pending";
    }

    private static MediaStudentDetailRespVO.TaskStageVO taskStage(String key, String label, String status, String detail) {
        MediaStudentDetailRespVO.TaskStageVO row = new MediaStudentDetailRespVO.TaskStageVO();
        row.setKey(key); row.setLabel(label); row.setStatus(status); row.setDetail(detail); return row;
    }

    private static MediaStudentDetailRespVO.OperationVO operation(String key, String type, String title,
                                                                   String detail, String operatorName,
                                                                   java.time.LocalDateTime occurredAt) {
        MediaStudentDetailRespVO.OperationVO row = new MediaStudentDetailRespVO.OperationVO();
        row.setKey(key); row.setType(type); row.setTitle(title); row.setDetail(detail);
        row.setOperatorName(operatorName); row.setOccurredAt(occurredAt); return row;
    }

    private static String userName(Map<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO> users,
                                   String updater, Long fallbackUserId) {
        Long userId = parseUserId(updater);
        if (userId == null) userId = fallbackUserId;
        var user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getNickname();
    }

    private static Long parseUserId(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    public MediaStudentTargetRespVO resolveTarget(Long userId, String bizType, Long bizId) {
        MediaAccountDO account;
        String tab;
        if ("media-account".equals(bizType)) {
            accountService.get(bizId, userId);
            account = accountMapper.selectById(bizId);
            tab = "accounts";
        } else if ("content".equals(bizType)) {
            var content = contentService.get(bizId, userId);
            account = accountMapper.selectById(content.getAccountId());
            tab = "content";
        } else if ("positioning-card".equals(bizType)) {
            var card = positioningService.get(bizId, userId);
            account = accountMapper.selectById(card.getAccountId());
            tab = "positioning";
        } else {
            throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        }
        if (account == null || account.getStudentPersonId() == null) throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        getDetail(userId, account.getStudentPersonId());
        return new MediaStudentTargetRespVO(account.getStudentPersonId(), tab, bizId);
    }

    public List<MediaStudentTalkRespVO> getTalkRecords(Long userId, Long personId) {
        myStudentService.getMediaStudent(userId, personId);
        List<MediaStudentTalkRecordDO> records = talkRecordMapper.selectByStudent(personId);
        var users = adminUserApi.getUserMap(records.stream().map(MediaStudentTalkRecordDO::getOperatorUserId).collect(java.util.stream.Collectors.toSet()));
        return records.stream().map(record -> {
            MediaStudentTalkRespVO response = new MediaStudentTalkRespVO();
            response.setId(record.getId()); response.setAccountId(record.getAccountId());
            response.setOperatorUserId(record.getOperatorUserId()); response.setContent(record.getContent());
            response.setOccurredAt(record.getOccurredAt());
            response.setAttachmentFileIds(record.getAttachmentFileIdsJson() == null ? List.of()
                    : JsonUtils.parseArray(record.getAttachmentFileIdsJson(), Long.class));
            var operator = users.get(record.getOperatorUserId());
            response.setOperatorUserName(operator == null ? null : operator.getNickname());
            return response;
        }).toList();
    }

    public Long createTalkRecord(Long userId, Long personId, MediaStudentTalkSaveReqVO request) {
        myStudentService.getMediaStudent(userId, personId);
        if (request.getAccountId() != null && accountMapper.selectByParticipantAndStudent(userId, personId).stream()
                .noneMatch(account -> account.getId().equals(request.getAccountId()))) {
            throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        }
        MediaStudentTalkRecordDO record = new MediaStudentTalkRecordDO();
        record.setStudentPersonId(personId); record.setAccountId(request.getAccountId());
        record.setOperatorUserId(userId); record.setContent(request.getContent().trim());
        record.setAttachmentFileIdsJson(JsonUtils.toJsonString(request.getAttachmentFileIds() == null
                ? List.of() : request.getAttachmentFileIds()));
        record.setOccurredAt(java.time.LocalDateTime.now()); talkRecordMapper.insert(record);
        return record.getId();
    }
}

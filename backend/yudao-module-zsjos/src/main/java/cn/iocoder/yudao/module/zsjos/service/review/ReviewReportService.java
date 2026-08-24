package cn.iocoder.yudao.module.zsjos.service.review;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.review.vo.ReviewReportRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.review.vo.ReviewReportSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.review.ReviewReportDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.review.ReviewReportMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ReviewReportService {
    @Resource private ReviewReportMapper mapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private MediaWorkflowEventService events;

    public List<ReviewReportRespVO> list(Long userId) {
        return mapper.selectByParticipant(userId).stream().map(row -> resp(row, userId)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ReviewReportSaveReqVO req, Long userId) {
        ReviewReportDO row = BeanUtils.toBean(req, ReviewReportDO.class);
        row.setReviewNo("RV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        row.setAuthorUserId(userId); row.setStatus("draft"); row.setVersion(0); mapper.insert(row);
        return row.getId();
    }

    @ZsjosPermission(bizType = "media-review", bizId = "#id", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version, Long userId) {
        ReviewReportDO row = require(id);
        if (!Objects.equals(row.getAuthorUserId(), userId) || !List.of("draft", "rejected").contains(row.getStatus())) throw exception(MEDIA_REVIEW_STATE_INVALID);
        Long reviewer = requireSupervisor(userId);
        if (mapper.transition(id, version, row.getStatus(), "submitted") == 0) throw exception(MEDIA_REVIEW_VERSION_CONFLICT);
        ReviewReportDO update = new ReviewReportDO(); update.setId(id); update.setReviewerUserId(reviewer); update.setSubmittedAt(LocalDateTime.now()); update.setRejectReason(null); mapper.updateById(update);
        events.transition("media-review", id, userId, row.getStatus(), "submitted", null, "media-review:" + id + ":" + version + ":submitted");
        events.createTaskAndNotify("media.review.pending", "MEDIA_REVIEW_APPROVAL", "media-review", id, reviewer, "复盘报告待审核", "review", userId, "media-review-approval:" + id + ":" + version, payload(row));
    }

    @ZsjosPermission(bizType = "media-review", bizId = "#id", action = "approve")
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version, Long userId) {
        ReviewReportDO row = require(id);
        if (!"submitted".equals(row.getStatus()) || !Objects.equals(row.getReviewerUserId(), userId)) throw exception(MEDIA_REVIEW_STATE_INVALID);
        if (mapper.transition(id, version, "submitted", "approved") == 0) throw exception(MEDIA_REVIEW_VERSION_CONFLICT);
        ReviewReportDO update = new ReviewReportDO(); update.setId(id); update.setReviewedAt(LocalDateTime.now()); mapper.updateById(update);
        events.transition("media-review", id, userId, "submitted", "approved", null, "media-review:" + id + ":" + version + ":approved");
        events.completeTask("MEDIA_REVIEW_APPROVAL", id, userId);
        events.notify("media.review.approved", "media-review", id, row.getAuthorUserId(), userId, "media-review-result:" + id + ":" + version + ":approved", payload(row));
    }

    @ZsjosPermission(bizType = "media-review", bizId = "#id", action = "reject")
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Integer version, String reason, Long userId) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw exception(MEDIA_REVIEW_REASON_REQUIRED);
        ReviewReportDO row = require(id);
        if (!"submitted".equals(row.getStatus()) || !Objects.equals(row.getReviewerUserId(), userId)) throw exception(MEDIA_REVIEW_STATE_INVALID);
        if (mapper.transition(id, version, "submitted", "rejected") == 0) throw exception(MEDIA_REVIEW_VERSION_CONFLICT);
        ReviewReportDO update = new ReviewReportDO(); update.setId(id); update.setReviewedAt(LocalDateTime.now()); update.setRejectReason(reason.trim()); mapper.updateById(update);
        events.transition("media-review", id, userId, "submitted", "rejected", reason, "media-review:" + id + ":" + version + ":rejected");
        events.completeTask("MEDIA_REVIEW_APPROVAL", id, userId);
        Map<String, Object> values = new LinkedHashMap<>(payload(row)); values.put("reason", reason.trim());
        events.notify("media.review.rejected", "media-review", id, row.getAuthorUserId(), userId, "media-review-result:" + id + ":" + version + ":rejected", values);
    }

    @ZsjosPermission(bizType = "media-review", bizId = "#id", action = "archive")
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id, Integer version, Long userId) {
        ReviewReportDO row = require(id);
        if (!"approved".equals(row.getStatus()) || !Objects.equals(row.getAuthorUserId(), userId) && !Objects.equals(row.getReviewerUserId(), userId)) throw exception(MEDIA_REVIEW_STATE_INVALID);
        if (mapper.transition(id, version, "approved", "archived") == 0) throw exception(MEDIA_REVIEW_VERSION_CONFLICT);
        ReviewReportDO update = new ReviewReportDO(); update.setId(id); update.setArchivedAt(LocalDateTime.now()); mapper.updateById(update);
        events.transition("media-review", id, userId, "approved", "archived", null, "media-review:" + id + ":" + version + ":archived");
    }

    private Long requireSupervisor(Long userId) {
        var user = adminUserApi.getUser(userId); var dept = user == null || user.getDeptId() == null ? null : deptApi.getDept(user.getDeptId());
        Long leader = dept == null ? null : dept.getLeaderUserId(); var supervisor = leader == null ? null : adminUserApi.getUser(leader);
        if (supervisor == null || Objects.equals(userId, leader) || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())) throw exception(MEDIA_REVIEW_SUPERVISOR_INVALID);
        return leader;
    }
    private ReviewReportDO require(Long id) { ReviewReportDO row = mapper.selectById(id); if (row == null) throw exception(MEDIA_REVIEW_NOT_EXISTS); return row; }
    private Map<String, Object> payload(ReviewReportDO row) { return Map.of("bizNo", row.getReviewNo(), "deepLink", "/zsjos/reviews?reviewId=" + row.getId()); }
    private ReviewReportRespVO resp(ReviewReportDO row, Long userId) {
        ReviewReportRespVO response = BeanUtils.toBean(row, ReviewReportRespVO.class);
        if (List.of("draft", "rejected").contains(row.getStatus()) && Objects.equals(row.getAuthorUserId(), userId)) response.setAvailableActions(List.of("submit"));
        else if ("submitted".equals(row.getStatus()) && Objects.equals(row.getReviewerUserId(), userId)) response.setAvailableActions(List.of("approve", "reject"));
        else if ("approved".equals(row.getStatus()) && (Objects.equals(row.getAuthorUserId(), userId) || Objects.equals(row.getReviewerUserId(), userId))) response.setAvailableActions(List.of("archive"));
        else response.setAvailableActions(List.of());
        return response;
    }
}

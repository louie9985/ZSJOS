package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ContentVersionService {
    @Resource private ContentVersionMapper mapper;
    @Resource private ContentService contentService;

    public List<ContentVersionRespVO> list(Long contentId, Long userId) {
        contentService.get(contentId, userId);
        return mapper.selectByContentId(contentId).stream()
                .map(row -> BeanUtils.toBean(row, ContentVersionRespVO.class)).toList();
    }

    @ZsjosPermission(bizType = "content", bizId = "#req.contentId", action = "version-create")
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContentVersionSaveReqVO req, Long userId) {
        ContentDO content = contentService.require(req.getContentId());
        ContentVersionDO replay = req.getIdempotencyKey() == null ? null : mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getContentId, req.getContentId())
                .eq(ContentVersionDO::getIdempotencyKey, req.getIdempotencyKey()));
        if (replay != null) return replay.getId();
        ContentVersionDO row = new ContentVersionDO();
        row.setContentId(content.getId());
        int nextVersion = content.getCurrentVersionNo() == null ? 1 : content.getCurrentVersionNo() + 1;
        row.setVersionNo(nextVersion);
        row.setStage(req.getStage());
        row.setMaterialRefsJson(req.getMaterialRefsJson());
        row.setDeliverableUrl(req.getDeliverableUrl());
        row.setScriptText(req.getScriptText());
        row.setSubmittedByUserId(userId);
        row.setSubmittedAt(LocalDateTime.now());
        row.setIdempotencyKey(req.getIdempotencyKey());
        mapper.insert(row);
        if (contentService.advanceCurrentVersion(content.getId(), content.getVersion(), nextVersion) == 0) {
            throw exception(CONTENT_VERSION_STAGE_INVALID);
        }
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, boolean approved, String comment, Long userId) {
        ContentVersionDO row = mapper.selectById(id);
        if (row == null) throw exception(CONTENT_VERSION_NOT_EXISTS);
        row.setReviewDecision(approved ? "approved" : "rejected");
        row.setReviewComment(comment);
        row.setReviewedByUserId(userId);
        row.setReviewedAt(LocalDateTime.now());
        mapper.updateById(row);
    }
}

package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FeedbackMapper extends BaseMapperX<FeedbackDO> {

    default FeedbackDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapperX<FeedbackDO>().eq(FeedbackDO::getId, id));
    }

    default FeedbackDO selectByProcessInstanceId(String id) {
        return selectOne(FeedbackDO::getProcessInstanceId, id);
    }

    default FeedbackDO selectByWorkOrderId(Long id) {
        return selectOne(FeedbackDO::getWorkOrderId, id);
    }

    default List<FeedbackDO> selectRecentBySubmitter(Long userId, int limit) {
        return selectRecentBySubmitter(FeedbackConstants.SUBJECT_ADMIN, userId, limit);
    }

    default List<FeedbackDO> selectRecentBySubmitter(String subjectType, Long userId, int limit) {
        return selectList(new LambdaQueryWrapperX<FeedbackDO>()
                .eq(FeedbackDO::getSubmitterSubjectType, subjectType)
                .eq(FeedbackDO::getSubmitterUserId, userId)
                .orderByDesc(FeedbackDO::getLastActivityAt)
                .orderByDesc(FeedbackDO::getId)
                .last("LIMIT " + limit));
    }

    default PageResult<FeedbackDO> selectMyPage(FeedbackPageReqVO req, Long userId) {
        return selectMyPage(req, FeedbackConstants.SUBJECT_ADMIN, userId);
    }

    default PageResult<FeedbackDO> selectMyPage(FeedbackPageReqVO req, String subjectType, Long userId) {
        LambdaQueryWrapperX<FeedbackDO> query = new LambdaQueryWrapperX<FeedbackDO>()
                .eq(FeedbackDO::getSubmitterSubjectType, subjectType)
                .eq(FeedbackDO::getSubmitterUserId, userId)
                .eqIfPresent(FeedbackDO::getFeedbackType, req.getFeedbackType())
                .eqIfPresent(FeedbackDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword().trim();
            query.and(wrapper -> wrapper.like(FeedbackDO::getFeedbackNo, keyword)
                    .or().like(FeedbackDO::getTitle, keyword));
        }
        return selectPage(req, query.orderByDesc(FeedbackDO::getLastActivityAt)
                .orderByDesc(FeedbackDO::getId));
    }

    default PageResult<FeedbackDO> selectPartnerPage(FeedbackPageReqVO req, Long accountId, Long partnerId) {
        LambdaQueryWrapperX<FeedbackDO> query = new LambdaQueryWrapperX<FeedbackDO>()
                .eq(FeedbackDO::getSubmitterSubjectType, FeedbackConstants.SUBJECT_PARTNER_ACCOUNT)
                .eq(FeedbackDO::getSubmitterUserId, accountId)
                .eq(FeedbackDO::getPartnerId, partnerId)
                .eqIfPresent(FeedbackDO::getFeedbackType, req.getFeedbackType())
                .eqIfPresent(FeedbackDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword().trim();
            query.and(wrapper -> wrapper.like(FeedbackDO::getFeedbackNo, keyword)
                    .or().like(FeedbackDO::getTitle, keyword));
        }
        return selectPage(req, query.orderByDesc(FeedbackDO::getLastActivityAt)
                .orderByDesc(FeedbackDO::getId));
    }

    default PageResult<FeedbackDO> selectAdminPage(FeedbackPageReqVO req, String type) {
        LambdaQueryWrapperX<FeedbackDO> query = new LambdaQueryWrapperX<FeedbackDO>()
                .eq(FeedbackDO::getFeedbackType, type)
                .eqIfPresent(FeedbackDO::getStatus, req.getStatus())
                .eqIfPresent(FeedbackDO::getAssigneeUserId, req.getAssigneeUserId())
                .betweenIfPresent(FeedbackDO::getCreateTime, req.getCreateTime());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(wrapper -> wrapper.like(FeedbackDO::getFeedbackNo, req.getKeyword().trim())
                    .or().like(FeedbackDO::getTitle, req.getKeyword().trim()));
        }
        return selectPage(req, query.orderByDesc(FeedbackDO::getLastActivityAt)
                .orderByDesc(FeedbackDO::getId));
    }
}

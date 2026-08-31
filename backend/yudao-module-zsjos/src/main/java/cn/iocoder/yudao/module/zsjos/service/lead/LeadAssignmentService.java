package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationSaveReqVO;

import java.util.List;

public interface LeadAssignmentService {

    PageResult<LeadAssignmentRelationRespVO> getRelationPage(LeadAssignmentRelationPageReqVO reqVO,
                                                              Long operatorUserId);

    List<LeadAssignmentUserRespVO> getEligibleSalesUsers();

    List<LeadAssignmentUserRespVO> getAssignableSalesUsers(Long sourceUserId);

    void saveRelations(LeadAssignmentSaveReqVO reqVO, Long operatorUserId);

    PageResult<LeadAssignmentLogRespVO> getLogPage(LeadAssignmentLogPageReqVO reqVO,
                                                   Long operatorUserId);

    PageResult<UserRelationRespVO> getAdminRelationPage(UserRelationPageReqVO reqVO);

    List<LeadAssignmentUserRespVO> getAdminEligibleTargetUsers(String sceneCode);

    List<LeadAssignmentUserRespVO> getConfiguredTargetUsers(String sceneCode, Long sourceUserId);

    void saveAdminRelations(UserRelationSaveReqVO reqVO, Long operatorUserId);

    PageResult<LeadAssignmentLogRespVO> getAdminLogPage(LeadAssignmentLogPageReqVO reqVO);

}

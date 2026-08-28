package cn.iocoder.yudao.module.zsjos.service.workorder;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
public interface WorkOrderService {
    WorkOrderFileRespVO upload(byte[] content, String name, String contentType, Long userId);
    Long createScene(WorkOrderSceneCreateReqVO req, Long userId);
    void updateScene(WorkOrderSceneUpdateReqVO req, Long userId);
    PageResult<WorkOrderSceneRespVO> scenePage(int pageNo, int pageSize);
    WorkOrderSceneRespVO getScene(String code);
    WorkOrderScenePublishValidationRespVO validateScenePublish(Long id);
    void publishScene(WorkOrderScenePublishReqVO req, Long userId);
    void disableScene(Long id, Integer version, Long userId);
    java.util.List<WorkOrderSceneRespVO> sceneVersions(Long id);
    PageResult<WorkOrderSceneRespVO> catalog(int pageNo, int pageSize, Long userId);
    PageResult<WorkOrderCandidateRespVO> candidatePage(WorkOrderCandidatePageReqVO req, Long userId);
    PageResult<WorkOrderCandidateRespVO> candidateDepartmentPage(WorkOrderCandidatePageReqVO req, Long userId);
    Long create(WorkOrderCreateReqVO req, Long userId);
    void take(Long id, WorkOrderActionReqVO req, Long userId);
    void claim(Long id, WorkOrderActionReqVO req, Long userId);
    void complete(Long id, WorkOrderActionReqVO req, Long userId);
    void accept(Long id, WorkOrderActionReqVO req, Long userId);
    void returnForRework(Long id, WorkOrderActionReqVO req, Long userId);
    void reject(Long id, WorkOrderActionReqVO req, Long userId);
    void withdraw(Long id, WorkOrderActionReqVO req, Long userId);
    void terminate(Long id, WorkOrderActionReqVO req, Long userId);
    PageResult<WorkOrderRespVO> myPage(String status, String view, int pageNo, int pageSize, Long userId);
    PageResult<WorkOrderRespVO> pool(String sceneCode, int pageNo, int pageSize, Long userId);
    WorkOrderRespVO get(Long id, Long userId);
    PageResult<WorkOrderRespVO> auditPage(String status, int pageNo, int pageSize);
    WorkOrderRespVO auditGet(Long id);
    Long createProductionEnvelope(String sceneCode, Long businessId, Long accountId, Long sourceUserId,
                                  Long targetUserId, Long targetDeptId, String remark, java.util.Map<String, Object> values,
                                  java.util.List<Long> attachmentIds, String idempotencyKey);
    void validateProductionPoolCandidate(Long businessId, Long userId);
    String rejectProductionAssignment(Long businessId, Long userId, String reason, String idempotencyKey);
    void syncProductionStatus(Long businessId, String businessStatus, Long targetUserId, Long operatorUserId,
                              String reason, String idempotencyKey);
    boolean isProductionTemplate(String sceneCode, Long userId);
    Long getProductionEnvelopeId(Long businessId);
}

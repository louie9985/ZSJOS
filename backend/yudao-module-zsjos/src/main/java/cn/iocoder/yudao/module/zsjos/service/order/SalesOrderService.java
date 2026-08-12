package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface SalesOrderService {
    Long createAndSubmit(Long leadId, Long userId, SalesOrderSubmitReqVO reqVO);
    void reviseAndResubmit(Long orderId, Long userId, SalesOrderSubmitReqVO reqVO);
    Long continueAndSubmit(Long orderId, Long userId, SalesOrderSubmitReqVO reqVO);
    SalesOrderRespVO get(Long orderId, Long userId);
    SalesOrderRespVO getOwn(Long orderId, Long userId);
    PageResult<SalesOrderListItemRespVO> getMyPage(SalesOrderMyPageReqVO reqVO, Long userId);
    SalesOrderStatusCountsRespVO getMyStatusCounts(Long userId);
    PageResult<SalesOrderListItemRespVO> getInboxPage(SalesOrderPageReqVO reqVO, Long userId);
    SalesOrderApprovalFilterProfileRespVO getApprovalFilterProfile(Long userId);
    void approve(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO);
    void reject(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO);
    LeadAttachmentUploadRespVO uploadVoucher(Long userId, MultipartFile file) throws IOException;
    void handleProcessResult(String processInstanceId, Integer status, String reason);
}

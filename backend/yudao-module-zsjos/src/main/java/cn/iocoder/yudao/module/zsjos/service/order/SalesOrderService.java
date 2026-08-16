package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;

public interface SalesOrderService {
    Long createAndSubmit(Long leadId, Long userId, SalesOrderSubmitReqVO reqVO);
    Long createSystemRepurchase(Long leadId, Long userId, SalesOrderRepurchaseReqVO reqVO);
    Long createExternalRepurchase(Long userId, SalesOrderRepurchaseReqVO reqVO);
    void reviseAndResubmit(Long orderId, Long userId, SalesOrderSubmitReqVO reqVO);
    SalesOrderRespVO get(Long orderId, Long userId);
    SalesOrderRespVO getOwn(Long orderId, Long userId);
    PageResult<SalesOrderListItemRespVO> getMyPage(SalesOrderMyPageReqVO reqVO, Long userId);
    CursorPageResult<SalesOrderListItemRespVO> getMyCursorPage(SalesOrderMyCursorReqVO reqVO, Long userId);
    PageResult<FinanceOrderExportRowRespVO> getFinanceExportPage(FinanceOrderExportReqVO reqVO, Long userId);
    SalesOrderStatusCountsRespVO getMyStatusCounts(Long userId);
    PageResult<SalesOrderListItemRespVO> getInboxPage(SalesOrderPageReqVO reqVO, Long userId);
    CursorPageResult<SalesOrderListItemRespVO> getInboxCursor(SalesOrderPageReqVO reqVO, Long userId);
    SalesOrderApprovalFilterProfileRespVO getApprovalFilterProfile(Long userId);
    void approve(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO);
    void reject(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO);
    void terminate(Long orderId, Long userId, SalesOrderTerminateReqVO reqVO);
    java.util.List<SalesOrderListItemRespVO> getCustomerOrders(Long leadId, Long userId);
    SalesOrderRespVO getCustomerOrder(Long leadId, Long orderId, Long userId);
    LeadAttachmentUploadRespVO uploadVoucher(Long userId, MultipartFile file) throws IOException;
    void handleProcessResult(String processInstanceId, Integer status, String reason);
}

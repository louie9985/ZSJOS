package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;

public interface DeliveryClassService {
    String BIZ_TYPE = "delivery-class";
    String PERMISSION_QUERY_MANAGED = "zsjos:delivery-class:query-managed";
    String PERMISSION_QUERY_MY = "zsjos:delivery-class:query-my";
    PageResult<DeliveryClassRespVO> getManagedPage(Long userId, DeliveryClassPageReqVO req);
    PageResult<DeliveryClassRespVO> getMyPage(Long userId, DeliveryClassPageReqVO req);
    DeliveryClassRespVO get(Long id, Long userId);
    PageResult<DeliveryClassStudentRespVO> students(Long id, Long userId, PageParam pageParam);
    List<DeliveryClassOptionRespVO> options(Long categoryId, boolean includePending);
    List<HomeroomCandidateRespVO> homeroomCandidates(Long userId);
    List<DeliveryClassCategoryOptionRespVO> categoryOptions();
    List<ExamProductScopeRespVO> productOptions();
    List<DeliveryClassExamOptionRespVO> examOptions(Long categoryId, Long productId, String selectedAttrsJson,
                                                    String selectedSkuIdsJson);
    AdminUserRespDTO validateHomeroom(Long userId);
    Long create(DeliveryClassSaveReqVO req, Long userId);
    void update(Long id, DeliveryClassSaveReqVO req, Long userId);
    void complete(Long id, Long userId);
    void directTransfer(Long relationId, DeliveryClassDirectTransferReqVO req, Long userId);
}

package cn.iocoder.yudao.module.zsjos.service.delivery;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliverySubmissionDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliverySubmissionReqVO;
public interface StudentDeliverySubmissionService { StudentDeliverySubmissionDO submit(StudentDeliverySubmissionReqVO req); }

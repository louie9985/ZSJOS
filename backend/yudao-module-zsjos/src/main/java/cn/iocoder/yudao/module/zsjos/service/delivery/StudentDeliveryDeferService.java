package cn.iocoder.yudao.module.zsjos.service.delivery;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryDeferReqVO; import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO;
public interface StudentDeliveryDeferService { StudentDeliveryDeferDO request(StudentDeliveryDeferReqVO req); void handleProcessResult(String processId, Integer status, String reason); }

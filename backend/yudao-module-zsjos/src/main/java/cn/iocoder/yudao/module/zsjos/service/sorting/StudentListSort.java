package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import java.util.List;
import java.util.stream.Collectors;

public final class StudentListSort {
    private StudentListSort() {}
    public static BusinessListSort<MyStudentRespVO> create() {
        return new BusinessListSort<MyStudentRespVO>(MyStudentRespVO::getPersonId)
                .field("name", MyStudentRespVO::getName).field("leadNo", MyStudentRespVO::getLeadNo)
                .field("mobile", MyStudentRespVO::getMobile).field("wechatId", MyStudentRespVO::getWechatId)
                .field("activatedAt", MyStudentRespVO::getActivatedAt)
                .field("className", row -> services(row).stream().map(MyStudentRespVO.ServiceVO::getClassName)
                        .filter(name -> name != null && !name.isBlank()).findFirst().orElse(null))
                .field("courses", row -> services(row).stream().map(s -> BusinessListSort.first(s.getCourseName(), s.getSkuName(), "课程服务")).collect(Collectors.joining("、")))
                .field("serviceStatus", row -> services(row).stream().map(s -> switch (s.getStatus() == null ? "" : s.getStatus()) {
                    case "active" -> "服务中"; case "completed" -> "已完成"; case "cancelled" -> "已取消"; default -> "未知状态";
                }).collect(Collectors.joining("、")))
                .field("orderNos", row -> services(row).stream().map(s -> BusinessListSort.first(s.getOrderNo(), "订单 " + s.getOrderId())).collect(Collectors.joining("、")));
    }
    private static List<MyStudentRespVO.ServiceVO> services(MyStudentRespVO row) { return row.getServices() == null ? List.of() : row.getServices(); }
}

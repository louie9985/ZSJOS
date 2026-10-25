package cn.iocoder.yudao.module.zsjos.controller.admin.studententry.vo;

import jakarta.validation.constraints.*;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassOptionRespVO;

public final class StudentEntryVO {
    private StudentEntryVO() {}
    public record Contact(@Size(max=32) String mobile, @Size(max=64) String wechatId) {}
    public record Create(@NotBlank @Pattern(regexp="media|teaching") String entryType,
            @NotBlank @Size(max=100) String name, @Size(max=32) String mobile, @Size(max=64) String wechatId,
            @Size(max=128) String skuRef, Long directorUserId, boolean teachingDelivery, Long classId,
            Long reusePersonId, boolean confirmReuse, @NotBlank @Size(max=64) String idempotencyKey) {}
    public record User(Long id, String name) {}
    public record Course(String skuRef, String name) {}
    public record Options(List<User> directors, List<DeliveryClassOptionRespVO> classes, List<Course> courses) {}
    public record ExistingService(Long serviceRelationId, String studentName, String status, String statusLabel) {}
    public record Check(String status, Long personId, String personNo, String name,
                        List<ExistingService> services) {}
    public record Result(Long personId, String personNo, Long serviceRelationId, boolean canRead) {}
}

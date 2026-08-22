package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MyStudentRespVO {
    private Long personId;
    private Long leadId;
    private String leadNo;
    private String name;
    private String mobile;
    private String wechatId;
    private LocalDateTime activatedAt;
    private List<ServiceVO> services;

    @Data
    public static class ServiceVO {
        private Long serviceRelationId;
        /** Technical link for loading the Lead detail; never a user-facing identifier. */
        private Long leadId;
        private String leadNo;
        private Long orderId;
        private String orderNo;
        private Long orderItemId;
        private String courseName;
        private String skuName;
        private List<String> categoryPath;
        private List<String> attributeValues;
        private String productSnapshot;
        private String status;
        private LocalDateTime activatedAt;
        private String acceptanceStatus;
        private LocalDateTime acceptedAt;
        private Integer version;
        private Boolean owner;
        private Long contentDirectorUserId;
        private String contentDirectorUserName;
        private Long careerPlannerUserId;
        private String careerPlannerUserName;
    }
}

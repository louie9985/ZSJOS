package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MyStudentRespVO {
    private Long personId;
    private String personNo;
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
        /** Delivery class of this service; null only for legacy rows before class migration. */
        private Long classId;
        private String className;
        private String courseName;
        private String skuName;
        private List<String> categoryPath;
        private List<String> attributeValues;
        private List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> specs;
        private String productSnapshot;
        private String status;
        private LocalDateTime activatedAt;
        private String acceptanceStatus;
        private LocalDateTime acceptedAt;
        private Integer version;
        private Boolean owner;
        private Long ownerUserId;
        private String ownerUserName;
        private Long contentDirectorUserId;
        private String contentDirectorUserName;
        private Long careerPlannerUserId;
        private String careerPlannerUserName;
        private Long operatorUserId;
        private String operatorUserName;
        private String directorStage;
        private LocalDateTime directorInterviewAt;
    }
}

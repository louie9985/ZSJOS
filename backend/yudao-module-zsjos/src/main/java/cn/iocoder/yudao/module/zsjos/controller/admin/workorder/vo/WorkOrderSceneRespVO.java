package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
import lombok.Data;
import java.util.List;
@Data public class WorkOrderSceneRespVO { private Long id; private String code; private String name; private String remark; private String sourcePostCode; private String targetPostCode; private String assignmentMode; private List<WorkOrderFieldDefinition> fields; private Integer status; private Integer version; }

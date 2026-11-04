package cn.iocoder.yudao.module.system.api.ip.dto;

import lombok.Data;
import java.util.List;

/** Address-code history, never evidence of a person's current household location. */
@Data
public class IdentityAreaRespDTO {
    private String code;
    private String status;
    private String label;
    private String dataVersion;
    private List<String> historicalLabels = List.of();
}

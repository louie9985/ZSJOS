package cn.iocoder.yudao.module.pms.service.kb.importing;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
@Data
@Component
@org.springframework.validation.annotation.Validated
@ConfigurationProperties(prefix="pms.knowledge-import")
public class PmsKnowledgeImportPolicy {
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(104857600)
    private long maxFileSize = 100L * 1024 * 1024;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(2147483648L)
    private long maxTotalSize = 2L * 1024 * 1024 * 1024;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000)
    private int maxFiles = 1000;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20)
    private int maxDepth = 20;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(3)
    private int concurrency = 3;
    @jakarta.validation.constraints.NotEmpty
    private List<String> fileTypes = List.of("doc","docx","xls","xlsx","ppt","pptx","txt","pdf","rar","zip","png","jpg","jpeg");
}

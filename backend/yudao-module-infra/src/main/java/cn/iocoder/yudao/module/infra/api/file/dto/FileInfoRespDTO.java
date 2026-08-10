package cn.iocoder.yudao.module.infra.api.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoRespDTO {

    private Long id;
    private Long configId;
    private String name;
    private String path;
    private String url;
    private String type;
    private Long size;
    private String creator;

}

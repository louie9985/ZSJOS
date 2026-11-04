package cn.iocoder.yudao.module.system.service.ip;

import cn.iocoder.yudao.module.system.api.ip.dto.IdentityAreaRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.ip.IdentityAreaDO;
import cn.iocoder.yudao.module.system.dal.mysql.ip.IdentityAreaMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class IdentityAreaService {
    @Resource private IdentityAreaMapper mapper;

    public IdentityAreaRespDTO resolve(String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid address code");
        return resolve(code, mapper.selectList(IdentityAreaDO::getCode, code));
    }

    static IdentityAreaRespDTO resolve(String code, List<IdentityAreaDO> records) {
        var result = new IdentityAreaRespDTO();
        result.setCode(code);
        result.setStatus("UNRECOGNIZED");
        if (records.isEmpty()) return result;
        var versions = records.stream().map(IdentityAreaDO::getDataVersion).distinct().toList();
        var labels = records.stream().map(IdentityAreaService::label).distinct().sorted().toList();
        result.setHistoricalLabels(labels);
        // Neither birth date nor successor relationships identify when or where this person was assigned a number.
        if (versions.size() != 1 || labels.size() != 1) {
            result.setStatus("AMBIGUOUS");
            return result;
        }
        result.setDataVersion(versions.getFirst());
        result.setLabel(labels.getFirst());
        result.setStatus(records.stream().anyMatch(r -> "在用".equals(r.getRecordStatus())) ? "CURRENT" : "HISTORICAL");
        return result;
    }

    private static String label(IdentityAreaDO record) {
        return Stream.of(record.getProvinceName(), record.getCityName(), record.getName())
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct()
                .collect(java.util.stream.Collectors.joining(" / "));
    }
}

package cn.iocoder.yudao.module.infra.framework.file.core.client.db;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileContentDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileContentMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;

import java.util.Comparator;
import java.util.List;

/**
 * 基于 DB 存储的文件客户端的配置类
 *
 * @author 芋道源码
 */
public class DBFileClient extends AbstractFileClient<DBFileClientConfig> {

    private FileContentMapper fileContentMapper;

    public DBFileClient(Long id, DBFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        fileContentMapper = SpringUtil.getBean(FileContentMapper.class);
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        FileContentDO contentDO = new FileContentDO().setConfigId(getId())
                .setPath(path).setContent(content);
        fileContentMapper.insert(contentDO);
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public String uploadFile(java.nio.file.Path source, String path, String type) throws Exception {
        var dataSource = SpringUtil.getBean(javax.sql.DataSource.class);
        var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        String sql = "INSERT INTO infra_file_content(config_id,path,content,creator,updater) VALUES(?,?,?,?,?)";
        try {
            // MySQL client-side prepared statements buffer the whole binary packet. Use its server-side stream.
            try (var statement = connection.isWrapperFor(com.mysql.cj.jdbc.JdbcConnection.class)
                    ? connection.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).serverPrepareStatement(sql)
                    : connection.prepareStatement(sql);
                 var input = java.nio.file.Files.newInputStream(source)) {
                String actor = java.util.Objects.toString(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), "");
                statement.setLong(1, getId()); statement.setString(2, path);
                statement.setBinaryStream(3, input, java.nio.file.Files.size(source));
                statement.setString(4, actor); statement.setString(5, actor);
                statement.executeUpdate();
            }
        } finally { org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, dataSource); }
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public void delete(String path) {
        fileContentMapper.deleteByConfigIdAndPath(getId(), path);
    }

    @Override
    public byte[] getContent(String path) {
        List<FileContentDO> list = fileContentMapper.selectListByConfigIdAndPath(getId(), path);
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        // 排序后，拿 id 最大的，即最后上传的
        list.sort(Comparator.comparing(FileContentDO::getId));
        return CollUtil.getLast(list).getContent();
    }

}

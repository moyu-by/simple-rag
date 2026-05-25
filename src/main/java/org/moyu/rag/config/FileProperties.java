package org.moyu.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置属性。
 *
 * <p>对应 {@code application.yml} 中的 {@code file.*} 配置项。
 * 提供 {@link #getUrlPrefix()} 拼接文件外网访问地址。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /** 访问协议 */
    private String protocol = "http";

    /** 服务器地址 */
    private String host = "localhost";

    /** 端口 */
    private int port = 8080;

    /** URL 子路径 */
    private String subPath = "file";

    /** 文件本地存储目录 */
    private String storePath = "./upload";

    /**
     * 拼接文件外网访问前缀。
     * <p>例：{@code http://localhost:8080/file/}</p>
     */
    public String getUrlPrefix() {
        return protocol + "://" + host + ":" + port + "/" + subPath + "/";
    }
}

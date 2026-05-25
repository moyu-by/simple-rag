package org.moyu.rag.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES 对称加密工具，用于保护存储到数据库的敏感字段（如 API Key）。
 *
 * <p>使用 AES/CBC/PKCS5Padding，密钥从 {@code application.yml} 中的
 * {@code aes.secret-key} 读取，必须以 Base64 或 Hex 格式提供。</p>
 *
 * <p><b>为什么不用 RSA？</b></p>
 * <ul>
 *   <li>RSA 公钥加密 → 私钥解密，适合"前端加密，后端解密"场景</li>
 *   <li>AES 是"后端加密，后端解密"，密钥只有服务端知道，适合数据库字段保护</li>
 *   <li>这里需要的是"落库加密、读库解密"的双向操作，AES 更合适且性能更好</li>
 * </ul>
 */
@Slf4j
@Component
public class AesEncryptor {

    @Value("${aes.secret-key}")
    private String secretKeyHex;

    private AES aes;

    @PostConstruct
    void init() {
        // 支持 16 进制密钥（64 字符 = 32 bytes，AES-256）
        // 也支持直接用 SecureUtil.aes(keyBytes) 的自然长度
        byte[] keyBytes = cn.hutool.core.codec.Base64.decode(secretKeyHex);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            // 如果不是标准 Base64 长度，尝试当 Hex 解析
            keyBytes = SecureUtil.decode(secretKeyHex);
        }
        aes = SecureUtil.aes(keyBytes);
        log.info("AES 加密工具初始化完成");
    }

    /**
     * 加密明文，返回 Base64 编码的密文（可直接存数据库）。
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        return aes.encryptBase64(plainText);
    }

    /**
     * 解密密文（Base64 编码），返回明文。
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return "";
        }
        return aes.decryptStr(cipherText);
    }
}

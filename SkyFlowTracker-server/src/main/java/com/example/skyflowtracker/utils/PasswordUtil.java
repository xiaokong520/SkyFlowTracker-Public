package com.example.skyflowtracker.utils;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码加密工具类
 * 使用 PBKDF2 算法进行密码加密和验证
 */
@Component
public class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码（包含盐值）
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        try {
            // 生成随机盐值
            byte[] salt = generateSalt();
            
            // 加密密码
            byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            
            // 将盐值和哈希值组合并编码为 Base64
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);
            
            return saltBase64 + ":" + hashBase64;
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 密码是否匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        
        try {
            // 分离盐值和哈希值
            String[] parts = encodedPassword.split(":");
            if (parts.length != 2) {
                return false;
            }
            
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] hash = Base64.getDecoder().decode(parts[1]);
            
            // 使用相同的盐值加密输入的密码
            byte[] testHash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            
            // 比较两个哈希值
            return slowEquals(hash, testHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成随机盐值
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * PBKDF2 加密
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }

    /**
     * 安全的字节数组比较（防止时序攻击）
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    /**
     * 验证密码强度
     * @param password 密码
     * @return 是否符合强度要求（至少8位，包含字母和数字）
     */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        // 至少包含一个字母和一个数字
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        return hasLetter && hasDigit;
    }

    /**
     * 获取密码强度等级
     * @param password 密码
     * @return 强度等级：0-弱，1-中，2-强
     */
    public static int getPasswordStrength(String password) {
        if (password == null || password.length() < 6) {
            return 0; // 弱
        }

        int strength = 0;
        
        // 长度检查
        if (password.length() >= 8) {
            strength++;
        }
        
        // 复杂度检查
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
        
        int complexity = 0;
        if (hasLower) complexity++;
        if (hasUpper) complexity++;
        if (hasDigit) complexity++;
        if (hasSpecial) complexity++;
        
        if (complexity >= 3) {
            strength++;
        }
        
        return strength; // 0-弱，1-中，2-强
    }
}

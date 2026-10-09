package com.example.skyflowtracker.utils;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.PemUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.example.skyflowtracker.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class TokenUtil {
    @Value("${jwt.filePath}")
    private String filePath;

    @Value("${jwt.issuer:skyflowtracker}")
    private String issuer;
    //生成token
    //私钥加密
    public String creatToken(String userId,Integer role)
            throws Exception  {
        //读取pem私钥文件
        BufferedInputStream pemPrivateKey = FileUtil.getInputStream(filePath + "/privatekey.pem");
        PrivateKey privateKey = PemUtil.readPemPrivateKey(pemPrivateKey);
        //生成签名器
        JWTSigner signer = JWTSignerUtil.rs256(privateKey);
        JWT jwt = JWT.create()
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiresAt(new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) //7天
                .setPayload("userId", userId)
                .setPayload("role",role)
                .setSigner(signer);
        return jwt.sign();
    }
    //解析/验证token
    //公钥解密
    public Map<String,Object> checkToken(String token)
            throws Exception{
        try{
            //读取emp公钥文件
            BufferedInputStream pemPublicKey = FileUtil.getInputStream(filePath + "/publickey.pem");
            PublicKey publicKey = PemUtil.readPemPublicKey(pemPublicKey);
            //生成签名器
            JWTSigner signer = JWTSignerUtil.rs256(publicKey);
            JWT jwt = JWT.of(token).setSigner(signer);
            //验证jwt
            JWTValidator.of(jwt)
                    .validateDate(new Date())
                    .validateAlgorithm(signer);
            //构造返回数据
            Map<String,Object> map = new HashMap<>();
            //获取userid与role
            String userId = jwt.getPayloads().getStr("userId");
            String role = jwt.getPayloads().getStr("role");
            if (role != null){
                map.put("role", Integer.parseInt(role));
            }
            map.put("message","验证通过");
            map.put("userId", userId);
            return map;
        }catch (Exception e){
            throw new AppException(400,handleHutoolException(e));
        }
    }
    //异常处理
    private static String handleHutoolException(Exception e){
        if (e instanceof ValidateException){
            return "token已过期";
        }
        if(e.getClass().getSimpleName().equals("JSONException")){
            return "token格式错误";
        }
        return "验证失败" + e.getMessage();
    }
}

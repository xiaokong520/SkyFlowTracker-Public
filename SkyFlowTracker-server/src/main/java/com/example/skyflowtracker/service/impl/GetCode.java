package com.example.skyflowtracker.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.service.inte.GetCodeInte;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GetCode implements GetCodeInte {
    private  EmailService emailService;
    private RedisTemplate<String,String> redisTemplate;
    @Autowired
    public GetCode(EmailService emailService, RedisTemplate<String, String> redisTemplate) {
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getVerificationCode(String email) throws IOException{
        //判断缓存中是否已经存在验证码
        String code = redisTemplate.opsForValue().get("SkyFlowTracker:code:" + email);
        if (code != null){
            throw new AppException(AppExceptionCodeMsg.CODE_EXIST);
        }
        //不存在则生成验证码
        code = createCode();
        //发送验证码
        try{
            String htmlContent = emailService.readHtmlFile("static/email/registCodeEmail.html");
            htmlContent = htmlContent.replace("${code}",code);
            String subject = "SkyFlowTracker注册";
            emailService.sendHtmlEmail(email,subject,htmlContent);
        }catch (MessagingException e){
            log.error("验证码发送失败！",e.getMessage());
            return "验证码发送失败！";
        }
        //将验证码存入redis缓存中(10分钟有效期)
        redisTemplate.opsForValue().set("SkyFlowTracker:code:" + email,code,10, TimeUnit.MINUTES);
        return "验证码发送成功，请注意查收！";
    }

    public String createCode(){
        String str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(str.length() - 1);
            code.append(str.charAt(index));
        }
        return code.toString();
    }

    @Override
    public Map<String, String> getImagCode() {
        //创建扭曲验证码
        ShearCaptcha shearCaptcha = CaptchaUtil.createShearCaptcha(250,100,4,5);
        String code = shearCaptcha.getCode();
        System.out.println(code);
        //生成UUID
        String uuid = UUID.randomUUID().toString();
        //将图片转换成BASE64字符串
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        shearCaptcha.write(outputStream);
        String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());
        //将验证码存入到redis中
        redisTemplate.opsForValue().set("SkyFlowTracker:imgCode:" + uuid,code,10,TimeUnit.MINUTES);
        //构建返回消息
        Map<String,String> data = new HashMap<>();
        data.put("imgId",uuid);
        data.put("base64Image",base64Image);
        return data;
    }
}

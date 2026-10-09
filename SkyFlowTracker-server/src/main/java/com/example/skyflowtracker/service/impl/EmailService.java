package com.example.skyflowtracker.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import com.example.skyflowtracker.service.inte.EmailServiceInte;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService implements EmailServiceInte {
    private JavaMailSender javaMailSender;
    @Autowired
    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Value("${spring.mail.username}")
    private String from;
    @Override
    public void sendHtmlEmail(String to, String subject, String content) throws MessagingException {
        MimeMessage message= javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(message,true,"UTF-8");
        //发送邮件设置
        mimeMessageHelper.setFrom(from);
        mimeMessageHelper.setTo(to);
        mimeMessageHelper.setSubject(subject);
        mimeMessageHelper.setText(content,true);
        //发送html邮件
        javaMailSender.send(message);
    }
    //读取html模板文件
    public String readHtmlFile(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource(templateName);
        try(InputStream inputStream = resource.getInputStream()){
            byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
            return new String(bytes,StandardCharsets.UTF_8);
        }
    }
}

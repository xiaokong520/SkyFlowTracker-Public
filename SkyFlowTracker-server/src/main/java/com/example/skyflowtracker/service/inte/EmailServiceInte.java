package com.example.skyflowtracker.service.inte;

import jakarta.mail.MessagingException;

import java.io.IOException;

public interface EmailServiceInte {
    public void sendHtmlEmail(String to,String subject,String content) throws MessagingException;
    public String readHtmlFile(String templateName) throws IOException;
}

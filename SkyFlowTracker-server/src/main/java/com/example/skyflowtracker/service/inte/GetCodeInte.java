package com.example.skyflowtracker.service.inte;

import java.io.IOException;
import java.util.Map;

public interface GetCodeInte {
    //获取邮件验证码（注册）
    public String getVerificationCode(String email) throws IOException;

    public String createCode();

    public Map<String,String>  getImagCode();
}

package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginDto {
    private String userName;
    private String email;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 25,message = "密码长度不能小于8位，不能大于25位")
    private String password;
    @NotBlank(message = "验证码不能为空")
    private String imgCode;
    @NotBlank(message = "图片验证码ID不能为空")
    private String imgId;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getImgCode() {
        return imgCode;
    }

    public void setImgCode(String imgCode) {
        this.imgCode = imgCode;
    }

    public String getImgId() {
        return imgId;
    }

    public void setImgId(String imgId) {
        this.imgId = imgId;
    }
}

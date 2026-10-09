package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePasswordDto {
    private String userId;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8,max = 25,message = "密码长度不能少于8位，不能多于25位")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

package com.example.skyflowtracker.dto;

import java.util.List;

public class DeleteDevicesDto {
    private String userId;
    private List<String> sns;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getSns() {
        return sns;
    }

    public void setSns(List<String> sns) {
        this.sns = sns;
    }
}

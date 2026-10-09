package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class DeleteUsersIdsDto {
    @NotBlank(message = "用户ID列表不能为空")
    private List<String> userIds;

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}

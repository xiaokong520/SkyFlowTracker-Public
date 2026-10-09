package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class CreateShareDto {
    @NotNull(message = "taskId不能为空")
    private Long taskId;

    @NotNull(message = "expireHours不能为空")
    private Integer expireHours;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(Integer expireHours) {
        this.expireHours = expireHours;
    }
}

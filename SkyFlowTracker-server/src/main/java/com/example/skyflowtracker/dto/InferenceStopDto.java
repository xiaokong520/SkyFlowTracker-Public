package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class InferenceStopDto {
    @NotNull(message = "taskId不能为空")
    private Long taskId;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
}

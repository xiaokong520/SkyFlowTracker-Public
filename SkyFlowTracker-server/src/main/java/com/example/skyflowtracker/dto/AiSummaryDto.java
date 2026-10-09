package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class AiSummaryDto {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
}

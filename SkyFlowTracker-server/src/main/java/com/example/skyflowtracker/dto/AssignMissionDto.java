package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class AssignMissionDto {
    @NotNull(message = "任务ID不能为空")
    private Long missionId;
    @NotNull(message = "被分派用户ID不能为空")
    private String assigneeId;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
}

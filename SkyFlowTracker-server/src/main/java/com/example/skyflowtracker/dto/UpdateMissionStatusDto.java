package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateMissionStatusDto {
    @NotNull(message = "任务ID不能为空")
    private Long missionId;
    @NotNull(message = "状态不能为空")
    private Integer status;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}

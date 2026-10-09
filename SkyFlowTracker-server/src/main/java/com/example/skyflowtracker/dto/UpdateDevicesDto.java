package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class UpdateDevicesDto {
    @NotBlank(message = "SN码不能为空")
    private String sn;
    private String userId;
    private String model;
    private LocalDateTime lastOnlineTime;
    private Long accumulatedVoyage;
    private Long flyTime;
    private String flightControllerSerialNumber;
    private LocalDateTime modificationTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(LocalDateTime lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public Long getAccumulatedVoyage() {
        return accumulatedVoyage;
    }

    public void setAccumulatedVoyage(Long accumulatedVoyage) {
        this.accumulatedVoyage = accumulatedVoyage;
    }

    public Long getFlyTime() {
        return flyTime;
    }

    public void setFlyTime(Long flyTime) {
        this.flyTime = flyTime;
    }

    public String getFlightControllerSerialNumber() {
        return flightControllerSerialNumber;
    }

    public void setFlightControllerSerialNumber(String flightControllerSerialNumber) {
        this.flightControllerSerialNumber = flightControllerSerialNumber;
    }

    public LocalDateTime getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(LocalDateTime modificationTime) {
        this.modificationTime = modificationTime;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }
}

package com.example.skyflowtracker.pojo;

import java.time.LocalDateTime;

public class Devices {
    private String sn; // 设备序列号（主键）
    private String userId; // 用户ID（主键）
    private String model; // 设备型号
    private LocalDateTime lastOnlineTime; // 最后在线时间
    private Long accumulatedVoyage; // 累计航程
    private Long flyTime; // 飞行时间
    private String flightControllerSerialNumber; // 飞控序列号
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime modificationTime; // 修改时间

    public Devices() {
    }

    public Devices(String sn, String userId) {
        this.sn = sn;
        this.userId = userId;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(LocalDateTime modificationTime) {
        this.modificationTime = modificationTime;
    }

    @Override
    public String toString() {
        return "Devices{" +
                "sn='" + sn + '\'' +
                ", userId='" + userId + '\'' +
                ", model='" + model + '\'' +
                ", lastOnlineTime=" + lastOnlineTime +
                ", accumulatedVoyage=" + accumulatedVoyage +
                ", flyTime=" + flyTime +
                ", flightControllerSerialNumber='" + flightControllerSerialNumber + '\'' +
                ", createTime=" + createTime +
                ", modificationTime=" + modificationTime +
                '}';
    }
}

package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class FlightsEndDto {
    @NotNull(message = "flightId不能为空")
    private Long flightId;
    private Double maxAltitude;
    private Double maxSpeed;
    private Double distance;
    private Integer status; // 1=已完成 2=异常终止

    public Long getFlightId() {
        return flightId;
    }

    public void setFlightId(Long flightId) {
        this.flightId = flightId;
    }

    public Double getMaxAltitude() {
        return maxAltitude;
    }

    public void setMaxAltitude(Double maxAltitude) {
        this.maxAltitude = maxAltitude;
    }

    public Double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

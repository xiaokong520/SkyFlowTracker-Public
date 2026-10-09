package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class InferenceStartDto {
    private Long flightId;
    private String sn;
    private String modelName;

    public Long getFlightId() {
        return flightId;
    }

    public void setFlightId(Long flightId) {
        this.flightId = flightId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }
}

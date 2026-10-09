package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;

public class FlightsStartDto {
    @NotBlank(message = "SN码不能为空")
    public String sn;
    public Double home_latitude;
    public Double home_longitude;

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public Double getHome_latitude() {
        return home_latitude;
    }

    public void setHome_latitude(Double home_latitude) {
        this.home_latitude = home_latitude;
    }

    public Double getHome_longitude() {
        return home_longitude;
    }

    public void setHome_longitude(Double home_longitude) {
        this.home_longitude = home_longitude;
    }
}

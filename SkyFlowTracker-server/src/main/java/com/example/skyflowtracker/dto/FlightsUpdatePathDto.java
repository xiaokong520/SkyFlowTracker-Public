package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class FlightsUpdatePathDto {
    @NotNull(message = "flightId不能为空")
    private Long flightId;
    @NotNull(message = "坐标点不能为空")
    private List<PathPoint> points;

    //航线坐标点
    public static class PathPoint {
        private Double lat; //维度
        private Double lng; //经度
        private Double alt; //高度
        private Double speed; //速度
        private Double heading; //航向角
        private Long ts; //时间戳（毫秒）

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }

        public Double getLng() {
            return lng;
        }

        public void setLng(Double lng) {
            this.lng = lng;
        }

        public Double getAlt() {
            return alt;
        }

        public void setAlt(Double alt) {
            this.alt = alt;
        }

        public Double getSpeed() {
            return speed;
        }

        public void setSpeed(Double speed) {
            this.speed = speed;
        }

        public Double getHeading() {
            return heading;
        }

        public void setHeading(Double heading) {
            this.heading = heading;
        }

        public Long getTs() {
            return ts;
        }

        public void setTs(Long ts) {
            this.ts = ts;
        }
    }

    public Long getFlightId() {
        return flightId;
    }

    public void setFlightId(Long flightId) {
        this.flightId = flightId;
    }

    public List<PathPoint> getPoints() {
        return points;
    }

    public void setPoints(List<PathPoint> points) {
        this.points = points;
    }
}

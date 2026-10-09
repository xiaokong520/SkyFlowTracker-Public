package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SyncFlyZonesDto {
    @NotNull(message = "禁飞区数据不能为空")
    private List<FlyZoneItem> zones;

    public List<FlyZoneItem> getZones() { return zones; }
    public void setZones(List<FlyZoneItem> zones) { this.zones = zones; }

    public static class FlyZoneItem {
        private Integer djiFlyZoneId;
        private String name;
        private Integer category;
        private Integer shape;
        private Double centerLat;
        private Double centerLng;
        private Double radius;
        private List<LatLngPoint> polygonPoints;
        private Double maxAltitude;

        public Integer getDjiFlyZoneId() { return djiFlyZoneId; }
        public void setDjiFlyZoneId(Integer djiFlyZoneId) { this.djiFlyZoneId = djiFlyZoneId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getCategory() { return category; }
        public void setCategory(Integer category) { this.category = category; }

        public Integer getShape() { return shape; }
        public void setShape(Integer shape) { this.shape = shape; }

        public Double getCenterLat() { return centerLat; }
        public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

        public Double getCenterLng() { return centerLng; }
        public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }

        public Double getRadius() { return radius; }
        public void setRadius(Double radius) { this.radius = radius; }

        public List<LatLngPoint> getPolygonPoints() { return polygonPoints; }
        public void setPolygonPoints(List<LatLngPoint> polygonPoints) { this.polygonPoints = polygonPoints; }

        public Double getMaxAltitude() { return maxAltitude; }
        public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }
    }

    public static class LatLngPoint {
        private Double lat;
        private Double lng;

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }

        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }
}

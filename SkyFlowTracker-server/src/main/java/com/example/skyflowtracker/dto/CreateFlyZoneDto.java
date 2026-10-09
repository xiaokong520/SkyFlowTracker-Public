package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateFlyZoneDto {
    @NotBlank(message = "区域名称不能为空")
    private String name;
    @NotNull(message = "区域类别不能为空")
    private Integer category;
    @NotNull(message = "区域形状不能为空")
    private Integer shape;
    private Double centerLat;
    private Double centerLng;
    private Double radius;
    private String polygonPoints;
    private Double maxAltitude;
    private String description;

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

    public String getPolygonPoints() { return polygonPoints; }
    public void setPolygonPoints(String polygonPoints) { this.polygonPoints = polygonPoints; }

    public Double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

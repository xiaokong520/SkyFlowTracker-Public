package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AddDevicesDto {
    @NotBlank(message = "添加类型不能为空")
    private String addType; //添加的类型，manual为手动/扫码添加，auto安卓端自动绑定
    private String targetUserId; //目标用户id
    private List<DeviceInfo> deviceInfos; //设备列表


    public class DeviceInfo {
        @NotBlank(message = "无人机SN码不能为空")
        private String sn;
        private String model; //型号
        private String flightControllerSerialNumber; //飞控序列号

        public String getSn() {
            return sn;
        }

        public void setSn(String sn) {
            this.sn = sn;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getFlightControllerSerialNumber() {
            return flightControllerSerialNumber;
        }

        public void setFlightControllerSerialNumber(String flightControllerSerialNumber) {
            this.flightControllerSerialNumber = flightControllerSerialNumber;
        }
    }

    public String getAddType() {
        return addType;
    }

    public void setAddType(String addType) {
        this.addType = addType;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public List<DeviceInfo> getDeviceInfos() {
        return deviceInfos;
    }

    public void setDeviceInfos(List<DeviceInfo> deviceInfos) {
        this.deviceInfos = deviceInfos;
    }
}

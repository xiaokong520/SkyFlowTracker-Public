package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.AddDevicesDto;
import com.example.skyflowtracker.dto.DeleteDevicesDto;
import com.example.skyflowtracker.dto.UpdateDevicesDto;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface DevicesServiceInte {
    Map<String, Object> addDevices(MultipartFile file, String snCode, String targetUserId, String deviceData, String token) throws Exception;
    Map<String, Object> getDevicesList(String token, Integer page, Integer size, String snCode, String userId) throws Exception;

    String updateDevices(String token, UpdateDevicesDto updateDevicesDto) throws Exception;

    String deleteDevices(String token, DeleteDevicesDto deleteDevicesDto) throws Exception;

    boolean checkDeviceBind(String token, String sn) throws Exception;
}

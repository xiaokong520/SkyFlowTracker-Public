package com.example.skyflowtracker.service.impl;

import com.baidu.aip.ocr.AipOcr;
import com.example.skyflowtracker.dto.DeleteDevicesDto;
import com.example.skyflowtracker.dto.UpdateDevicesDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.DevicesMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.Devices;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.DevicesServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DevicesService implements DevicesServiceInte {
    private DevicesMapper devicesMapper;
    private UsersMapper usersMapper;
    private RedisTemplate<String, String> redisTemplate;
    private TokenUtil tokenUtil;
    private ObjectMapper objectMapper;

    @Value("${baidu.appid}")
    private String APP_ID;
    @Value("${baidu.apikey}")
    private String APi_KEY;
    @Value("${baidu.secretkey}")
    private String SECRET_KEY;

    @Autowired
    public DevicesService(DevicesMapper devicesMapper, UsersMapper usersMapper, RedisTemplate<String, String> redisTemplate, TokenUtil tokenUtil) {
        this.devicesMapper = devicesMapper;
        this.usersMapper = usersMapper;
        this.redisTemplate = redisTemplate;
        this.tokenUtil = tokenUtil;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public Map<String,Object> addDevices(MultipartFile file, String snCode, String targetUserId,String deviceData, String token) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer currentRole = Integer.parseInt(checkToken.get("role").toString());
        //确定目标用户id
        String finalUserId;
        if (targetUserId != null && !targetUserId.isEmpty()){
            //不为空则代表管理员添加设备
            if (currentRole != 1){
                throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
            }
            finalUserId = targetUserId;
        }else {
            //为空则代表添加自己的id
            finalUserId = currentUserId;
        }
        //判断用户状态
        Users currentUser = usersMapper.selectUserByUserId(finalUserId);
        if (currentUser == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userStatus = currentUser.getStatus();
        if (userStatus == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userStatus == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //解析设备数据
        List<Devices> devicesList = new ArrayList<>();
        //情况1：上传二维码
        if (file != null && !file.isEmpty()){
            String sn = parseBaiduQRCode(file);
            System.out.println("sn: " + sn);
            if (sn == null || sn.isEmpty()){
                throw new AppException(AppExceptionCodeMsg.QR_CODE_ERROR);
            }
            LocalDateTime now = LocalDateTime.now();
            Devices devices = new Devices();
            devices.setSn(sn);
            devices.setUserId(finalUserId);
            devices.setCreateTime(now);
            devices.setModificationTime(now);
            devicesList.add(devices);
        } else if (snCode != null && !snCode.isEmpty()) {
            //情况2：手动添加SN码（支持批量添加）
            String[] snArray = snCode.split("[,\\n\r]+"); //支持逗号或者换行符分割
            LocalDateTime now = LocalDateTime.now();
            for (String sn:snArray){
                sn = sn.trim();
                if (!sn.isEmpty()){
                    Devices devices = new Devices();
                    devices.setSn(sn);
                    devices.setUserId(finalUserId);
                    devices.setCreateTime(now);
                    devices.setModificationTime(now);
                    devicesList.add(devices);
                }
            }
        } else if (deviceData != null && !deviceData.isEmpty()) {
            //情况3：安卓端自动绑定设备
            //解析字符串为devices对象
            try {
                Map<String,Object> data = objectMapper.readValue(deviceData,Map.class);
                String sn = data.get("sn").toString();
                if (sn == null || sn.isEmpty()){
                    throw new AppException(AppExceptionCodeMsg.DEVICE_DATA_ERROR);
                }
                Devices devices = new Devices();
                LocalDateTime now = LocalDateTime.now();
                devices.setSn(sn);
                devices.setUserId(finalUserId);
                devices.setModel(data.get("model").toString());
                devices.setFlightControllerSerialNumber(data.get("flightControllerSerialNumber").toString());
                devices.setCreateTime(now);
                devices.setModificationTime(now);
                devicesList.add(devices);
            }catch (Exception e){
                log.error("解析设备数据出错：{}", e.getMessage());
                throw new AppException(AppExceptionCodeMsg.DEVICE_DATA_ERROR);
            }
        }
        //检查设备列表是否为空
        if (devicesList.isEmpty()){
            throw new AppException(AppExceptionCodeMsg.DEVICE_LIST_EMPTY);
        }
        //添加
        int failCount = 0;
        List<Devices> vaildDevicesList = new ArrayList<>();
        StringBuilder errorMsg = new StringBuilder();
        for (Devices device:devicesList){
            try {
                //检查该设备是否存在
                Devices existDevice = devicesMapper.selectDeviceBySn(device.getSn());
                if (existDevice != null){
                    failCount++;
                    //判断是该账号绑定还是其他账号绑定
                    if (existDevice.getUserId().equals(device.getUserId())){
                        errorMsg.append("设备 ").append(device.getSn()).append("已存在，请勿重复添加\n");
                    }else {
                        //获取用户名
                        Users user = usersMapper.selectUserByUserId(existDevice.getUserId());
                        errorMsg.append("设备 ").append(device.getSn()).append("已绑定给用户 ").append(user.getUserName()).append("\n");
                    }
                }else {
                    //只有不存在的设备才加入有效列表
                    vaildDevicesList.add(device);
                }
            }catch (Exception e){
                log.error("添加设备出错：{}", e.getMessage());
                failCount++;
                errorMsg.append("设备 ").append(device.getSn()).append("添加失败，原因：").append(e.getMessage()).append("\n");
            }
        }
        //插入数据
        int result = 0;
        if (!vaildDevicesList.isEmpty()){
            result = devicesMapper.insertDevices(vaildDevicesList);
        }
        //构造返回消息
        Map<String,Object> map = new HashMap<>();
        Map<String,String> data = new HashMap<>();
        if (result > 0){
            map.put("message","添加成功");
        }else {
            map.put("message","添加失败");
        }
        data.put("successCount",String.valueOf(result));
        data.put("failCount",String.valueOf(failCount));
        data.put("errorMsg",errorMsg.toString());
        map.put("data",data);
        return map;
    }

    @Override
    public Map<String, Object> getDevicesList(String token, Integer page, Integer size, String snCode, String filterUserId) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer currentRole = Integer.parseInt(checkToken.get("role").toString());
        //判断是否是管理员，非管理员只能查询自己账号下的设备
        String userId = null;
        if (currentRole != 1){
            userId = currentUserId;
        } else if (filterUserId != null && !filterUserId.isEmpty()) {
            userId = filterUserId;
        }
        //计算分页参数
        int offset = (page - 1) * size;
        //查询设备列表和总数
        List<Map<String,Object>> devicesList = devicesMapper.selectDevicesList(userId,snCode,offset,size);
        int total = devicesMapper.countDevices(userId,snCode);
        //构建分页信息
        Map<String,Object> pagination = new HashMap<>();
        pagination.put("page",page);
        pagination.put("size",size);
        pagination.put("total",total);
        pagination.put("totalPages",(int) Math.ceil((double) total / size));
        //构建返回数据
        Map<String,Object> result = new HashMap<>();
        result.put("message","获取成功");
        Map<String,Object> data = new HashMap<>();
        data.put("devicesList",devicesList);
        data.put("pagination",pagination);
        result.put("data",data);

        return result;
    }

    @Override
    public String updateDevices(String token, UpdateDevicesDto updateDevicesDto) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer currentRole = Integer.parseInt(checkToken.get("role").toString());
        String updateUserId = updateDevicesDto.getUserId();
        //检查权限，只有管理员才能修改所有人的设备信息
        if (updateUserId != null){
            if (currentRole != 1){
                throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
            }else {
                //判断该用户是否存在
                Users users = usersMapper.selectUserByUserId(updateUserId);
                if (users == null){
                    throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
                }else {
                    currentUserId = updateUserId;
                }
            }
        }
        //判断该设备是否存在
        Devices devices = devicesMapper.selectDeviceBySnAndUserId(updateDevicesDto.getSn(), currentUserId);
        if (devices == null){
            throw new AppException(AppExceptionCodeMsg.DEVICE_NOT_EXIST);
        }
        //更新设备数据
        updateDevicesDto.setUserId(currentUserId);
        updateDevicesDto.setModificationTime(LocalDateTime.now());

        int result = devicesMapper.updateDevices(updateDevicesDto);
        if (result > 0){
            return "修改成功";
        }else {
            return "修改失败";
        }
    }

    @Override
    public String deleteDevices(String token, DeleteDevicesDto deleteDevicesDto) throws Exception {
        //检查token并检查权限
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        Integer currentRole = Integer.parseInt(checkToken.get("role").toString());
        String currentUserId = checkToken.get("userId").toString();
        String deleteUserId = deleteDevicesDto.getUserId();
        if (deleteUserId != null){
            if (currentRole != 1){
                throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
            }else {
                currentUserId = deleteUserId;
            }
            //判断用户是否存在
            Users users = usersMapper.selectUserByUserId(currentUserId);
            if (users == null){
                throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
            }
        }
        //删除数据
        deleteDevicesDto.setUserId(currentUserId);
        int result = devicesMapper.deleteDevices(deleteDevicesDto);
        if (result > 0){
            return "删除成功";
        }else {
            return "删除失败";
        }
    }

    @Override
    public boolean checkDeviceBind(String token, String sn) throws Exception {
        //检查token，获取当前用户id
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        //用 sn + user_id 精确查询该设备是否属于当前用户
        Devices devices = devicesMapper.selectDeviceBySnAndUserId(sn, currentUserId);
        return devices != null;
    }

    //解析二维码图片获取SN码
    public String parseBaiduQRCode(MultipartFile file){
        try {
            AipOcr client = new AipOcr(APP_ID,APi_KEY,SECRET_KEY);
            byte[] imageBytes = file.getBytes();
            JSONObject result = client.qrcode(imageBytes,new HashMap<>());
            log.info("result: {}", result.toString());
            //检查是否有识别结果
            int codesResultNum = result.optInt("codes_result_num",0);
            if (codesResultNum == 0){
                log.error("未识别到二维码");
                return null;
            }
            //获取识别结果中的text数组
            JSONArray codesResult = result.getJSONArray("codes_result");
            if (codesResult != null && codesResult.length() > 0){
                JSONObject firstCode = codesResult.getJSONObject(0);
                JSONArray textArray = firstCode.optJSONArray("text");
                if (textArray != null && textArray.length() > 0){
                    String sn = textArray.getString(0);
                    log.info("sn: {}", sn);
                    return sn;
                }
            }
            return null;
        }catch (Exception e){
            log.error("百度二维码识别失败：{}", e.getMessage());
            return null;
        }
    }

}

package com.example.skyflowtracker.service.impl;


import com.example.skyflowtracker.dto.DeleteInferenceTasksDto;
import com.example.skyflowtracker.dto.InferenceStartDto;
import com.example.skyflowtracker.dto.InferenceStopDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.DevicesMapper;
import com.example.skyflowtracker.mapper.FlightsMapper;
import com.example.skyflowtracker.mapper.InferenceTasksMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.Flights;
import com.example.skyflowtracker.pojo.InferenceTasks;
import com.example.skyflowtracker.pojo.Devices;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.InferenceTasksServiceInte;
import com.example.skyflowtracker.utils.DemoVideoDecoder;
import com.example.skyflowtracker.utils.TokenUtil;
import com.example.skyflowtracker.websocket.InferenceHandler;
import com.example.skyflowtracker.websocket.VideoStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InferenceTasksService implements InferenceTasksServiceInte {
    private TokenUtil tokenUtil;
    private FlightsMapper flightsMapper;
    private UsersMapper usersMapper;
    private DevicesMapper devicesMapper;
    private InferenceTasksMapper inferenceTasksMapper;
    private VideoStreamHandler videoStreamHandler;
    private InferenceHandler inferenceHandler;
    private RestTemplate restTemplat;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Value("${flask.baseUrl}")
    private String flaskBaseUrl;

    @Value("${nginx.baseUrl}")
    private String nginxBaseUrl;

    //正在运行的推理任务: taskId -> 线程
    private final ConcurrentHashMap<Long, Thread> runningTasks = new ConcurrentHashMap<>();
    //任务停止标志: taskId -> 是否已停止
    private final ConcurrentHashMap<Long, Boolean> stopFlags = new ConcurrentHashMap<>();
    //taskId -> sessionId 映射
    private final ConcurrentHashMap<Long, String> taskSessionMap = new ConcurrentHashMap<>();
    //sn->taskId映射
    private final ConcurrentHashMap<String, Long> snTaskMap = new ConcurrentHashMap<>();
    //任务启动时间: taskId -> 启动时间戳
    private final ConcurrentHashMap<Long, Long> taskStartTime = new ConcurrentHashMap<>();
    //演示视频解码器: sn -> DemoVideoDecoder
    private final ConcurrentHashMap<String, DemoVideoDecoder> demoDecoders = new ConcurrentHashMap<>();
    // Flask 演示会话: sn -> flask demo session_id
    private final ConcurrentHashMap<String, String> demoSessionMap = new ConcurrentHashMap<>();
    // 演示预览线程: sn -> thread
    private final ConcurrentHashMap<String, Thread> demoPreviewThreads = new ConcurrentHashMap<>();
    // 演示停止标志: sn -> stopped
    private final ConcurrentHashMap<String, Boolean> demoStopFlags = new ConcurrentHashMap<>();

    @Autowired
    public InferenceTasksService(TokenUtil tokenUtil, FlightsMapper flightsMapper,UsersMapper usersMapper,
                                 DevicesMapper devicesMapper,
                                 InferenceTasksMapper inferenceTasksMapper,@Lazy VideoStreamHandler videoStreamHandler,
                                 InferenceHandler inferenceHandler, RestTemplate restTemplat) {
        this.tokenUtil = tokenUtil;
        this.flightsMapper = flightsMapper;
        this.usersMapper = usersMapper;
        this.devicesMapper = devicesMapper;
        this.inferenceTasksMapper = inferenceTasksMapper;
        this.videoStreamHandler = videoStreamHandler;
        this.inferenceHandler = inferenceHandler;
        this.restTemplat = restTemplat;
    }

    //使用setter延迟注入，防止循环依赖
    @Autowired
    public void setVideoStreamHandler(VideoStreamHandler videoStreamHandler) {
        this.videoStreamHandler = videoStreamHandler;
    }

    @Override
    public Map<String, Object> start(String token, InferenceStartDto inferenceStartDto) throws Exception {
        //获取userId
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        //判断账号状态
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userRole = user.getStatus();
        if (userRole == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userRole == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //验证飞行记录是否存在
        Flights flights;
        if (inferenceStartDto.getFlightId() != null) {
            flights = flightsMapper.selectFlightsByIdAndUserId(inferenceStartDto.getFlightId(), userId);
        } else if (inferenceStartDto.getSn() != null && !inferenceStartDto.getSn().isEmpty()) {
            flights = flightsMapper.selectRunningFlightBySn(inferenceStartDto.getSn());
        } else {
            throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
        }
        if (flights == null){
            throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
        }
        //检查设备是否在线
        String sn = flights.getSn();
        if (!videoStreamHandler.isOnline(sn)){
            Map<String, Object> result = new HashMap<>();
            result.put("message","设备不在线，无法开始推理");
            return result;
        }
        //生成sessionId
        String sessionId = "task_" + flights.getId() + "_" + System.currentTimeMillis() / 1000;
        //创建推理任务记录
        LocalDateTime now = LocalDateTime.now();
        InferenceTasks task = new InferenceTasks();
        task.setFlightId(flights.getId());
        task.setModelName(inferenceStartDto.getModelName() != null ? inferenceStartDto.getModelName() : "yolov12x");
        task.setStatus(0); //进行中
        task.setStartTime(now);
        task.setCreateTime(now);
        task.setModificationTime(now);
        inferenceTasksMapper.insertTask(task);
        //保存taskId->sessionId映射
        taskSessionMap.put(task.getId(), sessionId);
        snTaskMap.put(sn,task.getId());
        stopFlags.put(task.getId(), false);
        taskStartTime.put(task.getId(), System.currentTimeMillis());
        //启动帧转发线程
        Thread forwardThread = new Thread(() ->
            forwardFrames(sn,sessionId,task.getId()),"inference-" + task.getId()
        );
        forwardThread.setDaemon(true);
        forwardThread.start();
        runningTasks.put(task.getId(), forwardThread);
        //返回结果
        Map<String,Object> result = new HashMap<>();
        result.put("message","开始推理");
        Map<String,Object> data = new HashMap<>();
        data.put("taskId",task.getId());
        data.put("sessionId",sessionId);
        result.put("data",data);

        return result;
    }

    @Override
    public Map<String, Object> stop(String token, InferenceStopDto inferenceStopDto) throws Exception {
        //获取userId
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        //判断账号状态
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userRole = user.getStatus();
        if (userRole == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userRole == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //获取任务id并检查该任务是否存在
        Long taskId = inferenceStopDto.getTaskId();
        InferenceTasks task = inferenceTasksMapper.selectTasksById(taskId);
        if (task == null){
            throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        }
        //检查该任务是否正在运行
        Thread runningThread = runningTasks.get(taskId);
        if (runningThread == null){
            // 任务线程已退出（可能视频自然播完已自动停止），检查任务状态
            if (task.getStatus() != null && task.getStatus() != 0) {
                // 任务已经结束（被 autoStop 处理过），直接返回成功
                Map<String, Object> result = new HashMap<>();
                result.put("message","停止推理成功");
                Map<String, Object> data = new HashMap<>();
                data.put("taskId", task.getId());
                result.put("data", data);
                return result;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("message","任务未运行");
            return result;
        }
        //获取 sn（用于后续清理映射）
        Flights flight = flightsMapper.selectFlightsById(task.getFlightId());
        String sn = flight != null ? flight.getSn() : null;
        //先获取sessionId（必须在线程退出前获取，因为forwardFrames退出时会清理taskSessionMap）
        String sessionId = taskSessionMap.get(taskId);
        taskSessionMap.remove(taskId);
        //停止推理线程
        stopFlags.put(taskId, true);
        runningThread.interrupt();
        runningTasks.remove(taskId);
        //等待帧转发线程真正退出，避免它继续 consume 帧
        try {
            runningThread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        //移除 sn 映射，之后 handleBinaryMessage 会自动推送原始帧
        if (sn != null) {
            snTaskMap.remove(sn);
            //等待一帧到达后主动推送原始帧，避免画面停留在最后一帧推理结果
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] latestFrame = videoStreamHandler.getLatestFrame(sn);
            if (latestFrame != null) {
                videoStreamHandler.pushOriginalFrameToWeb(sn, latestFrame);
            }
            DemoVideoDecoder demoDecoder = demoDecoders.remove(sn);
            if (demoDecoder != null) demoDecoder.stop();
            // 清理 Flask 演示会话
            demoSessionMap.remove(sn);
            demoStopFlags.remove(sn);
            videoStreamHandler.setDemoMode(sn, false);
        }
        //调用flask端停止推理
        Map<String,Object> flaskResult = null;
        if (sessionId != null){
            try {
                Map<String, Object> resultBody = new HashMap<>();
                resultBody.put("session_id", sessionId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(resultBody, headers);
                ResponseEntity<String> response = restTemplat.postForEntity(flaskBaseUrl + "/stop_inference", entity, String.class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null){
                    Map<String,Object> flaskResponse = objectMapper.readValue(response.getBody(),Map.class);
                    flaskResult = (Map<String,Object>) flaskResponse.get("data");
                }
            }catch (Exception e){
                log.error("调用flask停止推理失败：{}",e.getMessage());
            }
        }
        //更新任务列表
        LocalDateTime now = LocalDateTime.now();
        task.setEndTime(now);
        task.setStatus(1);
        task.setModificationTime(now);
        if (flaskResult != null){
            // 添加空值检查
            if (flaskResult.get("video_path") != null) {
                task.setVideoPath(flaskResult.get("video_path").toString());
            }
            if (flaskResult.get("total_detections") != null) {
                task.setTotalDetections(((Number)flaskResult.get("total_detections")).intValue());
            }
            //构建result_data
            Map<String,Object> resultData = new HashMap<>();
            if (flaskResult.get("frame_count") != null) {
                resultData.put("frame_count",flaskResult.get("frame_count"));
            }
            if (flaskResult.get("duration") != null) {
                resultData.put("duration",flaskResult.get("duration"));
            }
            if (flaskResult.get("class_counts") != null) {
                resultData.put("class_counts",flaskResult.get("class_counts"));
            }
            if (!resultData.isEmpty()) {
                task.setResultData(objectMapper.writeValueAsString(resultData));
            }
        }
        int res = inferenceTasksMapper.updateTask(task);
        //完成飞行记录
        if (flight != null) {
            flightsMapper.completeFlightById(flight.getId(), now, now);
        }
        //返回结果
        Map<String, Object> result = new HashMap<>();
        if (res > 0){
            result.put("message","停止推理成功");
            Map<String,Object> data = new HashMap<>();
            data.put("taskId",task.getId());
            if (flaskResult != null){
                data.put("videoPath",flaskResult.get("video_path"));
                data.put("totalDetections",flaskResult.get("total_detections"));
                data.put("duration",flaskResult.get("duration"));
                result.put("data",data);
            }
        }else {
            result.put("message","停止推理失败");
        }
        return result;
    }

    @Override
    public Map<String, Object> forceStop(String token, Long taskId) throws Exception {
        // 验证管理员权限
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        if (userRole != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        // 检查任务是否存在
        InferenceTasks task = inferenceTasksMapper.selectTasksById(taskId);
        if (task == null) {
            throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        }

        // 获取 sn
        Flights flight = flightsMapper.selectFlightsById(task.getFlightId());
        String sn = flight != null ? flight.getSn() : null;

        // 强制停止任务
        log.info("管理员强制停止推理任务: taskId={}", taskId);
        stopTaskInternal(taskId, sn);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "强制停止成功");
        return result;
    }

    @Override
    public Map<String, Object> getInferenceTasksList(String token, Long flightId, Integer page, Integer pageSize) throws Exception {
        //获取userId
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        //判断账号状态
        Users user = usersMapper.selectUserByUserId(currentUserId);
        if (user == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userRole = user.getStatus();
        if (userRole == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userRole == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //判断是否是管理员，只有管理员才可查询所有人的记录
        String userId = null;
        if (userRole != 1){
            userId = currentUserId;
        }
        //如果指定了飞行记录id，则验证飞行记录是否存在
        if (flightId != null){
            Flights flight;
            if (userRole == 1){
                //管理员：只验证飞行记录是否存在
                flight = flightsMapper.selectFlightsById(flightId);
            } else {
                //普通用户：验证飞行记录是否属于自己
                flight = flightsMapper.selectFlightsByIdAndUserId(flightId, currentUserId);
            }
            if (flight == null){
                throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
            }
        }
        //计算 分页参数
        int offset = (page - 1) * pageSize;
        //查询列表和总数
        List<Map<String ,Object>> list = inferenceTasksMapper.selectTasksList(userId, flightId, offset, pageSize);
        int total = inferenceTasksMapper.countTasks(userId, flightId);
        //构建分页信息
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("size", pageSize);
        pagination.put("total", total);
        pagination.put("totalPage", (int) Math.ceil((double) total / pageSize));
        //构建返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("tasksList", list);
        data.put("pagination", pagination);
        result.put("data", data);

        return result;
    }

    @Override
    public Map<String, Object> getInferenceTasksDetail(String token, Long taskId) throws Exception {
        //参数校验
        if (taskId == null){
            throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        }
        //获取userId和角色
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        //查询推理任务
        InferenceTasks task = inferenceTasksMapper.selectTasksById(taskId);
        if (task == null){
            throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        }
        //验证权限：管理员可以查所有，普通用户只能查自己的
        if (userRole != 1){
            if (task.getFlightId() != null) {
                //实时推理任务：通过飞行记录验证归属
                Flights flight = flightsMapper.selectFlightsByIdAndUserId(task.getFlightId(), currentUserId);
                if (flight == null) {
                    throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
                }
            } else {
                //离线推理任务：通过 user_id 验证归属
                if (!currentUserId.equals(task.getUserId())) {
                    throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
                }
            }
        }
        //构建返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("id", task.getId());
        data.put("flightId", task.getFlightId());
        data.put("modelName", task.getModelName());
        String videoPath = task.getVideoPath();
        if (videoPath != null && !videoPath.isEmpty()) {
            videoPath = nginxBaseUrl + videoPath;
        }
        data.put("videoPath", videoPath);
        //解析 resultData 为 JSON 对象
        Object resultDataJson;
        String resultDataStr = task.getResultData();
        if (resultDataStr != null && !resultDataStr.isEmpty()) {
            try {
                resultDataJson = objectMapper.readValue(resultDataStr, Map.class);
            } catch (Exception e) {
                resultDataJson = new HashMap<>();
            }
        } else {
            resultDataJson = new HashMap<>();
        }
        data.put("resultData", resultDataJson);

        data.put("status", task.getStatus());
        data.put("totalDetections", task.getTotalDetections());
        data.put("startTime", task.getStartTime());
        data.put("endTime", task.getEndTime());

        result.put("data", data);
        return result;
    }

    @Override
    public String deleteInferenceTasks(String token, DeleteInferenceTasksDto deleteInferenceTasksDto) throws Exception {
        //判断用户是否是管理员，只有管理员才可以删除
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        if (userRole != 1){
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }
        //删除数据
        int result = inferenceTasksMapper.deleteTasks(deleteInferenceTasksDto.getIds());
        if (result > 0){
            return "删除成功";
        }else {
            return "删除失败";
        }
    }

    //演示视频预览帧轮询：从 Flask /demo_frame 获取原始帧推送给 Web 前端
    private void pollDemoFrames(String sn, String flaskSessionId, double fps) {
        long intervalMs = Math.max((long) (1000.0 / fps) - 15, 10);
        log.info("演示预览轮询开始: sn={}, sessionId={}, fps={}, interval={}ms", sn, flaskSessionId, fps, intervalMs);

        while (!Boolean.TRUE.equals(demoStopFlags.get(sn))) {
            try {
                long startTime = System.currentTimeMillis();
                ResponseEntity<byte[]> response = restTemplat.getForEntity(
                    flaskBaseUrl + "/demo_frame?session_id=" + flaskSessionId, byte[].class);

                String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "";

                if (contentType.contains("image/jpeg") && response.getBody() != null) {
                    String metadataJson = "{\"code\":1,\"data\":{\"total\":0,\"detections\":[]}}";
                    inferenceHandler.pushBinaryInferenceResult(sn, response.getBody(), metadataJson);
                } else {
                    break;
                }
                // 扣除请求耗时，保持目标帧率
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepMs = Math.max(intervalMs - elapsed, 5);
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("轮询演示帧失败: {}", e.getMessage());
                try { Thread.sleep(100); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // 如果不是被主动停止的，说明视频自然播完
        if (!Boolean.TRUE.equals(demoStopFlags.get(sn))) {
            log.info("演示视频自然播完，触发自动停止: sn={}", sn);
            autoStopBySn(sn);
        }
    }

    //帧转发循环：从 VideoStreamHandler 取帧 → 发给 Flask /detect → 推给 Web 前端
    private void forwardFrames(String sn,String sessionId,Long taskId){
        log.info("开始帧转发，SN：{},sessionId：{},taskId：{}",sn,sessionId,taskId);

        // 检查是否为演示模式（Flask 端管理视频）
        String demoSessionId = demoSessionMap.get(sn);
        if (demoSessionId != null) {
            forwardDemoFrames(sn, sessionId, taskId, demoSessionId);
            return;
        }

        int totalDetection = 0;
        while (!Boolean.TRUE.equals(stopFlags.get(taskId))){
            try {
                // 先获取帧ID（消费前读取，避免新帧覆盖）
                Long frameId = videoStreamHandler.getLatestFrameId(sn);

                //从videoStreamHandler获取并消费最新帧（取后删除，避免重复发送同一帧）
                byte[] frame = videoStreamHandler.consumeLatestFrame(sn);
                if (frame == null){
                    //没有新帧，短暂等待
                    Thread.sleep(30);
                    continue;
                }

                //构建multipart/form-data请求请求发给flask端
                LinkedMultiValueMap<String,Object> body = new LinkedMultiValueMap<>();
                body.add("image",new ByteArrayResource(frame){
                    @Override
                    public @Nullable String getFilename() {
                        return "frame.jpg";
                    }
                });
                body.add("session_id",sessionId);
                body.add("confidence","0.5");
                if (frameId != null) {
                    body.add("frame_id", String.valueOf(frameId));
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

                long tBeforeFlask = System.currentTimeMillis();
                // 端到端延迟日志: LATENCY|frame_id|2|t_before_flask
                if (frameId != null) {
                    log.info("LATENCY|{}|2|{}", frameId, tBeforeFlask);
                }

                ResponseEntity<String> response = restTemplat.postForEntity(flaskBaseUrl + "/detect", entity, String.class);

                long tAfterFlask = System.currentTimeMillis();
                // 端到端延迟日志: LATENCY|frame_id|3|t_after_flask
                if (frameId != null) {
                    log.info("LATENCY|{}|3|{}", frameId, tAfterFlask);
                }

                //发送完成后再次检查停止标志，避免推送已停止任务的结果
                if (Boolean.TRUE.equals(stopFlags.get(taskId))) break;

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null){
                    //解析Flask返回的JSON
                    Map<String,Object> detectResult = objectMapper.readValue(response.getBody(),Map.class);
                    Map<String,Object> detectData = (Map<String,Object>) detectResult.get("data");
                    if (detectData != null && detectData.get("total") != null){
                        totalDetection = ((Number)detectData.get("total")).intValue();
                    }
                    //提取base64图片并解码为二进制，通过二进制协议推送给web端
                    String base64Image = detectData != null ? (String) detectData.get("annotated_image") : null;
                    if (base64Image != null) {
                        byte[] jpegData = Base64.getDecoder().decode(base64Image);
                        detectData.remove("annotated_image");
                        // 将 frame_id 写入元数据，供前端延迟追踪
                        if (frameId != null) {
                            detectData.put("_frame_id", frameId);
                        }
                        String metadataJson = objectMapper.writeValueAsString(detectResult);
                        inferenceHandler.pushBinaryInferenceResult(sn, jpegData, metadataJson);
                    } else {
                        //fallback: 没有图片时仍用文本推送
                        inferenceHandler.pushInferenceResult(sn, response.getBody());
                    }
                }
                //Flask推理本身已有耗时，不再额外sleep
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }catch (Exception e){
                log.error("帧转发循环异常：{}",e.getMessage());
                try {
                    Thread.sleep(200);
                }catch (InterruptedException ie){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("帧转发循环结束，SN：{},sessionId{},taskId{},{}",sn,sessionId,taskId,totalDetection);

        //清理映射（仅清理 stopFlags，snTaskMap 由 stopInference 负责清理）
        stopFlags.remove(taskId);
        taskSessionMap.remove(taskId);
        taskStartTime.remove(taskId);
    }

    //演示模式帧转发：调用 Flask /detect_next，Flask 直接从视频读帧+推理（无网络传帧开销）
    private void forwardDemoFrames(String sn, String sessionId, Long taskId, String demoSessionId) {
        log.info("演示推理模式开始: sn={}, demoSession={}, taskId={}", sn, demoSessionId, taskId);

        // 停止预览线程，避免两个线程同时从 Flask 读帧（不用 interrupt，避免中断 IO 导致 WebSocket 断开）
        demoStopFlags.put(sn, true);
        Thread previewThread = demoPreviewThreads.remove(sn);
        if (previewThread != null) {
            try { previewThread.join(3000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        while (!Boolean.TRUE.equals(stopFlags.get(taskId))) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", demoSessionId);
                body.put("confidence", 0.5);
                body.put("recording_session_id", sessionId);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<byte[]> resp = restTemplat.postForEntity(
                    flaskBaseUrl + "/detect_next", entity, byte[].class);

                if (Boolean.TRUE.equals(stopFlags.get(taskId))) break;

                String contentType = resp.getHeaders().getContentType() != null
                    ? resp.getHeaders().getContentType().toString() : "";

                if (contentType.contains("application/octet-stream") && resp.getBody() != null) {
                    byte[] payload = resp.getBody();
                    // 二进制协议: [4字节metaLen][metaJSON][JPEG]
                    int metaLen = ByteBuffer.wrap(payload, 0, 4).getInt();
                    byte[] jpegData = Arrays.copyOfRange(payload, 4 + metaLen, payload.length);
                    String metaJson = new String(payload, 4, metaLen, StandardCharsets.UTF_8);
                    inferenceHandler.pushBinaryInferenceResult(sn, jpegData, metaJson);
                } else {
                    // JSON 响应 = 视频结束
                    log.info("演示视频推理结束（视频播完）: sn={}, taskId={}", sn, taskId);
                    break;
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.error("演示推理帧转发异常: {}", e.getMessage());
                try { Thread.sleep(200); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("演示推理帧转发结束: sn={}, taskId={}", sn, taskId);
        stopFlags.remove(taskId);
        taskSessionMap.remove(taskId);
        taskStartTime.remove(taskId);

        // 如果不是被 stop() 主动停止的（runningTasks 中仍有该 taskId），说明视频自然播完，触发自动停止
        if (runningTasks.containsKey(taskId)) {
            autoStopBySn(sn);
        }
    }

    /**
     * 定时清理超时的推理任务（每分钟执行一次）
     * 超时时间：30分钟无活动自动停止
     */
    @Scheduled(fixedRate = 60000) // 每60秒执行一次
    public void cleanupTimeoutTasks() {
        long now = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000; // 30分钟超时

        for (Map.Entry<Long, Long> entry : taskStartTime.entrySet()) {
            Long taskId = entry.getKey();
            Long startTime = entry.getValue();

            if (now - startTime > timeout) {
                log.warn("检测到超时推理任务，自动停止: taskId={}, 运行时长={}分钟",
                    taskId, (now - startTime) / 60000);

                try {
                    // 构造停止请求
                    InferenceStopDto stopDto = new InferenceStopDto();
                    stopDto.setTaskId(taskId);

                    // 获取任务信息以获取 userId（用于 token 验证）
                    InferenceTasks task = inferenceTasksMapper.selectTasksById(taskId);
                    if (task != null) {
                        Flights flight = flightsMapper.selectFlightsById(task.getFlightId());
                        if (flight != null) {
                            // 直接调用内部停止逻辑，跳过 token 验证
                            stopTaskInternal(taskId, flight.getSn());
                        }
                    }
                } catch (Exception e) {
                    log.error("自动停止超时任务失败: taskId={}", taskId, e);
                }
            }
        }
    }

    /**
     * 内部停止任务方法（不需要 token 验证）
     */
    private void stopTaskInternal(Long taskId, String sn) {
        Thread runningThread = runningTasks.get(taskId);
        if (runningThread == null) {
            return;
        }

        // 先获取sessionId（必须在线程退出前获取）
        String sessionId = taskSessionMap.get(taskId);
        taskSessionMap.remove(taskId);
        taskStartTime.remove(taskId);

        // 停止推理线程
        stopFlags.put(taskId, true);
        runningTasks.remove(taskId);

        // 如果不是当前线程自己调用（视频自然播完时线程自己触发 autoStop），才需要 interrupt/join
        if (runningThread != Thread.currentThread()) {
            runningThread.interrupt();
            try {
                runningThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 移除 sn 映射
        if (sn != null) {
            snTaskMap.remove(sn);
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] latestFrame = videoStreamHandler.getLatestFrame(sn);
            if (latestFrame != null) {
                videoStreamHandler.pushOriginalFrameToWeb(sn, latestFrame);
            }
            // 清理 Flask 演示会话（如果有）
            demoSessionMap.remove(sn);
            demoStopFlags.remove(sn);
        }

        // 调用flask端停止推理
        if (sessionId != null) {
            try {
                Map<String, Object> resultBody = new HashMap<>();
                resultBody.put("session_id", sessionId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(resultBody, headers);
                restTemplat.postForEntity(flaskBaseUrl + "/stop_inference", entity, String.class);
            } catch (Exception e) {
                log.error("调用flask停止推理失败：{}", e.getMessage());
            }
        }

        // 更新任务状态为超时停止
        try {
            InferenceTasks task = inferenceTasksMapper.selectTasksById(taskId);
            if (task != null) {
                LocalDateTime now = LocalDateTime.now();
                task.setEndTime(now);
                task.setStatus(2); // 2表示异常结束
                task.setModificationTime(now);
                inferenceTasksMapper.updateTask(task);
                // 完成飞行记录
                if (task.getFlightId() != null) {
                    flightsMapper.completeFlightById(task.getFlightId(), now, now);
                }
            }
        } catch (Exception e) {
            log.error("更新超时任务状态失败: taskId={}", taskId, e);
        }

        stopFlags.remove(taskId);
    }

    //检查某个设备是否有正在运行的推理任务
    @Override
    public boolean hasRunningTask(String sn) {
        return snTaskMap.containsKey(sn);
    }


    // 提供给 stop 接口使用
    public ConcurrentHashMap<Long, Thread> getRunningTasks() {
        return runningTasks;
    }

    public ConcurrentHashMap<Long, String> getTaskSessionMap() {
        return taskSessionMap;
    }

    @Override
    public Map<String, Object> startOfflineInference(String token, MultipartFile video, String modelName, Double confidence) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 验证token
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();

        // 验证用户状态
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        if (user.getStatus() == 2) throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);

        // 生成sessionId
        String sessionId = "offline_" + userId + "_" + System.currentTimeMillis();

        // 保存视频到磁盘临时目录
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "skyflow_offline";
        new File(tempDir).mkdirs();
        String savedPath = tempDir + File.separator + sessionId + "_" + video.getOriginalFilename();
        File savedFile = new File(savedPath);
        video.transferTo(savedFile);
        log.info("离线推理视频已保存: {}", savedPath);

        // 创建推理任务记录
        InferenceTasks task = new InferenceTasks();
        task.setFlightId(null); // 离线任务无飞行记录
        task.setUserId(userId);
        task.setModelName(modelName);
        task.setStatus(0);
        task.setStartTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setModificationTime(LocalDateTime.now());
        inferenceTasksMapper.insertTask(task);

        Long taskId = task.getId();

        // 启动后台线程进行流式离线推理
        Thread offlineThread = new Thread(() -> {
            log.info("离线推理线程启动: taskId={}", taskId);
            HttpURLConnection conn = null;
            try {
                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("file_path", savedPath);
                requestBody.put("session_id", sessionId);
                if (confidence != null) {
                    requestBody.put("confidence", confidence);
                }
                String jsonBody = objectMapper.writeValueAsString(requestBody);

                // 调用Flask流式接口
                String urlStr = flaskBaseUrl + "offline_detect_stream";
                log.info("调用Flask流式接口: {}", urlStr);
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(0); // 无超时，因为流式响应可能持续很久

                // 发送请求
                conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
                conn.getOutputStream().flush();

                int responseCode = conn.getResponseCode();
                log.info("Flask响应状态码: {}", responseCode);

                if (responseCode != 200) {
                    log.error("Flask接口返回错误: {}", responseCode);
                    task.setStatus(2);
                    task.setEndTime(LocalDateTime.now());
                    task.setModificationTime(LocalDateTime.now());
                    inferenceTasksMapper.updateTask(task);
                    return;
                }

                // 逐行读取NDJSON流式响应
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    Map<String, Object> frameData = objectMapper.readValue(line, Map.class);
                    String type = (String) frameData.get("type");

                    if ("frame".equals(type)) {
                        // 通过WebSocket推送标注画面给前端
                        String wsMessage = objectMapper.writeValueAsString(frameData);
                        inferenceHandler.pushInferenceResult(sessionId, wsMessage);
                    } else if ("complete".equals(type)) {
                        // 推理完成，更新数据库
                        task.setStatus(1);
                        task.setEndTime(LocalDateTime.now());
                        task.setVideoPath(frameData.get("video_path").toString());
                        task.setTotalDetections(((Number) frameData.get("total_detections")).intValue());
                        task.setModificationTime(LocalDateTime.now());

                        Map<String, Object> resultData = new HashMap<>();
                        resultData.put("frame_count", frameData.get("frame_count"));
                        resultData.put("duration", frameData.get("duration"));
                        resultData.put("class_counts", frameData.get("class_counts"));
                        task.setResultData(objectMapper.writeValueAsString(resultData));

                        inferenceTasksMapper.updateTask(task);

                        // 推送完成消息给前端
                        Map<String, Object> completeMsg = new HashMap<>();
                        completeMsg.put("type", "complete");
                        completeMsg.put("taskId", taskId);
                        completeMsg.put("message", "离线推理完成");
                        inferenceHandler.pushInferenceResult(sessionId, objectMapper.writeValueAsString(completeMsg));

                        log.info("离线推理完成: taskId={}", taskId);
                    } else if ("error".equals(type)) {
                        log.error("Flask返回错误: {}", frameData.get("message"));
                        task.setStatus(2);
                        task.setEndTime(LocalDateTime.now());
                        task.setModificationTime(LocalDateTime.now());
                        inferenceTasksMapper.updateTask(task);
                    }
                }
                reader.close();

            } catch (Exception e) {
                log.error("离线推理失败: taskId={}", taskId, e);
                task.setStatus(2);
                task.setEndTime(LocalDateTime.now());
                task.setModificationTime(LocalDateTime.now());
                try {
                    inferenceTasksMapper.updateTask(task);
                } catch (Exception ex) {
                    log.error("更新任务状态失败", ex);
                }
            } finally {
                if (conn != null) conn.disconnect();
                // 删除临时文件
                try {
                    if (savedFile.exists()) savedFile.delete();
                } catch (Exception e) {
                    log.warn("删除临时文件失败: {}", savedPath);
                }
            }
        });
        offlineThread.start();

        // 立即返回任务信息和sessionId（前端用sessionId连WebSocket）
        result.put("message", "离线推理已启动");
        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("sessionId", sessionId);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> startDemo(String token, String sn) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        if (user.getStatus() == 2) throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);

        // 查询设备绑定的用户，飞行记录归属设备绑定者
        Devices devices = devicesMapper.selectDeviceBySn(sn);
        if (devices == null) throw new AppException(AppExceptionCodeMsg.DEVICE_NOT_EXIST);
        String deviceUserId = devices.getUserId();

        Flights flight = new Flights();
        flight.setUserId(deviceUserId);
        flight.setSn(sn);
        flight.setHomeLatitude(24.6189);
        flight.setHomeLongitude(118.0531);
        flight.setStartTime(LocalDateTime.now());
        flight.setStatus(0);
        flight.setCreateTime(LocalDateTime.now());
        flight.setModificationTime(LocalDateTime.now());
        flightsMapper.insertFlight(flight);

        // 启动演示模式：上传视频给 Flask，由 Flask 统一解码
        videoStreamHandler.setDemoMode(sn, true);
        InputStream videoStream = getClass().getClassLoader().getResourceAsStream("demo/demo_traffic.mp4");
        if (videoStream == null) {
            videoStreamHandler.setDemoMode(sn, false);
            throw new AppException(AppExceptionCodeMsg.DEVICE_DATA_ERROR);
        }

        // 上传视频给 Flask /upload_demo_video
        String flaskSessionId;
        double fps;
        try {
            byte[] videoBytes = videoStream.readAllBytes();
            videoStream.close();

            LinkedMultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
            uploadBody.add("video", new ByteArrayResource(videoBytes) {
                @Override
                public String getFilename() {
                    return "demo_traffic.mp4";
                }
            });
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<LinkedMultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(uploadBody, uploadHeaders);
            ResponseEntity<String> uploadResp = restTemplat.postForEntity(
                flaskBaseUrl + "/upload_demo_video", uploadEntity, String.class);

            if (uploadResp.getStatusCode() != HttpStatus.OK || uploadResp.getBody() == null) {
                videoStreamHandler.setDemoMode(sn, false);
                throw new AppException(AppExceptionCodeMsg.DEVICE_DATA_ERROR);
            }
            Map<String, Object> uploadResult = objectMapper.readValue(uploadResp.getBody(), Map.class);
            Map<String, Object> uploadData = (Map<String, Object>) uploadResult.get("data");
            flaskSessionId = (String) uploadData.get("session_id");
            fps = uploadData.get("fps") != null ? ((Number) uploadData.get("fps")).doubleValue() : 25.0;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            videoStreamHandler.setDemoMode(sn, false);
            log.error("上传演示视频到Flask失败: {}", e.getMessage());
            throw new AppException(AppExceptionCodeMsg.DEVICE_DATA_ERROR);
        }

        demoSessionMap.put(sn, flaskSessionId);

        // 启动预览帧轮询线程
        demoStopFlags.put(sn, false);
        final double finalFps = fps;
        Thread previewThread = new Thread(() -> pollDemoFrames(sn, flaskSessionId, finalFps), "demo-preview-" + sn);
        previewThread.setDaemon(true);
        previewThread.start();
        demoPreviewThreads.put(sn, previewThread);

        /* === 旧方案（DemoVideoDecoder 本地解码）保留备用 ===
        InputStream videoStream2 = getClass().getClassLoader().getResourceAsStream("demo/demo_traffic.mp4");
        DemoVideoDecoder decoder = new DemoVideoDecoder(videoStream2,
            frame -> videoStreamHandler.injectDemoFrame(sn, frame),
            () -> {
                videoStreamHandler.setDemoMode(sn, false);
                demoDecoders.remove(sn);
                autoStopBySn(sn);
            }
        );
        demoDecoders.put(sn, decoder);
        decoder.start();
        === 旧方案结束 === */

        Map<String, Object> result = new HashMap<>();
        result.put("message", "演示模式已启动");
        Map<String, Object> data = new HashMap<>();
        data.put("flightId", flight.getId());
        result.put("data", data);
        return result;
    }

    /**
     * 延迟测试模式：仅触发 Android 端播放本地视频，不设服务端 demo 模式。
     * Android 帧通过正常 WebSocket 管道传输，可测量完整链路延迟。
     */
    @Override
    public Map<String, Object> startLatencyTest(String token, String sn) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        if (user.getStatus() == 2) throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);

        // 创建设备在线状态（如果设备不在线，模拟在线状态以支持本地测试）
        if (!videoStreamHandler.isOnline(sn)) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "设备不在线，请确保Android端已连接");
            return result;
        }

        // 查询设备绑定的用户，飞行记录归属设备绑定者
        Devices devices = devicesMapper.selectDeviceBySn(sn);
        if (devices == null) throw new AppException(AppExceptionCodeMsg.DEVICE_NOT_EXIST);
        String deviceUserId = devices.getUserId();

        Flights flight = new Flights();
        flight.setUserId(deviceUserId);
        flight.setSn(sn);
        flight.setHomeLatitude(24.6189);
        flight.setHomeLongitude(118.0531);
        flight.setStartTime(LocalDateTime.now());
        flight.setStatus(0);
        flight.setCreateTime(LocalDateTime.now());
        flight.setModificationTime(LocalDateTime.now());
        flightsMapper.insertFlight(flight);

        // 通知 Android 端开始播放本地演示视频（复用 START_DEMO 指令）
        videoStreamHandler.sendCommandToDevice(sn, "{\"command\":\"START_DEMO\"}");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "延迟测试已启动，Android端开始播放本地视频");
        Map<String, Object> data = new HashMap<>();
        data.put("flightId", flight.getId());
        data.put("sn", sn);
        result.put("data", data);
        return result;
    }

    /**
     * 停止延迟测试：通知 Android 端停止播放，清理推理任务
     */
    @Override
    public Map<String, Object> stopLatencyTest(String token, String sn) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);

        // 自动停止关联的推理任务
        autoStopBySn(sn);

        // 通知 Android 端停止播放本地视频
        videoStreamHandler.sendCommandToDevice(sn, "{\"command\":\"STOP_DEMO\"}");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "延迟测试已停止");
        return result;
    }

    @Override
    public Map<String, Object> stopDemo(String token, String sn) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);

        DemoVideoDecoder decoder = demoDecoders.remove(sn);
        if (decoder != null) decoder.stop();

        // Flask 演示会话清理
        demoStopFlags.put(sn, true);
        Thread previewThread = demoPreviewThreads.remove(sn);
        if (previewThread != null) {
            try { previewThread.join(3000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String flaskSessionId = demoSessionMap.remove(sn);
        if (flaskSessionId != null) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", flaskSessionId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                restTemplat.postForEntity(flaskBaseUrl + "/stop_demo_video", entity, String.class);
            } catch (Exception e) {
                log.error("调用Flask停止演示视频失败: {}", e.getMessage());
            }
        }
        demoStopFlags.remove(sn);
        videoStreamHandler.setDemoMode(sn, false);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "演示已停止");
        return result;
    }

    @Override
    public void autoStopBySn(String sn) {
        Long taskId = snTaskMap.get(sn);
        if (taskId != null) {
            log.info("演示视频播放结束，自动停止推理: sn={}, taskId={}", sn, taskId);
            stopTaskInternal(taskId, sn);
        }
        DemoVideoDecoder decoder = demoDecoders.remove(sn);
        if (decoder != null) decoder.stop();

        // Flask 演示会话清理
        demoStopFlags.put(sn, true);
        Thread previewThread2 = demoPreviewThreads.remove(sn);
        if (previewThread2 != null) {
            try { previewThread2.join(3000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String flaskSessionId = demoSessionMap.remove(sn);
        if (flaskSessionId != null) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", flaskSessionId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                restTemplat.postForEntity(flaskBaseUrl + "/stop_demo_video", entity, String.class);
            } catch (Exception e) {
                log.error("调用Flask停止演示视频失败: {}", e.getMessage());
            }
        }
        demoStopFlags.remove(sn);
        videoStreamHandler.setDemoMode(sn, false);

        String notification = "{\"type\":\"auto_stopped\",\"taskId\":" + (taskId != null ? taskId : "null") + ",\"reason\":\"demo_video_ended\"}";
        inferenceHandler.pushInferenceResult(sn, notification);
    }
}

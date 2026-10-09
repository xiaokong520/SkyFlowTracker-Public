"""
SkyFlowTracker Flask 推理服务
仅负责 YOLOv12 车流量检测推理，业务逻辑由 SpringBoot 处理
"""

from flask import Flask, request, jsonify, Response
from ultralytics import YOLO
from collections import deque
import queue
import cv2
import numpy as np
import time
import os
import json
import logging
import threading
import base64
import struct

app = Flask(__name__)

# 日志配置
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 模型配置
MODEL_PATH = os.getenv('YOLO_MODEL_PATH', 'models/yolo12m.pt')
CONFIDENCE_THRESHOLD = float(os.getenv('CONFIDENCE_THRESHOLD', '0.5'))
# 推理输入分辨率（越小越快，640 是精度和速度的平衡点）
INFERENCE_IMGSZ = int(os.getenv('INFERENCE_IMGSZ', '640'))
# 启用 FP16 半精度推理（RTX 系列显卡 FP16 性能约为 FP32 的两倍）
USE_HALF = os.getenv('USE_HALF', 'true').lower() == 'true'

# 车辆相关类别（COCO 数据集）
VEHICLE_CLASSES = {
    2: 'car',       # 小汽车
    5: 'bus',        # 公交车
    7: 'truck',      # 卡车
}

# 视频输出配置
VIDEO_OUTPUT_DIR = os.getenv('VIDEO_OUTPUT_DIR', os.path.join('output', 'videos'))
# 存入数据库的相对路径前缀（对应 nginx 静态资源路径）
VIDEO_RELATIVE_PREFIX = 'videos/output'

# ==================== 演示视频会话管理 ====================
# session_id -> { 'cap': VideoCapture, 'fps': float, 'temp_path': str, 'lock': Lock }
demo_sessions = {}
demo_sessions_lock = threading.Lock()

# ==================== FPS 计算器 ====================
fps_timestamps = deque(maxlen=30)  # 保留最近 30 帧的时间戳，计算平均 FPS

# ==================== 视频录制会话管理 ====================
# 结构: { session_id: { 'writer': cv2.VideoWriter, 'path': str, 'frame_count': int, 'lock': Lock } }
recording_sessions = {}
sessions_lock = threading.Lock()

# ==================== 异步视频写入队列 ====================
# 视频写入放到后台线程，避免阻塞推理主线程
video_write_queue = queue.Queue(maxsize=60)


def _video_write_worker():
    """后台线程：从队列取帧写入视频文件"""
    while True:
        try:
            item = video_write_queue.get()
            if item is None:
                video_write_queue.task_done()
                break
            session, frame, detections_for_count = item
            with session['lock']:
                session['writer'].write(frame)
                session['frame_count'] += 1
                for det in detections_for_count:
                    tid = det.get('track_id', -1)
                    if tid != -1 and tid not in session['seen_track_ids']:
                        session['seen_track_ids'].add(tid)
                        cls = det.get('class', 'unknown')
                        session['class_counts'][cls] = session['class_counts'].get(cls, 0) + 1
                        session['total_detections'] += 1
            video_write_queue.task_done()
        except Exception as e:
            logger.error(f"视频写入线程异常: {e}")
            video_write_queue.task_done()


_video_writer_thread = threading.Thread(target=_video_write_worker, daemon=True)
_video_writer_thread.start()


def load_model():
    """加载 YOLO 模型并预热"""
    global model
    if not os.path.exists(MODEL_PATH):
        logger.error(f"模型文件不存在: {MODEL_PATH}")
        return False
    try:
        model = YOLO(MODEL_PATH)
        logger.info(f"模型加载成功: {MODEL_PATH}")
        # 预热：首次推理会触发 CUDA kernel 编译，比较慢
        dummy = np.zeros((640, 640, 3), dtype=np.uint8)
        model.predict(dummy, imgsz=INFERENCE_IMGSZ, half=USE_HALF, verbose=False)
        logger.info(f"模型预热完成 (imgsz={INFERENCE_IMGSZ}, half={USE_HALF})")
        logger.info(f"使用设备: {model.device}")
        return True
    except Exception as e:
        logger.error(f"模型加载失败: {e}")
        return False


@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({
        'status': 'ok',
        'model_loaded': model is not None,
        'model_path': MODEL_PATH
    })


def _remux_with_actual_fps(session, duration):
    """
    用实际帧率重新封装视频文件。
    VideoWriter 创建时 fps 是预估值，实际写入帧率可能远低于该值，
    导致播放时长远短于真实录制时长（看起来像快进）。
    这里读出所有帧，用 实际帧数/实际时长 作为 fps 重写视频。
    """
    if duration <= 0 or session['frame_count'] <= 0:
        return

    actual_fps = session['frame_count'] / duration
    declared_fps = session['video_fps']

    # 如果实际帧率和声明帧率接近（误差 <20%），不需要重新封装
    if abs(actual_fps - declared_fps) / declared_fps < 0.2:
        return

    # 根据相对路径还原绝对路径
    relative_path = session['path']
    filename = os.path.basename(relative_path)
    original_path = os.path.join(VIDEO_OUTPUT_DIR, filename)

    if not os.path.exists(original_path):
        logger.warning(f"重封装失败，原始视频不存在: {original_path}")
        return

    temp_path = original_path + '.tmp.mp4'
    logger.info(f"重封装视频: declared_fps={declared_fps}, actual_fps={actual_fps:.2f}, frames={session['frame_count']}, duration={duration}s")

    try:
        cap = cv2.VideoCapture(original_path)
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fourcc = cv2.VideoWriter_fourcc(*'avc1')
        writer = cv2.VideoWriter(temp_path, fourcc, actual_fps, (w, h))

        while True:
            ret, frame = cap.read()
            if not ret:
                break
            writer.write(frame)

        cap.release()
        writer.release()

        # 替换原文件
        os.replace(temp_path, original_path)
        logger.info(f"重封装完成: {original_path}, actual_fps={actual_fps:.2f}")
    except Exception as e:
        logger.error(f"重封装视频失败: {e}")
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass


@app.route('/stop_inference', methods=['POST'])
def stop_inference():
    """
    停止推理 + 录制视频
    
    请求：JSON
    - session_id: 会话ID
    
    返回：JSON
    - session_id: 会话ID
    - video_path: 视频文件路径
    - frame_count: 总帧数
    - total_detections: 累计检测到的车辆总数
    - duration: 录制时长（秒）
    """
    data = request.get_json()
    if not data or 'session_id' not in data:
        return jsonify({'code': 0, 'message': '缺少 session_id'}), 400

    session_id = data['session_id']

    with sessions_lock:
        session = recording_sessions.pop(session_id, None)

    if session is None:
        return jsonify({'code': 0, 'message': f'会话 {session_id} 不存在'}), 404

    # 等待异步视频写入队列排空，确保所有帧都写入完成
    video_write_queue.join()

    # 释放 VideoWriter
    with session['lock']:
        session['writer'].release()

    # 重置跟踪器状态，使下次推理的跟踪ID从1开始
    if model is not None:
        model.predictor = None

    duration = round(time.time() - session['start_time'], 1)

    # 用实际帧率重新封装视频，避免播放速度与录制时长不一致
    _remux_with_actual_fps(session, duration)

    logger.info(f"停止推理+录制: session={session_id}, frames={session['frame_count']}, total_detections={session['total_detections']}, duration={duration}s")

    return jsonify({
        'code': 1,
        'data': {
            'session_id': session_id,
            'video_path': session['path'],
            'frame_count': session['frame_count'],
            'total_detections': session['total_detections'],
            'class_counts': session['class_counts'],
            'duration': duration
        }
    })


@app.route('/detect', methods=['POST'])
def detect():
    """
    车流量检测接口（实时推理）
    
    请求：multipart/form-data
    - image: JPEG/PNG 图片文件
    - confidence: (可选) 置信度阈值，默认 0.5
    - session_id: (可选) 推理会话ID，首次提供时自动创建录制会话
    
    返回：JSON
    - detections: 检测结果列表
    - summary: 各类别计数
    - total: 本帧检测到的车辆数
    - inference_time_ms: 推理耗时（毫秒）
    - annotated_image: 标注后的图片（base64）
    """
    if model is None:
        return jsonify({'code': 0, 'message': '模型未加载'}), 503

    # 检查是否有图片
    if 'image' not in request.files:
        return jsonify({'code': 0, 'message': '缺少 image 参数'}), 400

    file = request.files['image']
    if file.filename == '':
        return jsonify({'code': 0, 'message': '图片文件为空'}), 400

    # 读取参数
    conf = float(request.form.get('confidence', CONFIDENCE_THRESHOLD))
    session_id = request.form.get('session_id')
    frame_id = request.form.get('frame_id')  # 端到端延迟追踪用

    try:
        # 读取图片
        img_bytes = file.read()
        img_array = np.frombuffer(img_bytes, dtype=np.uint8)
        img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)

        if img is None:
            return jsonify({'code': 0, 'message': '图片解码失败'}), 400

        # 推理 + 跟踪（只检测车辆类别，启用 BoT-SORT 跟踪）
        vehicle_class_ids = list(VEHICLE_CLASSES.keys())
        start_time = time.time()
        results = model.track(
            img, conf=conf, classes=vehicle_class_ids,
            persist=True, verbose=False,
            imgsz=INFERENCE_IMGSZ, half=USE_HALF
        )
        inference_time = (time.time() - start_time) * 1000

        # 解析检测结果（含跟踪 ID）
        detections = []
        summary = {}

        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for box in boxes:
                cls_id = int(box.cls[0])
                if cls_id not in VEHICLE_CLASSES:
                    continue

                cls_name = VEHICLE_CLASSES[cls_id]
                confidence = float(box.conf[0])
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                # 获取跟踪 ID（首帧可能还没分配 ID）
                track_id = int(box.id[0]) if box.id is not None else -1

                detections.append({
                    'class': cls_name,
                    'track_id': track_id,
                    'confidence': round(confidence, 3),
                    'bbox': {
                        'x1': round(x1, 1),
                        'y1': round(y1, 1),
                        'x2': round(x2, 1),
                        'y2': round(y2, 1)
                    }
                })
                summary[cls_name] = summary.get(cls_name, 0) + 1

        # 绘制标注框
        annotated_img = results[0].plot()

        # 计算实时 FPS（基于最近 30 帧的平均值）
        now = time.time()
        fps_timestamps.append(now)
        if len(fps_timestamps) >= 2:
            elapsed = fps_timestamps[-1] - fps_timestamps[0]
            current_fps = (len(fps_timestamps) - 1) / elapsed if elapsed > 0 else 0
        else:
            current_fps = 0

        # 在画面左上角绘制 FPS 和检测数量
        fps_text = f'FPS: {current_fps:.1f}'
        count_text = f'Vehicles: {len(detections)}'
        cv2.putText(annotated_img, fps_text, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)
        cv2.putText(annotated_img, count_text, (10, 65), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)

        # 如果提供了 session_id，自动创建/写入视频
        if session_id:
            with sessions_lock:
                session = recording_sessions.get(session_id)
                
                # 首次调用：自动创建录制会话
                if session is None:
                    os.makedirs(VIDEO_OUTPUT_DIR, exist_ok=True)
                    video_filename = f'{session_id}_{int(time.time())}.mp4'
                    video_path = os.path.join(VIDEO_OUTPUT_DIR, video_filename)
                    relative_path = f'{VIDEO_RELATIVE_PREFIX}/{video_filename}'
                    
                    # 使用当前帧的尺寸
                    h, w = annotated_img.shape[:2]
                    fourcc = cv2.VideoWriter_fourcc(*'avc1')
                    writer = cv2.VideoWriter(video_path, fourcc, 10, (w, h))
                    
                    if not writer.isOpened():
                        logger.error(f"创建视频写入器失败: {video_path}")
                    else:
                        session = {
                            'writer': writer,
                            'path': relative_path,
                            'frame_count': 0,
                            'width': w,
                            'height': h,
                            'lock': threading.Lock(),
                            'start_time': time.time(),
                            'total_detections': 0,
                            'class_counts': {},
                            'seen_track_ids': set(),
                            'video_fps': 10
                        }
                        recording_sessions[session_id] = session
                        logger.info(f"自动创建录制会话: session={session_id}, path={video_path}, {w}x{h}")
            
            # 异步写入视频帧（不阻塞推理主线程）
            if session:
                frame = annotated_img.copy()
                h, w = frame.shape[:2]
                if w != session['width'] or h != session['height']:
                    frame = cv2.resize(frame, (session['width'], session['height']))
                try:
                    video_write_queue.put_nowait((session, frame, list(detections)))
                except queue.Full:
                    pass  # 队列满时丢帧，优先保证推理速度

        # 将标注图片编码为 base64，供前端实时显示
        _, img_encoded = cv2.imencode('.jpg', annotated_img, [cv2.IMWRITE_JPEG_QUALITY, 80])
        img_base64 = base64.b64encode(img_encoded).decode('utf-8')

        return jsonify({
            'code': 1,
            'data': {
                'detections': detections,
                'summary': summary,
                'total': len(detections),
                'inference_time_ms': round(inference_time, 1),
                'annotated_image': img_base64,
                'frame_id': frame_id  # 回传帧ID，供延迟追踪
            }
        })

    except Exception as e:
        logger.error(f"推理异常: {e}")
        return jsonify({'code': 0, 'message': f'推理失败: {str(e)}'}), 500


@app.route('/offline_detect_stream', methods=['POST'])
def offline_detect_stream():
    """
    流式离线视频推理接口

    请求：JSON
    - file_path: 视频文件在磁盘上的绝对路径
    - confidence: (可选) 置信度阈值，默认 0.5
    - session_id: (可选) 会话ID

    返回：NDJSON 流
    - 每帧: {"type":"frame", "annotated_image": base64, "frame_number": N, "total_frames": T, "detections": [...]}
    - 结束: {"type":"complete", "video_path": ..., "total_detections": ..., ...}
    """
    if model is None:
        return jsonify({'code': 0, 'message': '模型未加载'}), 503

    data = request.get_json()
    if not data or 'file_path' not in data:
        return jsonify({'code': 0, 'message': '缺少 file_path 参数'}), 400

    file_path = data['file_path']
    if not os.path.exists(file_path):
        return jsonify({'code': 0, 'message': f'文件不存在: {file_path}'}), 400

    conf = float(data.get('confidence', CONFIDENCE_THRESHOLD))
    session_id = data.get('session_id', f'offline_{int(time.time())}')

    def generate():
        try:
            cap = cv2.VideoCapture(file_path)
            if not cap.isOpened():
                yield json.dumps({'type': 'error', 'message': '视频打开失败'}) + '\n'
                return

            fps = int(cap.get(cv2.CAP_PROP_FPS)) or 10
            width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

            # 创建输出视频
            os.makedirs(VIDEO_OUTPUT_DIR, exist_ok=True)
            output_filename = f'{session_id}_{int(time.time())}.mp4'
            output_path = os.path.join(VIDEO_OUTPUT_DIR, output_filename)
            fourcc = cv2.VideoWriter_fourcc(*'avc1')
            writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

            if not writer.isOpened():
                cap.release()
                yield json.dumps({'type': 'error', 'message': '输出视频创建失败'}) + '\n'
                return

            frame_count = 0
            total_detections = 0
            class_counts = {}
            seen_track_ids = set()
            vehicle_class_ids = list(VEHICLE_CLASSES.keys())
            start_time = time.time()

            while True:
                ret, frame = cap.read()
                if not ret:
                    break

                # YOLO 推理 + 跟踪
                results = model.track(
                    frame, conf=conf, classes=vehicle_class_ids,
                    persist=True, verbose=False,
                    imgsz=INFERENCE_IMGSZ, half=USE_HALF
                )

                # 解析检测结果
                detections = []
                annotated = frame.copy()
                for result in results:
                    boxes = result.boxes
                    if boxes is None:
                        continue
                    for box in boxes:
                        cls_id = int(box.cls[0])
                        if cls_id not in VEHICLE_CLASSES:
                            continue

                        cls_name = VEHICLE_CLASSES[cls_id]
                        confidence = float(box.conf[0])
                        x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
                        track_id = int(box.id[0]) if box.id is not None else -1

                        if track_id != -1 and track_id not in seen_track_ids:
                            seen_track_ids.add(track_id)
                            class_counts[cls_name] = class_counts.get(cls_name, 0) + 1
                            total_detections += 1

                        color = (0, 255, 0)
                        cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 2)
                        label = f'{cls_name} {confidence:.2f}'
                        if track_id != -1:
                            label += f' ID:{track_id}'
                        cv2.putText(annotated, label, (x1, y1 - 10),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)

                        detections.append({
                            'class': cls_name,
                            'track_id': track_id,
                            'confidence': round(confidence, 3)
                        })

                cv2.putText(annotated, f'Vehicles: {len(seen_track_ids)}', (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)

                writer.write(annotated)
                frame_count += 1

                # 每帧都编码为 base64 推送给前端（每3帧推送一次，减少带宽）
                if frame_count % 3 == 0 or frame_count == 1:
                    _, img_encoded = cv2.imencode('.jpg', annotated, [cv2.IMWRITE_JPEG_QUALITY, 70])
                    img_base64 = base64.b64encode(img_encoded).decode('utf-8')

                    frame_data = {
                        'type': 'frame',
                        'annotated_image': img_base64,
                        'frame_number': frame_count,
                        'total_frames': total_frames,
                        'detections': detections,
                        'total_detections': total_detections,
                        'fps': fps
                    }
                    yield json.dumps(frame_data) + '\n'

            cap.release()
            writer.release()

            if model is not None:
                model.predictor = None

            duration = round(time.time() - start_time, 1)
            relative_path = f'{VIDEO_RELATIVE_PREFIX}/{output_filename}'
            logger.info(f"流式离线推理完成: frames={frame_count}, detections={total_detections}, duration={duration}s")

            complete_data = {
                'type': 'complete',
                'video_path': relative_path,
                'frame_count': frame_count,
                'total_detections': total_detections,
                'class_counts': class_counts,
                'duration': duration
            }
            yield json.dumps(complete_data) + '\n'

        except Exception as e:
            logger.error(f"流式离线推理异常: {e}")
            yield json.dumps({'type': 'error', 'message': str(e)}) + '\n'

    return Response(generate(), mimetype='application/x-ndjson')


@app.route('/upload_demo_video', methods=['POST'])
def upload_demo_video():
    """
    上传演示视频，Flask 用 cv2.VideoCapture 打开并管理

    请求：multipart/form-data
    - video: 视频文件

    返回：JSON
    - session_id: 演示会话ID
    - fps: 视频帧率
    """
    if 'video' not in request.files:
        return jsonify({'code': 0, 'message': '缺少 video 参数'}), 400

    file = request.files['video']
    session_id = f"demo_{int(time.time() * 1000)}"

    temp_dir = os.path.join(os.path.dirname(__file__), 'temp')
    os.makedirs(temp_dir, exist_ok=True)
    temp_path = os.path.join(temp_dir, f'{session_id}.mp4')
    file.save(temp_path)

    cap = cv2.VideoCapture(temp_path)
    if not cap.isOpened():
        os.remove(temp_path)
        return jsonify({'code': 0, 'message': '视频文件打开失败'}), 400

    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    with demo_sessions_lock:
        demo_sessions[session_id] = {
            'cap': cap,
            'fps': fps,
            'total_frames': total_frames,
            'temp_path': temp_path,
            'lock': threading.Lock(),
            'last_read_time': time.time(),
        }

    logger.info(f"演示视频上传成功: session={session_id}, fps={fps}, frames={total_frames}")
    return jsonify({'code': 1, 'data': {'session_id': session_id, 'fps': fps, 'total_frames': total_frames}})


@app.route('/demo_frame', methods=['GET'])
def demo_frame():
    """
    获取演示视频的下一帧（原始画质）

    参数：session_id (query string)

    返回：
    - 成功: image/jpeg 二进制
    - 视频结束: JSON {"code": 2, "msg": "video_ended"}
    """
    session_id = request.args.get('session_id')
    if not session_id:
        return jsonify({'code': 0, 'message': '缺少 session_id'}), 400

    with demo_sessions_lock:
        session = demo_sessions.get(session_id)
    if not session:
        return jsonify({'code': 0, 'message': 'session not found'}), 404

    with session['lock']:
        # 跳帧：根据时间差保持视频实时播放速度
        now_ts = time.time()
        last_ts = session.get('last_read_time', now_ts)
        elapsed = now_ts - last_ts
        fps = session.get('fps', 25.0)
        frames_to_skip = max(0, int(elapsed * fps) - 1)
        for _ in range(frames_to_skip):
            ret = session['cap'].grab()
            if not ret:
                return jsonify({'code': 2, 'msg': 'video_ended'})
        ret, frame = session['cap'].read()
        session['last_read_time'] = time.time()

    if not ret:
        return jsonify({'code': 2, 'msg': 'video_ended'})

    _, img_encoded = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 90])
    return Response(img_encoded.tobytes(), mimetype='image/jpeg')


@app.route('/detect_next', methods=['POST'])
def detect_next():
    """
    从演示视频读取下一帧并执行 YOLO 推理

    请求：JSON
    - session_id: 演示会话ID
    - confidence: (可选) 置信度阈值
    - recording_session_id: (可选) 录制会话ID，用于同时录制推理视频

    返回：
    - 成功: application/octet-stream 二进制协议 [4字节metaLen][metaJSON][JPEG]
    - 视频结束: JSON {"code": 2, "msg": "video_ended"}
    """
    if model is None:
        return jsonify({'code': 0, 'message': '模型未加载'}), 503

    data = request.get_json()
    if not data or 'session_id' not in data:
        return jsonify({'code': 0, 'message': '缺少 session_id'}), 400

    session_id = data['session_id']
    conf = float(data.get('confidence', CONFIDENCE_THRESHOLD))
    recording_session_id = data.get('recording_session_id')

    with demo_sessions_lock:
        session = demo_sessions.get(session_id)
    if not session:
        return jsonify({'code': 0, 'message': 'session not found'}), 404

    with session['lock']:
        # 跳帧逻辑：根据上次调用的时间差和视频帧率，跳过应跳的帧数以保持实时播放速度
        now_ts = time.time()
        last_ts = session.get('last_read_time', now_ts)
        elapsed = now_ts - last_ts
        fps = session.get('fps', 25.0)
        frames_to_skip = max(0, int(elapsed * fps) - 1)
        for _ in range(frames_to_skip):
            ret = session['cap'].grab()
            if not ret:
                return jsonify({'code': 2, 'msg': 'video_ended'})
        ret, frame = session['cap'].read()
        session['last_read_time'] = time.time()

    if not ret:
        return jsonify({'code': 2, 'msg': 'video_ended'})

    # YOLO 推理 + 跟踪
    vehicle_class_ids = list(VEHICLE_CLASSES.keys())
    results = model.track(
        frame, conf=conf, classes=vehicle_class_ids,
        persist=True, verbose=False,
        imgsz=INFERENCE_IMGSZ, half=USE_HALF
    )

    # 解析检测结果
    detections = []
    summary = {}
    for result in results:
        boxes = result.boxes
        if boxes is None:
            continue
        for box in boxes:
            cls_id = int(box.cls[0])
            if cls_id not in VEHICLE_CLASSES:
                continue
            cls_name = VEHICLE_CLASSES[cls_id]
            confidence = float(box.conf[0])
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            track_id = int(box.id[0]) if box.id is not None else -1
            detections.append({
                'class': cls_name,
                'track_id': track_id,
                'confidence': round(confidence, 3),
                'bbox': {'x1': round(x1, 1), 'y1': round(y1, 1), 'x2': round(x2, 1), 'y2': round(y2, 1)}
            })
            summary[cls_name] = summary.get(cls_name, 0) + 1

    # 绘制标注帧
    annotated_img = results[0].plot()

    # 绘制 FPS 和检测数量
    now = time.time()
    fps_timestamps.append(now)
    if len(fps_timestamps) >= 2:
        elapsed = fps_timestamps[-1] - fps_timestamps[0]
        current_fps = (len(fps_timestamps) - 1) / elapsed if elapsed > 0 else 0
    else:
        current_fps = 0
    cv2.putText(annotated_img, f'FPS: {current_fps:.1f}', (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)
    cv2.putText(annotated_img, f'Vehicles: {len(detections)}', (10, 65), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2)

    # 录制视频（复用现有 recording_sessions 逻辑）
    if recording_session_id:
        with sessions_lock:
            rec_session = recording_sessions.get(recording_session_id)
            if rec_session is None:
                os.makedirs(VIDEO_OUTPUT_DIR, exist_ok=True)
                video_filename = f'{recording_session_id}_{int(time.time())}.mp4'
                video_path = os.path.join(VIDEO_OUTPUT_DIR, video_filename)
                relative_path = f'{VIDEO_RELATIVE_PREFIX}/{video_filename}'
                h, w = annotated_img.shape[:2]
                fourcc = cv2.VideoWriter_fourcc(*'avc1')
                writer = cv2.VideoWriter(video_path, fourcc, 10, (w, h))
                if writer.isOpened():
                    rec_session = {
                        'writer': writer, 'path': relative_path,
                        'frame_count': 0, 'width': w, 'height': h,
                        'lock': threading.Lock(), 'start_time': time.time(),
                        'total_detections': 0, 'class_counts': {},
                        'seen_track_ids': set(), 'video_fps': 10
                    }
                    recording_sessions[recording_session_id] = rec_session
        if rec_session:
            rec_frame = annotated_img.copy()
            h, w = rec_frame.shape[:2]
            if w != rec_session['width'] or h != rec_session['height']:
                rec_frame = cv2.resize(rec_frame, (rec_session['width'], rec_session['height']))
            try:
                video_write_queue.put_nowait((rec_session, rec_frame, list(detections)))
            except queue.Full:
                pass

    # 编码标注帧为 JPEG
    _, img_encoded = cv2.imencode('.jpg', annotated_img, [cv2.IMWRITE_JPEG_QUALITY, 80])
    jpeg_bytes = img_encoded.tobytes()

    # 构建二进制协议: [4字节metaLen][metaJSON][JPEG]
    meta = json.dumps({
        'code': 1,
        'data': {
            'detections': detections,
            'summary': summary,
            'total': len(detections)
        }
    })
    meta_bytes = meta.encode('utf-8')
    payload = struct.pack('>I', len(meta_bytes)) + meta_bytes + jpeg_bytes

    return Response(payload, mimetype='application/octet-stream')


@app.route('/stop_demo_video', methods=['POST'])
def stop_demo_video():
    """
    停止演示视频，释放资源

    请求：JSON
    - session_id: 演示会话ID
    """
    data = request.get_json()
    if not data or 'session_id' not in data:
        return jsonify({'code': 0, 'message': '缺少 session_id'}), 400

    session_id = data['session_id']

    with demo_sessions_lock:
        session = demo_sessions.pop(session_id, None)

    if session:
        with session['lock']:
            session['cap'].release()
        if os.path.exists(session['temp_path']):
            try:
                os.remove(session['temp_path'])
            except OSError:
                pass
        # 重置跟踪器状态
        if model is not None:
            model.predictor = None
        logger.info(f"演示视频已停止: session={session_id}")

    return jsonify({'code': 1, 'data': {'message': 'stopped'}})


if __name__ == '__main__':
    load_model()

    host = os.getenv('FLASK_HOST', '0.0.0.0')
    port = int(os.getenv('FLASK_PORT', '5000'))
    debug = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'

    logger.info(f"Flask 推理服务启动: {host}:{port}")
    app.run(host=host, port=port, debug=debug)

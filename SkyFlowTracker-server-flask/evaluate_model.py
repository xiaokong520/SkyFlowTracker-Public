"""
YOLOv12 模型精度验证脚本（仅 car / bus / truck 三类）

用法：
    python evaluate_model.py --model models/yolo12m.pt --dataset datasets/val_dataset --imgsz 640

验证集目录结构（YOLO 格式）：
    val_dataset/
    ├── images/          # 验证图片
    │   ├── 001.jpg
    │   └── ...
    └── labels/          # YOLO 标注文件（.txt，每行: class_id cx cy w h，归一化坐标）
        ├── 001.txt
        └── ...

输出：终端打印各类别 Precision / Recall / AP50 / mAP50，并保存 JSON 结果文件。
"""

import os
import sys
import json
import argparse
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
import cv2
from ultralytics import YOLO


# 关注的目标类别名称（从 classes.txt 或 model.names 中按名称匹配 ID）
# 值相同表示合并为同一类别
TARGET_ALIASES = {
    'car': 'car',
    'bus': 'bus',
    'truck': 'truck',
    'van': 'truck',    # van 归类为 truck
}

# 评估 IoU 阈值
IOU_THRESHOLD = 0.5


def parse_args():
    parser = argparse.ArgumentParser(description='YOLOv12 车辆检测精度验证')
    parser.add_argument('--model', type=str, required=True, help='YOLO 模型文件路径')
    parser.add_argument('--dataset', type=str, required=True, help='验证集根目录（含 images/ 和 labels/）')
    parser.add_argument('--imgsz', type=int, default=640, help='推理输入尺寸')
    parser.add_argument('--conf', type=float, default=0.001, help='置信度阈值（默认0.001，保留所有框用于计算mAP）')
    parser.add_argument('--device', type=str, default='cuda', help='推理设备: cuda / cpu / 0,1,2...')
    parser.add_argument('--output', type=str, default=None, help='结果输出JSON路径，默认保存到 output/val/')
    return parser.parse_args()


def load_gt_mapping(labels_dir):
    """
    从 classes.txt 读取数据集类别ID到目标名称的映射。
    数据集标签文件中的 class_id 是 classes.txt 的行索引（从0开始）。
    返回 {dataset_class_id: target_name}
    """
    labels_dir = Path(labels_dir)
    for base in [labels_dir, labels_dir.parent]:
        classes_file = base / 'classes.txt'
        if classes_file.exists():
            names = [line.strip() for line in open(classes_file, encoding='utf-8') if line.strip()]
            mapping = {}
            for i, name in enumerate(names):
                alias = name.lower()
                if alias in TARGET_ALIASES:
                    mapping[i] = TARGET_ALIASES[alias]
            return mapping
    return {}


def load_model_mapping(model):
    """
    从模型类别名称推导模型输出ID到目标名称的映射。
    模型输出的 cls_id 由训练时的 data.yaml 决定（如 COCO 预训练模型使用 COCO ID）。
    返回 {model_class_id: target_name}
    """
    mapping = {}
    for cls_id, name in model.names.items():
        alias = name.lower()
        if alias in TARGET_ALIASES:
            mapping[int(cls_id)] = TARGET_ALIASES[alias]
    return mapping


def load_ground_truth(label_dir, gt_mapping):
    """加载所有 YOLO 格式标注文件，返回 {stem: [(cls_name, cx, cy, w, h), ...]}"""
    gt = {}
    label_dir = Path(label_dir)
    for txt_path in label_dir.glob('*.txt'):
        stem = txt_path.stem
        if stem == 'classes':
            continue
        boxes = []
        with open(txt_path, 'r') as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) < 5:
                    continue
                cls_id = int(parts[0])
                if cls_id in gt_mapping:
                    cx, cy, w, h = map(float, parts[1:5])
                    boxes.append((gt_mapping[cls_id], cx, cy, w, h))
        gt[stem] = boxes
    return gt


def xywh_to_xyxy(cx, cy, w, h, img_w, img_h):
    """YOLO 归一化中心坐标 -> 像素 xyxy"""
    x1 = (cx - w / 2) * img_w
    y1 = (cy - h / 2) * img_h
    x2 = (cx + w / 2) * img_w
    y2 = (cy + h / 2) * img_h
    return x1, y1, x2, y2


def compute_ap(precisions, recalls):
    """计算 AP (Average Precision)，使用 11-point 插值法"""
    recalls = np.array(recalls)
    precisions = np.array(precisions)
    indices = np.argsort(recalls)
    recalls = recalls[indices]
    precisions = precisions[indices]

    ap = 0.0
    for t in np.linspace(0, 1, 11):
        mask = recalls >= t
        if np.any(mask):
            ap += np.max(precisions[mask]) / 11.0
    return ap


def box_iou_batch(boxes_a, boxes_b):
    """批量计算两组 xyxy 框的 IoU 矩阵 (numpy 向量化)"""
    boxes_a = np.array(boxes_a, dtype=np.float32)  # [N, 4]
    boxes_b = np.array(boxes_b, dtype=np.float32)  # [M, 4]
    if len(boxes_a) == 0 or len(boxes_b) == 0:
        return np.zeros((len(boxes_a), len(boxes_b)))

    inter_x1 = np.maximum(boxes_a[:, 0:1], boxes_b[:, 0])
    inter_y1 = np.maximum(boxes_a[:, 1:2], boxes_b[:, 1])
    inter_x2 = np.minimum(boxes_a[:, 2:3], boxes_b[:, 2])
    inter_y2 = np.minimum(boxes_a[:, 3:4], boxes_b[:, 3])
    inter_area = np.maximum(0, inter_x2 - inter_x1) * np.maximum(0, inter_y2 - inter_y1)

    area_a = (boxes_a[:, 2] - boxes_a[:, 0]) * (boxes_a[:, 3] - boxes_a[:, 1])
    area_b = (boxes_b[:, 2] - boxes_b[:, 0]) * (boxes_b[:, 3] - boxes_b[:, 1])

    union = area_a[:, None] + area_b - inter_area
    return inter_area / (union + 1e-10)


CHUNK_SIZE = 2000  # 分块大小，避免超大 IoU 矩阵导致内存溢出


def evaluate_class(predictions, ground_truths):
    """
    对单个类别计算 Precision / Recall / AP（分块向量化，避免 OOM）
    predictions: [(conf, x1, y1, x2, y2), ...]
    ground_truths: [(x1, y1, x2, y2), ...]
    """
    predictions = sorted(predictions, key=lambda x: x[0], reverse=True)
    num_gt = len(ground_truths)
    num_pred = len(predictions)

    if num_gt == 0:
        return {'precision': 0, 'recall': 0, 'ap': 0, 'tp': 0, 'fp': 0, 'fn': 0,
                'num_gt': 0, 'num_pred': num_pred}

    if num_pred == 0:
        return {'precision': 0, 'recall': 0, 'ap': 0, 'tp': 0, 'fp': 0,
                'fn': num_gt, 'num_gt': num_gt, 'num_pred': 0}

    pred_boxes = np.array([[p[1], p[2], p[3], p[4]] for p in predictions], dtype=np.float32)
    gt_boxes = np.array([[g[0], g[1], g[2], g[3]] for g in ground_truths], dtype=np.float32)

    tp_list = np.zeros(num_pred, dtype=np.int32)
    fp_list = np.zeros(num_pred, dtype=np.int32)
    gt_matched = np.zeros(num_gt, dtype=np.bool_)

    for start in range(0, num_pred, CHUNK_SIZE):
        end = min(start + CHUNK_SIZE, num_pred)
        chunk_boxes = pred_boxes[start:end]

        # 只取尚未匹配的 GT
        unmatched_idx = np.where(~gt_matched)[0]
        if len(unmatched_idx) == 0:
            fp_list[start:] = 1
            break

        unmatched_gt = gt_boxes[unmatched_idx]
        iou_chunk = box_iou_batch(chunk_boxes, unmatched_gt)
        iou_chunk[iou_chunk < IOU_THRESHOLD] = 0

        for i in range(len(chunk_boxes)):
            row = iou_chunk[i]
            best_local = np.argmax(row)
            if row[best_local] > 0:
                global_j = unmatched_idx[best_local]
                tp_list[start + i] = 1
                gt_matched[global_j] = True
                iou_chunk[:, best_local] = 0  # 本块内不再匹配此 GT
            else:
                fp_list[start + i] = 1

    tp = int(tp_list.sum())
    fp = int(fp_list.sum())
    fn = num_gt - tp

    cum_tp = np.cumsum(tp_list)
    cum_fp = np.cumsum(fp_list)

    precisions = cum_tp / (cum_tp + cum_fp + 1e-10)
    recalls = cum_tp / (num_gt + 1e-10)

    ap = compute_ap(precisions, recalls)

    return {
        'precision': round(tp / (tp + fp + 1e-10), 4),
        'recall': round(tp / (num_gt + 1e-10), 4),
        'ap': round(ap, 4),
        'tp': tp,
        'fp': fp,
        'fn': fn,
        'num_gt': num_gt,
        'num_pred': num_pred,
    }


def main():
    args = parse_args()

    # 检查 CUDA 可用性
    import torch
    if args.device == 'cuda' and not torch.cuda.is_available():
        print('警告: CUDA 不可用，fallback 到 CPU')
        args.device = 'cpu'

    # 加载模型
    print(f'[1/4] 加载模型: {args.model}')
    model = YOLO(args.model)
    model.to(args.device)
    print(f'      设备: {model.device}')

    # 从模型类别名称推导预测ID映射（模型输出ID → 目标名称）
    model_mapping = load_model_mapping(model)
    if not model_mapping:
        print('错误: 模型类别名称中未匹配到 car/bus/truck，请检查模型训练配置')
        sys.exit(1)
    model_target_ids = list(model_mapping.keys())
    print(f'      模型类别映射: {model_mapping}')

    # 加载真值标注
    images_dir = Path(args.dataset) / 'images'
    labels_dir = Path(args.dataset) / 'labels'

    if not images_dir.exists():
        print(f'错误: images 目录不存在: {images_dir}')
        sys.exit(1)
    if not labels_dir.exists():
        print(f'错误: labels 目录不存在: {labels_dir}')
        sys.exit(1)

    # 从 classes.txt 读取数据集ID映射（标签文件中的ID → 目标名称）
    gt_mapping = load_gt_mapping(labels_dir)
    if gt_mapping:
        print(f'      数据集类别映射: {gt_mapping}')
    else:
        print('警告: 未找到 classes.txt，尝试用模型类别映射作为 GT 映射')
        gt_mapping = model_mapping

    target_names = sorted(set(list(gt_mapping.values()) + list(model_mapping.values())))

    print(f'[2/4] 加载真值标注: {labels_dir}')
    gt_data = load_ground_truth(labels_dir, gt_mapping)
    print(f'      共 {len(gt_data)} 张有标注的图片')

    # 收集所有预测
    print(f'[3/4] 运行推理 (imgsz={args.imgsz}, conf={args.conf})...')
    all_predictions = defaultdict(list)  # {class_name: [(conf, x1, y1, x2, y2), ...]}
    all_ground_truths = defaultdict(list)  # {class_name: [(x1, y1, x2, y2), ...]}
    total_time = 0.0
    total_images = 0

    label_files = sorted(labels_dir.glob('*.txt'))

    for label_path in label_files:
        stem = label_path.stem

        # 查找同名图片
        img_path = None
        for ext in ('.jpg', '.jpeg', '.png', '.bmp'):
            candidate = images_dir / (stem + ext)
            if candidate.exists():
                img_path = candidate
                break
        if img_path is None:
            continue

        img = cv2.imread(str(img_path))
        if img is None:
            continue
        h, w = img.shape[:2]
        total_images += 1

        # 推理（使用模型自身的类别ID过滤）
        t0 = time.time()
        use_half = (args.device != 'cpu')
        results = model.predict(img, conf=args.conf, classes=model_target_ids,
                                 imgsz=args.imgsz, half=use_half, device=args.device, verbose=False)
        total_time += (time.time() - t0) * 1000

        # 收集预测框（使用模型类别映射）
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for box in boxes:
                cls_id = int(box.cls[0])
                if cls_id not in model_mapping:
                    continue
                cls_name = model_mapping[cls_id]
                conf = float(box.conf[0])
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                all_predictions[cls_name].append((conf, x1, y1, x2, y2))

        # 收集真值框（GT 标签文件中的坐标是归一化的中心点格式）
        if stem in gt_data:
            for cls_name, cx, cy, bw, bh in gt_data[stem]:
                gx1, gy1, gx2, gy2 = xywh_to_xyxy(cx, cy, bw, bh, w, h)
                all_ground_truths[cls_name].append((gx1, gy1, gx2, gy2))

        if total_images % 50 == 0:
            print(f'      已处理 {total_images} 张...')

    avg_inference_ms = total_time / total_images if total_images > 0 else 0
    print(f'      完成: {total_images} 张图片, 平均推理耗时 {avg_inference_ms:.1f}ms/张')

    # 逐类别计算指标
    print(f'\n[4/4] 评估结果 (IoU={IOU_THRESHOLD}):')
    print('=' * 70)

    class_results = {}
    total_tp = total_fp = total_fn = 0
    total_gt = 0
    total_pred = 0
    ap_list = []

    for cls_name in target_names:
        preds = all_predictions.get(cls_name, [])
        gts = all_ground_truths.get(cls_name, [])
        result = evaluate_class(preds, gts)
        class_results[cls_name] = result

        total_tp += result['tp']
        total_fp += result['fp']
        total_fn += result['fn']
        total_gt += result['num_gt']
        total_pred += result['num_pred']
        ap_list.append(result['ap'])

        print(f'  {cls_name:>6s} | Precision={result["precision"]:.4f}  '
              f'Recall={result["recall"]:.4f}  AP={result["ap"]:.4f}  '
              f'(GT={result["num_gt"]}, Pred={result["num_pred"]}, '
              f'TP={result["tp"]}, FP={result["fp"]}, FN={result["fn"]})')

    # 总体指标
    mAP = round(np.mean(ap_list), 4) if ap_list else 0
    overall_precision = round(total_tp / (total_tp + total_fp + 1e-10), 4)
    overall_recall = round(total_tp / (total_gt + 1e-10), 4)

    print('-' * 70)
    print(f'  总体    | Precision={overall_precision:.4f}  '
          f'Recall={overall_recall:.4f}  mAP50={mAP:.4f}  '
          f'(GT={total_gt}, Pred={total_pred}, TP={total_tp}, FP={total_fp}, FN={total_fn})')
    print(f'  平均推理耗时: {avg_inference_ms:.1f}ms/张')
    print('=' * 70)

    # 保存结果
    default_dir = Path('output/val')
    default_dir.mkdir(parents=True, exist_ok=True)
    output_path = args.output or str(default_dir / f'eval_result_{int(time.time())}.json')
    result_data = {
        'model': args.model,
        'dataset': args.dataset,
        'imgsz': args.imgsz,
        'conf_threshold': args.conf,
        'iou_threshold': IOU_THRESHOLD,
        'model_mapping': {str(k): v for k, v in model_mapping.items()},
        'gt_mapping': {str(k): v for k, v in gt_mapping.items()},
        'total_images': total_images,
        'avg_inference_ms': round(avg_inference_ms, 1),
        'class_results': class_results,
        'overall': {
            'precision': overall_precision,
            'recall': overall_recall,
            'mAP50': mAP,
            'total_gt': total_gt,
            'total_pred': total_pred,
            'tp': total_tp,
            'fp': total_fp,
            'fn': total_fn,
        }
    }
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result_data, f, ensure_ascii=False, indent=2)
    print(f'\n结果已保存: {output_path}')


if __name__ == '__main__':
    main()

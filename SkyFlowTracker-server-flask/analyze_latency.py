"""
端到端延迟分析脚本

用法:
    python analyze_latency.py --android android_latency.log --backend backend_latency.log --browser browser_latency.log

日志格式（各端统一）:
    LATENCY|frame_id|stage|timestamp_ms

    stage 含义:
      0 = Android 采集 (capture_ts)
      1 = 后端 WS 接收 (recv_ts)
      2 = 后端发送前 (t_before_flask)
      3 = Flask 返回后 (t_after_flask)
      4 = 前端渲染完成 (render_ts)

延迟分段:
    seg0: Android→后端 (stage 1 - stage 0)
    seg1: 后端队列等待 (stage 2 - stage 1)
    seg2: Flask 推理往返 (stage 3 - stage 2)
    seg3: 后端→前端渲染 (stage 4 - stage 3)
    total: 端到端总延迟 (stage 4 - stage 0)
"""

import re
import sys
import argparse
from collections import defaultdict

import numpy as np


def parse_args():
    parser = argparse.ArgumentParser(description='端到端延迟分析')
    parser.add_argument('--android', type=str, help='Android logcat 日志文件')
    parser.add_argument('--backend', type=str, required=True, help='Spring Boot 后端日志文件')
    parser.add_argument('--browser', type=str, help='浏览器 Console 日志文件')
    parser.add_argument('--output', type=str, default=None, help='输出JSON路径')
    return parser.parse_args()


def parse_log(filepath):
    """解析 LATENCY 日志行，返回 {frame_id: {stage: ts}}"""
    records = defaultdict(dict)
    pattern = re.compile(r'LATENCY.*?(\d+)\|(\d)\|([\d.]+)')

    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            m = pattern.search(line)
            if m:
                frame_id = int(m.group(1))
                stage = int(m.group(2))
                ts = float(m.group(3))
                # 同 stage 保留最早的（去重）
                if stage not in records[frame_id]:
                    records[frame_id][stage] = ts
    return dict(records)


def compute_stats(values):
    """计算 avg, min, max, p50, p95, p99，负值截零"""
    if not values:
        return {'avg': 0, 'min': 0, 'max': 0, 'p50': 0, 'p95': 0, 'p99': 0, 'count': 0}
    arr = np.maximum(np.array(values), 0)  # 负值截零，避免时钟偏差残留
    return {
        'avg': round(float(np.mean(arr)), 1),
        'min': round(float(np.min(arr)), 1),
        'max': round(float(np.max(arr)), 1),
        'p50': round(float(np.percentile(arr, 50)), 1),
        'p95': round(float(np.percentile(arr, 95)), 1),
        'p99': round(float(np.percentile(arr, 99)), 1),
        'count': len(values),
    }


def main():
    args = parse_args()

    # 加载各端日志
    all_records = defaultdict(dict)

    if args.backend:
        print(f'加载后端日志: {args.backend}')
        backend = parse_log(args.backend)
        for fid, stages in backend.items():
            all_records[fid].update(stages)
        print(f'  后端记录: {len(backend)} 帧')

    if args.android:
        print(f'加载Android日志: {args.android}')
        android = parse_log(args.android)
        for fid, stages in android.items():
            all_records[fid].update(stages)
        print(f'  Android记录: {len(android)} 帧')

    if args.browser:
        print(f'加载浏览器日志: {args.browser}')
        browser = parse_log(args.browser)
        for fid, stages in browser.items():
            all_records[fid].update(stages)
        print(f'  浏览器记录: {len(browser)} 帧')

    # 按 frame_id 计算各段延迟
    seg0 = []  # Android → 后端 WS 接收
    seg1 = []  # 后端队列等待 (WS接收 → forwardFrames取走)
    seg2 = []  # Flask 推理往返
    seg3 = []  # 后端 → 前端渲染
    recv_to_render = []  # 后端接收 → 前端渲染 (不需推理，有 stage1+4 即可)
    total = []  # 端到端

    # 计算 Android 设备与服务器之间的时钟偏差
    # 假设同局域网内真实网络延迟 <20ms，大量帧的 s1-s0 中位数为时钟偏差
    raw_seg0 = []
    for fid, stages in all_records.items():
        s0 = stages.get(0)
        s1 = stages.get(1)
        if s0 is not None and s1 is not None:
            raw_seg0.append(s1 - s0)

    clock_skew = 0
    if raw_seg0:
        clock_skew = float(np.median(raw_seg0))
        if abs(clock_skew) > 100:
            print(f'检测到时钟偏差: {clock_skew:.0f}ms (Android {"快" if clock_skew < 0 else "慢"}于服务器 {abs(clock_skew/1000):.1f}s)，已自动修正')
        else:
            clock_skew = 0

    complete = 0
    partial = 0

    for fid in sorted(all_records.keys()):
        stages = all_records[fid]
        s0 = stages.get(0)
        s1 = stages.get(1)
        s2 = stages.get(2)
        s3 = stages.get(3)
        s4 = stages.get(4)

        if s1 is not None and s0 is not None:
            seg0.append(s1 - s0 - clock_skew)
        if s2 is not None and s1 is not None:
            seg1.append(s2 - s1)
        if s3 is not None and s2 is not None:
            seg2.append(s3 - s2)
        if s4 is not None and s3 is not None:
            seg3.append(s4 - s3)
        if s4 is not None and s1 is not None:
            recv_to_render.append(s4 - s1)
        if s4 is not None and s0 is not None:
            total.append(s4 - s0 - clock_skew)

        if all(v is not None for v in [s0, s1, s2, s3, s4]):
            complete += 1
        elif len(stages) >= 2:
            partial += 1

    # 输出结果
    segments = [
        ('Android→后端WS接收', seg0),
        ('后端队列等待', seg1),
        ('Flask推理往返', seg2),
        ('后端→前端渲染', seg3),
        ('后端接收→前端渲染(无需推理)', recv_to_render),
        ('端到端总延迟', total),
    ]

    print(f'\n关联结果: {complete} 帧完整链路, {partial} 帧部分数据')
    print('=' * 90)
    print(f'{"延迟分段":<20s} {"avg":>8s} {"min":>8s} {"max":>8s} {"p50":>8s} {"p95":>8s} {"p99":>8s} {"帧数":>6s}')
    print('-' * 90)

    result_data = {
        'complete_frames': complete,
        'partial_frames': partial,
        'segments': {},
    }

    for name, values in segments:
        stats = compute_stats(values)
        result_data['segments'][name] = stats
        if stats['count'] > 0:
            print(f'{name:<20s} {stats["avg"]:>7.1f}ms {stats["min"]:>7.1f}ms {stats["max"]:>7.1f}ms '
                  f'{stats["p50"]:>7.1f}ms {stats["p95"]:>7.1f}ms {stats["p99"]:>7.1f}ms {stats["count"]:>5d}')
        else:
            print(f'{name:<20s} {"(无数据)":>50s}')

    print('=' * 90)

    # 保存 JSON
    if args.output:
        import json
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(result_data, f, ensure_ascii=False, indent=2)
        print(f'\n结果已保存: {args.output}')


if __name__ == '__main__':
    main()

/*
 * Copyright (c) 2018-2020 DJI
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package dji.v5.ux.cameracore.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.v5.utils.common.LogUtils;

/**
 * 视频流录制器
 * 用于录制DJI视频流到手机存储
 */
public class VideoStreamRecorder {
    private static final String TAG = "VideoStreamRecorder";
    private static final int FRAME_RATE = 30; // 帧率
    private static final int I_FRAME_INTERVAL = 1; // I帧间隔（秒）
    private static final int BIT_RATE = 4000000; // 比特率 4Mbps（高质量）
    
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private boolean muxerStarted = false;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private long startTime = 0;
    private int frameCount = 0;
    
    private int videoWidth;
    private int videoHeight;
    private String outputPath;
    private Context context;
    
    /**
     * 录制监听器
     */
    public interface RecordingListener {
        void onRecordingStarted(String filePath);
        void onRecordingProgress(long durationMs);
        void onRecordingStopped(String filePath, boolean success);
        void onRecordingError(String error);
    }
    
    private RecordingListener listener;
    
    public VideoStreamRecorder(Context context) {
        this.context = context;
    }
    
    public void setListener(RecordingListener listener) {
        this.listener = listener;
    }
    
    /**
     * 开始录制
     */
    public boolean startRecording(int width, int height) {
        if (isRecording.get()) {
            LogUtils.w(TAG, "已经在录制中");
            return false;
        }
        
        this.videoWidth = width;
        this.videoHeight = height;
        
        try {
            // 创建输出文件
            outputPath = createOutputFile();
            if (outputPath == null) {
                notifyError("无法创建输出文件");
                return false;
            }
            
            // 初始化编码器
            if (!initEncoder()) {
                notifyError("初始化编码器失败");
                return false;
            }
            
            // 初始化混合器
            if (!initMuxer()) {
                notifyError("初始化混合器失败");
                return false;
            }
            
            isRecording.set(true);
            startTime = System.currentTimeMillis();
            frameCount = 0;
            
            LogUtils.i(TAG, "开始录制: " + outputPath);
            if (listener != null) {
                listener.onRecordingStarted(outputPath);
            }
            
            return true;
        } catch (Exception e) {
            LogUtils.e(TAG, "启动录制失败: " + e.getMessage());
            notifyError("启动录制失败: " + e.getMessage());
            release();
            return false;
        }
    }
    
    /**
     * 停止录制
     */
    public void stopRecording() {
        if (!isRecording.get()) {
            return;
        }
        
        isRecording.set(false);
        
        try {
            // 停止编码器
            if (mediaCodec != null) {
                try {
                    mediaCodec.stop();
                } catch (Exception e) {
                    LogUtils.e(TAG, "停止编码器失败: " + e.getMessage());
                }
            }
            
            // 停止混合器
            if (mediaMuxer != null && muxerStarted) {
                try {
                    mediaMuxer.stop();
                } catch (Exception e) {
                    LogUtils.e(TAG, "停止混合器失败: " + e.getMessage());
                }
            }
            
            LogUtils.i(TAG, "录制停止: " + outputPath + ", 帧数: " + frameCount);
            
            if (listener != null) {
                listener.onRecordingStopped(outputPath, true);
            }
            
        } catch (Exception e) {
            LogUtils.e(TAG, "停止录制失败: " + e.getMessage());
            if (listener != null) {
                listener.onRecordingStopped(outputPath, false);
            }
        } finally {
            release();
        }
    }
    
    /**
     * 编码一帧（Bitmap格式）
     */
    public void encodeFrame(Bitmap bitmap) {
        if (!isRecording.get() || mediaCodec == null) {
            return;
        }
        
        try {
            // 将Bitmap转换为YUV420并编码
            byte[] yuvData = bitmapToYUV420(bitmap);
            encodeFrameYUV(yuvData);
        } catch (Exception e) {
            LogUtils.e(TAG, "编码帧失败: " + e.getMessage());
        }
    }
    
    /**
     * 编码一帧（YUV420格式）
     */
    public void encodeFrameYUV(byte[] yuvData) {
        if (!isRecording.get() || mediaCodec == null) {
            return;
        }
        
        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(yuvData);
                    
                    long presentationTimeUs = (System.currentTimeMillis() - startTime) * 1000;
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuvData.length, presentationTimeUs, 0);
                    frameCount++;
                    
                    // 通知进度
                    if (listener != null && frameCount % 30 == 0) { // 每秒通知一次
                        listener.onRecordingProgress(System.currentTimeMillis() - startTime);
                    }
                }
            }
            
            // 获取输出
            drainEncoder(false);
            
        } catch (Exception e) {
            LogUtils.e(TAG, "编码YUV帧失败: " + e.getMessage());
        }
    }
    
    /**
     * 是否正在录制
     */
    public boolean isRecording() {
        return isRecording.get();
    }
    
    // ========== 私有方法 ==========
    
    private boolean initEncoder() {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            
            LogUtils.i(TAG, "编码器初始化成功: " + videoWidth + "x" + videoHeight);
            return true;
        } catch (IOException e) {
            LogUtils.e(TAG, "初始化编码器失败: " + e.getMessage());
            return false;
        }
    }
    
    private boolean initMuxer() {
        try {
            mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            LogUtils.i(TAG, "混合器初始化成功");
            return true;
        } catch (IOException e) {
            LogUtils.e(TAG, "初始化混合器失败: " + e.getMessage());
            return false;
        }
    }
    
    private void drainEncoder(boolean endOfStream) {
        if (mediaCodec == null) {
            return;
        }
        
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        while (true) {
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                videoTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
                muxerStarted = true;
                LogUtils.i(TAG, "混合器已启动");
            } else if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                
                if (outputBuffer != null) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    
                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw new RuntimeException("muxer hasn't started");
                        }
                        
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    }
                }
                
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }
    
    private String createOutputFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "DJI_VIDEO_" + timeStamp + ".mp4";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SkyFlowTracker");
                
                Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    // 获取实际文件路径（用于MediaMuxer）
                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    File appDir = new File(dcimDir, "SkyFlowTracker");
                    if (!appDir.exists()) {
                        appDir.mkdirs();
                    }
                    return new File(appDir, fileName).getAbsolutePath();
                }
            } else {
                // Android 9及以下
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File appDir = new File(dcimDir, "SkyFlowTracker");
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }
                return new File(appDir, fileName).getAbsolutePath();
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "创建输出文件失败: " + e.getMessage());
        }
        return null;
    }
    
    private byte[] bitmapToYUV420(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        
        byte[] yuv = new byte[width * height * 3 / 2];
        encodeYUV420SP(yuv, argb, width, height);
        return yuv;
    }
    
    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;
        
        int R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff);
                
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
                
                index++;
            }
        }
    }
    
    private void release() {
        if (mediaCodec != null) {
            try {
                mediaCodec.release();
            } catch (Exception e) {
                LogUtils.e(TAG, "释放编码器失败: " + e.getMessage());
            }
            mediaCodec = null;
        }
        
        if (mediaMuxer != null) {
            try {
                mediaMuxer.release();
            } catch (Exception e) {
                LogUtils.e(TAG, "释放混合器失败: " + e.getMessage());
            }
            mediaMuxer = null;
        }
        
        muxerStarted = false;
        videoTrackIndex = -1;
    }
    
    private void notifyError(String error) {
        LogUtils.e(TAG, error);
        if (listener != null) {
            listener.onRecordingError(error);
        }
    }
}

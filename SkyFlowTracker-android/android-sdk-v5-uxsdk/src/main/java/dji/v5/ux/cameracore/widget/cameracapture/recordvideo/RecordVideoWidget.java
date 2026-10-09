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

package dji.v5.ux.cameracore.widget.cameracapture.recordvideo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import dji.sdk.keyvalue.value.camera.CameraSDCardState;
import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.sdk.keyvalue.value.camera.SSDOperationState;
import dji.sdk.keyvalue.value.common.CameraLensType;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.ux.R;
import dji.v5.ux.cameracore.util.CameraActionSound;
import dji.v5.ux.cameracore.widget.cameracapture.recordvideo.RecordVideoWidgetModel.RecordingState;
import dji.v5.ux.cameracore.widget.cameracontrols.RemoteControllerButtonDownModel;
import dji.v5.ux.core.base.DJISDKModel;
import dji.v5.ux.core.base.ICameraIndex;
import dji.v5.ux.core.base.SchedulerProvider;
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget;
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore;
import dji.v5.ux.core.util.CameraUtil;
import dji.v5.ux.core.util.UxErrorHandle;
import io.reactivex.rxjava3.core.Completable;

/**
 * Record Video Widget
 * <p>
 * Widget can be used for recording video. The widget displays the current video mode. It also
 * displays the storage state and errors associated with it.
 */
public class RecordVideoWidget extends ConstraintLayoutWidget<Object> implements OnClickListener, ICameraIndex {

    //region Fields
    private static final String TAG = "RecordVideoWidget";
    private RecordVideoWidgetModel widgetModel;
    private RemoteControllerButtonDownModel buttonDownModel;
    private ImageView centerImageView;
    private TextView videoTimerTextView;
    private ImageView storageStatusOverlayImageView;
    private Map<StorageIconState, Drawable> storageInternalIconMap;
    private Map<StorageIconState, Drawable> storageSSDIconMap;
    private Map<StorageIconState, Drawable> storageSDCardIconMap;
    private Drawable recordVideoStartDrawable;
    private Drawable recordVideoStopDrawable;
    private CameraActionSound cameraActionSound;
    private dji.v5.ux.cameracore.util.VideoStreamRecorder videoStreamRecorder; // 视频流录制器
    private boolean isRecordingToPhone = false; // 是否正在录制到手机
    //endregion

    //region Lifecycle
    public RecordVideoWidget(Context context) {
        super(context);
    }

    public RecordVideoWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecordVideoWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        inflate(context, R.layout.uxsdk_widget_record_video, this);
        centerImageView = findViewById(R.id.image_view_center);
        videoTimerTextView = findViewById(R.id.text_view_video_record_time);
        storageStatusOverlayImageView = findViewById(R.id.image_view_storage_status_overlay);
        storageInternalIconMap = new HashMap<>();
        storageSSDIconMap = new HashMap<>();
        storageSDCardIconMap = new HashMap<>();
        centerImageView.setOnClickListener(this);
        cameraActionSound = new CameraActionSound(context);
        videoStreamRecorder = new dji.v5.ux.cameracore.util.VideoStreamRecorder(context);
        setupVideoRecorder();
        if (!isInEditMode()) {
            widgetModel = new RecordVideoWidgetModel(DJISDKModel.getInstance(), ObservableInMemoryKeyedStore.getInstance());
            buttonDownModel = new RemoteControllerButtonDownModel(DJISDKModel.getInstance(), ObservableInMemoryKeyedStore.getInstance());
        }
        initDefaults();
        if (attrs != null) {
            initAttributes(context, attrs);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            widgetModel.setup();
            buttonDownModel.setup();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            widgetModel.cleanup();
            buttonDownModel.cleanup();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void reactToModelChanges() {
        addReaction(widgetModel.getRecordingTimeInSeconds()
                .observeOn(SchedulerProvider.ui())
                .subscribe(this::updateRecordingTime, UxErrorHandle.logErrorConsumer(TAG, "record time: ")));
        addReaction(widgetModel.getRecordingState()
                .observeOn(SchedulerProvider.ui())
                .subscribe(recordingState -> onIsRecordingVideoChange(recordingState, true), UxErrorHandle.logErrorConsumer(TAG, "is recording: ")));
        addReaction(widgetModel.getCameraVideoStorageState()
                .observeOn(SchedulerProvider.ui())
                .subscribe(this::updateCameraForegroundResource, UxErrorHandle.logErrorConsumer(TAG, "camera storage update: ")));
        // 监听录屏请求
        addReaction(widgetModel.getScreenRecordRequest()
                .observeOn(SchedulerProvider.ui())
                .subscribe(this::handleScreenRecordRequest, UxErrorHandle.logErrorConsumer(TAG, "screenRecordRequest: ")));
        addReaction(buttonDownModel.isRecordButtonDownProcessor().toFlowable()
                .observeOn(SchedulerProvider.ui())
                .subscribe(aBoolean -> {
                    if (aBoolean == Boolean.TRUE) {
                        actionOnRecording();
                    }
                }));
    }

    @NonNull
    @Override
    public String getIdealDimensionRatioString() {
        return getResources().getString(R.string.uxsdk_widget_default_ratio);
    }

    @NonNull
    public ComponentIndexType getCameraIndex() {
        return widgetModel.getCameraIndex();
    }

    @NonNull
    @Override
    public CameraLensType getLensType() {
        return widgetModel.getLensType();
    }

    @Override
    public void updateCameraSource(@NonNull ComponentIndexType cameraIndex, @NonNull CameraLensType lensType) {
        if (!isInEditMode()) {
            widgetModel.updateCameraSource(cameraIndex, lensType);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(centerImageView)) {
            actionOnRecording();
        }
    }

    private void actionOnRecording() {
        dji.v5.utils.common.LogUtils.d(TAG, "actionOnRecording 被调用, isRecordingToPhone=" + isRecordingToPhone);
        showToast("录像按钮被点击, 手机录制状态=" + isRecordingToPhone);
        
        if (!widgetModel.isVideoMode()) {
            dji.v5.utils.common.LogUtils.d(TAG, "不是视频模式，返回");
            showToast("不是视频模式");
            return;
        }
        
        dji.v5.utils.common.LogUtils.d(TAG, "是视频模式，继续处理");
        
        // 如果正在手机录制，直接停止
        if (isRecordingToPhone) {
            dji.v5.utils.common.LogUtils.d(TAG, "正在手机录制，准备停止");
            showToast("停止手机录制");
            stopRecordingToPhone();
            // 更新UI
            onIsRecordingVideoChange(RecordingState.RECORDING_STOPPED, true);
            return;
        }
        
        widgetModel.getRecordingState().firstOrError().flatMapCompletable(recordingState -> {
            dji.v5.utils.common.LogUtils.d(TAG, "当前录制状态: " + recordingState);
            showToast("当前录制状态: " + recordingState);
            
            if (recordingState == RecordingState.RECORDING_IN_PROGRESS) {
                dji.v5.utils.common.LogUtils.d(TAG, "正在录制，准备停止");
                showToast("正在录制，准备停止");
                return widgetModel.stopRecordVideo();
            } else if (recordingState == RecordingState.RECORDING_STOPPED || recordingState == RecordingState.RECORDING_NOT_STARED) {
                dji.v5.utils.common.LogUtils.d(TAG, "未录制，准备开始");
                showToast("未录制，准备开始录像");
                return widgetModel.startRecordVideo();
            } else {
                dji.v5.utils.common.LogUtils.d(TAG, "未知状态，不处理");
                showToast("未知状态: " + recordingState);
                return Completable.complete();
            }
        }).observeOn(SchedulerProvider.ui()).subscribe(() -> {
            dji.v5.utils.common.LogUtils.d(TAG, "录像操作完成");
            showToast("录像操作已提交");
        }, error -> {
            dji.v5.utils.common.LogUtils.e(TAG, "录像操作失败: " + error.getMessage());
            showToast("录像操作失败: " + error.getMessage());
            UxErrorHandle.logErrorConsumer(TAG, "START STOP VIDEO").accept(error);
        });
    }
    
    /**
     * 显示Toast提示
     */
    private void showToast(String message) {
        // 确保在UI线程中显示Toast
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            post(() -> android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show());
        }
    }

    //region private helpers
    private void initDefaults() {
        recordVideoStartDrawable = getResources().getDrawable(R.drawable.uxsdk_selector_start_record_video);
        recordVideoStopDrawable = getResources().getDrawable(R.drawable.uxsdk_selector_stop_record_video);
        setInternalStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_internal_storage_not_inserted);
        setInternalStorageIcon(StorageIconState.SLOW, R.drawable.uxsdk_ic_internal_storage_slow);
        setInternalStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_internal_storage_full);
        setSDCardStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_sdcard_not_inserted);
        setSDCardStorageIcon(StorageIconState.SLOW, R.drawable.uxsdk_ic_sdcard_slow);
        setSDCardStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_sdcard_full);
        setSSDStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_ssd_not_inserted);
        setSSDStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_ssd_full);
        setVideoTimerTextColor(Color.WHITE);
    }

    private void initAttributes(@NonNull Context context, @NonNull AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RecordVideoWidget);
        updateCameraSource(ComponentIndexType.find(typedArray.getInt(R.styleable.RecordVideoWidget_uxsdk_cameraIndex, 0)),
                CameraLensType.find(typedArray.getInt(R.styleable.RecordVideoWidget_uxsdk_lensType, 0)));

        int textAppearance = typedArray.getResourceId(R.styleable.RecordVideoWidget_uxsdk_timerTextAppearance, INVALID_RESOURCE);
        if (textAppearance != INVALID_RESOURCE) {
            setTimerTextAppearance(textAppearance);
        }
        setVideoTimerTextColor(typedArray.getColor(R.styleable.RecordVideoWidget_uxsdk_timerTextColor, Color.WHITE));
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_timerTextBackground) != null) {
            setVideoTimerTextBackground(typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_timerTextBackground));
        }
        setVideoTimerTextSize(typedArray.getDimension(R.styleable.RecordVideoWidget_uxsdk_timerTextSize, 12));
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_foregroundIconBackground) != null) {
            setForegroundIconBackground(typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_foregroundIconBackground));
        }

        initRecordVideoDrawable(typedArray);
        initInternalStorageIcon(typedArray);
        initSDCardStorageIcon(typedArray);
        initSSDStorageIcon(typedArray);
        typedArray.recycle();
    }

    private void initSSDStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdNotInsertedIcon) != null) {
            setSSDStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdFullIcon) != null) {
            setSSDStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdSlowIcon) != null) {
            setSSDStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_ssdSlowIcon));
        }
    }

    private void initSDCardStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardNotInsertedIcon) != null) {
            setSDCardStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardFullIcon) != null) {
            setSDCardStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardSlowIcon) != null) {
            setSDCardStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_sdCardSlowIcon));
        }

    }

    private void initInternalStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageNotInsertedIcon) != null) {
            setInternalStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageFullIcon) != null) {
            setInternalStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageSlowIcon) != null) {
            setInternalStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_internalStorageSlowIcon));
        }
    }

    private void initRecordVideoDrawable(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_recordStartIcon) != null) {
            recordVideoStartDrawable = typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_recordStartIcon);
        }
        if (typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_recordStopIcon) != null) {
            recordVideoStopDrawable = typedArray.getDrawable(R.styleable.RecordVideoWidget_uxsdk_recordStopIcon);
        }
    }

    private void updateCameraForegroundResource(CameraVideoStorageState cameraVideoStorageState) {
        Drawable foregroundResource = null;
        if (cameraVideoStorageState instanceof CameraSDVideoStorageState) {
            if (cameraVideoStorageState.getStorageLocation() == CameraStorageLocation.SDCARD) {
                foregroundResource = updateResourceWithStorageInSDCard(cameraVideoStorageState);
            } else if (cameraVideoStorageState.getStorageLocation() == CameraStorageLocation.INTERNAL) {
                foregroundResource = updateResourceWithStorageInternal(cameraVideoStorageState);
            }
        } else if (cameraVideoStorageState instanceof CameraSSDVideoStorageState) {
            if (((CameraSSDVideoStorageState) cameraVideoStorageState).getSsdOperationState()
                    == SSDOperationState.NOT_FOUND) {
                foregroundResource = getSSDStorageIcon(StorageIconState.NOT_INSERTED);
            } else if (((CameraSSDVideoStorageState) cameraVideoStorageState).getSsdOperationState()
                    == SSDOperationState.FULL) {
                foregroundResource = getSSDStorageIcon(StorageIconState.FULL);
            }
        }

        storageStatusOverlayImageView.setImageDrawable(foregroundResource);
    }

    private Drawable updateResourceWithStorageInSDCard(CameraVideoStorageState cameraVideoStorageState) {
        Drawable foregroundResource = null;
        if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.NOT_INSERTED) {
            foregroundResource = getSDCardStorageIcon(StorageIconState.NOT_INSERTED);
        } else if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.FULL) {
            foregroundResource = getSDCardStorageIcon(StorageIconState.FULL);
        } else if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.SLOW) {
            foregroundResource = getSDCardStorageIcon(StorageIconState.SLOW);
        }
        return foregroundResource;
    }

    private Drawable updateResourceWithStorageInternal(CameraVideoStorageState cameraVideoStorageState) {
        Drawable foregroundResource = null;
        if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.NOT_INSERTED) {
            foregroundResource = getInternalStorageIcon(StorageIconState.NOT_INSERTED);
        } else if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.FULL) {
            foregroundResource = getInternalStorageIcon(StorageIconState.FULL);
        } else if (((CameraSDVideoStorageState) cameraVideoStorageState).getSdCardOperationState()
                == CameraSDCardState.SLOW) {
            foregroundResource = getInternalStorageIcon(StorageIconState.SLOW);
        }
        return foregroundResource;
    }

    private void updateRecordingTime(int seconds) {
        videoTimerTextView.setText(CameraUtil.formatVideoTime(getResources(), seconds));
    }

    private void onIsRecordingVideoChange(RecordingState recordingState, boolean playSound) {
        boolean isRecordingVideo = recordingState == RecordingState.RECORDING_IN_PROGRESS;
        Drawable recordStart = recordVideoStartDrawable;
        Drawable recordStop = recordVideoStopDrawable;
        centerImageView.setImageDrawable(isRecordingVideo ? recordStop : recordStart);
        videoTimerTextView.setVisibility(isRecordingVideo ? View.VISIBLE : View.INVISIBLE);
        storageStatusOverlayImageView.setVisibility(isRecordingVideo ? View.GONE : View.VISIBLE);
        if (playSound) {
            if (recordingState == RecordingState.RECORDING_IN_PROGRESS) {
                cameraActionSound.playStartRecordVideo();
            } else if (recordingState == RecordingState.RECORDING_STOPPED) {
                cameraActionSound.playStopRecordVideo();
            }
        }
    }

    private void checkAndUpdateCameraForegroundResource() {
        if (!isInEditMode()) {
            addDisposable(widgetModel.getCameraVideoStorageState().firstOrError()
                    .observeOn(SchedulerProvider.ui())
                    .subscribe(this::updateCameraForegroundResource,
                            UxErrorHandle.logErrorConsumer(TAG, "check and update camera foreground resource: ")));
        }
    }

    private void checkAndUpdateCenterImageView() {
        if (!isInEditMode()) {
            addDisposable(widgetModel.getRecordingState().firstOrError()
                    .observeOn(SchedulerProvider.ui())
                    .subscribe(recordingState -> onIsRecordingVideoChange(recordingState, false),
                            UxErrorHandle.logErrorConsumer(TAG, "check and update camera foreground resource: ")));
        }
    }
    //endregion

    //region customizations

    /**
     * Get the current start recording video icon
     *
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getRecordVideoStartDrawable() {
        return recordVideoStartDrawable;
    }

    /**
     * Set the start record video icon
     *
     * @param resourceId to be used
     */
    public void setRecordVideoStartDrawable(@DrawableRes int resourceId) {
        setRecordVideoStartDrawable(getResources().getDrawable(resourceId));
    }

    /**
     * Set the start record video icon
     *
     * @param drawable to be used
     */
    public void setRecordVideoStartDrawable(@Nullable Drawable drawable) {
        recordVideoStartDrawable = drawable;
        checkAndUpdateCenterImageView();
    }

    /**
     * Get the current stop video recording icon
     *
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getRecordVideoStopDrawable() {
        return recordVideoStopDrawable;
    }

    /**
     * Set stop video recording icon
     *
     * @param resourceId to be used
     */
    public void setRecordVideoStopDrawable(@DrawableRes int resourceId) {
        setRecordVideoStopDrawable(getResources().getDrawable(resourceId));
    }

    /**
     * Set stop video recording icon
     *
     * @param drawable to be used
     */
    public void setRecordVideoStopDrawable(@Nullable Drawable drawable) {
        recordVideoStopDrawable = drawable;
        checkAndUpdateCenterImageView();
    }

    /**
     * Set the icon for internal storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @param resourceId       to be used
     */
    public void setInternalStorageIcon(@NonNull StorageIconState storageIconState, @DrawableRes int resourceId) {
        setInternalStorageIcon(storageIconState, getResources().getDrawable(resourceId));
    }

    /**
     * Set the icon for internal storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @param drawable         to be used
     */
    public void setInternalStorageIcon(@NonNull StorageIconState storageIconState, @Nullable Drawable drawable) {
        storageInternalIconMap.put(storageIconState, drawable);
        checkAndUpdateCameraForegroundResource();
    }

    /**
     * Get the icon for internal storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getInternalStorageIcon(@NonNull StorageIconState storageIconState) {
        return storageInternalIconMap.get(storageIconState);
    }

    /**
     * Set the icon for SD card storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @param resourceId       to be used
     */
    public void setSDCardStorageIcon(@NonNull StorageIconState storageIconState, @DrawableRes int resourceId) {
        setSDCardStorageIcon(storageIconState, getResources().getDrawable(resourceId));
    }

    /**
     * Set the icon for SD card storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @param drawable         to be used
     */
    public void setSDCardStorageIcon(@NonNull StorageIconState storageIconState, @Nullable Drawable drawable) {
        storageSDCardIconMap.put(storageIconState, drawable);
        checkAndUpdateCameraForegroundResource();
    }

    /**
     * Get the icon for SD card storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getSDCardStorageIcon(@NonNull StorageIconState storageIconState) {
        return storageSDCardIconMap.get(storageIconState);
    }

    /**
     * Set the icon for SSD storage based on storage icon state
     *
     * @param storageIconState for which icon should be used
     * @param resourceId       to be used
     */
    public void setSSDStorageIcon(@NonNull StorageIconState storageIconState, @DrawableRes int resourceId) {
        setSSDStorageIcon(storageIconState, getResources().getDrawable(resourceId));
    }

    /**
     * Set the icon for SSD storage based on storage icon state
     *
     * @param storageIconState for which icon should be used
     * @param drawable         Drawable to be used
     */
    public void setSSDStorageIcon(@NonNull StorageIconState storageIconState, @Nullable Drawable drawable) {
        storageSSDIconMap.put(storageIconState, drawable);
        checkAndUpdateCameraForegroundResource();
    }

    /**
     * Get the icon for SSD storage based on storage icon state
     *
     * @param storageIconState for which icon is used
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getSSDStorageIcon(@NonNull StorageIconState storageIconState) {
        return storageSSDIconMap.get(storageIconState);
    }

    /**
     * Set the background of the foreground icon
     *
     * @param resourceId to be used as background
     */
    public void setForegroundIconBackground(@DrawableRes int resourceId) {
        storageStatusOverlayImageView.setBackgroundResource(resourceId);
    }

    /**
     * Set background of the foreground icon
     *
     * @param drawable to be used as background
     */
    public void setForegroundIconBackground(@Nullable Drawable drawable) {
        storageStatusOverlayImageView.setBackground(drawable);
    }

    /**
     * Get current background of foreground icon
     *
     * @return Drawable being used
     */
    public Drawable getForegroundIconBackground() {
        return storageStatusOverlayImageView.getBackground();
    }

    /**
     * Set the background of the video record duration text
     *
     * @param resourceId to be used
     */
    public void setVideoTimerTextBackground(@DrawableRes int resourceId) {
        videoTimerTextView.setBackgroundResource(resourceId);
    }

    /**
     * Set the background of the video record duration text
     *
     * @param drawable to be used
     */
    public void setVideoTimerTextBackground(@Nullable Drawable drawable) {
        videoTimerTextView.setBackground(drawable);
    }

    /**
     * Get the color state list currently used for video record duration text
     *
     * @return ColorSateList
     */
    @Nullable
    public ColorStateList getVideoTimerTextColors() {
        return videoTimerTextView.getTextColors();
    }

    /**
     * Set the color state list for video record duration text
     *
     * @param colorStateList to be used
     */
    public void setVideoTimerTextColors(@Nullable ColorStateList colorStateList) {
        videoTimerTextView.setTextColor(colorStateList);
    }

    /**
     * Get the current text color of video record duration text
     *
     * @return integer value representing color
     */
    @ColorInt
    public int getVideoTimerTextColor() {
        return videoTimerTextView.getCurrentTextColor();
    }

    /**
     * Set the text color of video record duration text
     *
     * @param color integer value representing color
     */
    public void setVideoTimerTextColor(@ColorInt int color) {
        videoTimerTextView.setTextColor(color);
    }

    /**
     * Get the current text size of video record duration text
     *
     * @return float value representing text size
     */
    @Dimension
    public float getVideoTimerTextSize() {
        return videoTimerTextView.getTextSize();
    }

    /**
     * Set the text size of video record duration text
     *
     * @param textSize float value
     */
    public void setVideoTimerTextSize(@Dimension float textSize) {
        videoTimerTextView.setTextSize(textSize);
    }

    /**
     * Set the text appearance for video record duration text
     *
     * @param textAppearance to be used
     */
    public void setTimerTextAppearance(@StyleRes int textAppearance) {
        videoTimerTextView.setTextAppearance(getContext(), textAppearance);
    }
    //endregion
    
    //region Video Stream Recording
    
    /**
     * 设置视频录制器
     */
    private void setupVideoRecorder() {
        videoStreamRecorder.setListener(new dji.v5.ux.cameracore.util.VideoStreamRecorder.RecordingListener() {
            @Override
            public void onRecordingStarted(String filePath) {
                dji.v5.utils.common.LogUtils.i(TAG, "录制器回调-录制开始: " + filePath);
                showToast("录制器已启动");
                // 不在这里设置 isRecordingToPhone，因为已经在 handleScreenRecordRequest 中设置了
                // 播放开始录像音效
                post(() -> cameraActionSound.playStartRecordVideo());
            }

            @Override
            public void onRecordingProgress(long durationMs) {
                // 更新录制时间显示
                int seconds = (int) (durationMs / 1000);
                dji.v5.utils.common.LogUtils.d(TAG, "录制进度: " + seconds + "秒");
                // 在UI线程更新时间
                post(() -> updateRecordingTime(seconds));
            }

            @Override
            public void onRecordingStopped(String filePath, boolean success) {
                dji.v5.utils.common.LogUtils.i(TAG, "录制器回调-录制停止: " + filePath + ", 成功: " + success);
                showToast("录制停止: " + (success ? "成功" : "失败"));
                // 播放停止录像音效
                post(() -> cameraActionSound.playStopRecordVideo());
                
                if (success) {
                    // 显示提示弹窗
                    post(() -> showNoStorageDialog());
                }
            }

            @Override
            public void onRecordingError(String error) {
                dji.v5.utils.common.LogUtils.e(TAG, "录制错误: " + error);
                showToast("录制错误: " + error);
                // 发生错误时重置状态
                isRecordingToPhone = false;
                post(() -> onIsRecordingVideoChange(RecordingState.RECORDING_STOPPED, false));
            }
        });
    }
    
    /**
     * 处理录屏请求
     */
    private void handleScreenRecordRequest(boolean start) {
        dji.v5.utils.common.LogUtils.d(TAG, "=== handleScreenRecordRequest 被调用 ===");
        dji.v5.utils.common.LogUtils.d(TAG, "参数 start=" + start);
        dji.v5.utils.common.LogUtils.d(TAG, "当前 isRecordingToPhone=" + isRecordingToPhone);
        
        showToast("收到录屏请求: " + (start ? "开始" : "停止") + ", 当前状态=" + isRecordingToPhone);
        
        if (start) {
            // 开始录制到手机
            isRecordingToPhone = true;
            dji.v5.utils.common.LogUtils.d(TAG, "已设置 isRecordingToPhone = true");
            showToast("已设置录制状态为true");
            // 更新UI显示为录制中
            onIsRecordingVideoChange(RecordingState.RECORDING_IN_PROGRESS, true);
            startRecordingToPhone();
        } else {
            // 停止录制
            stopRecordingToPhone();
            isRecordingToPhone = false;
            dji.v5.utils.common.LogUtils.d(TAG, "已设置 isRecordingToPhone = false");
            showToast("已设置录制状态为false");
            // 更新UI显示为未录制
            onIsRecordingVideoChange(RecordingState.RECORDING_STOPPED, true);
        }
        
        dji.v5.utils.common.LogUtils.d(TAG, "=== handleScreenRecordRequest 执行完毕，isRecordingToPhone=" + isRecordingToPhone + " ===");
    }
    
    /**
     * 开始录制视频流到手机
     */
    private void startRecordingToPhone() {
        try {
            dji.v5.utils.common.LogUtils.d(TAG, "开始录制视频流到手机");
            showToast("开始录制视频流到手机");
            
            // 获取MediaDataCenter的CameraStreamManager
            dji.v5.manager.interfaces.IMediaDataCenter mediaDataCenter = dji.v5.manager.datacenter.MediaDataCenter.getInstance();
            dji.v5.manager.interfaces.ICameraStreamManager streamManager = mediaDataCenter.getCameraStreamManager();
            
            if (streamManager == null) {
                dji.v5.utils.common.LogUtils.e(TAG, "CameraStreamManager为null");
                showToast("CameraStreamManager为null");
                return;
            }
            
            showToast("CameraStreamManager获取成功");
            
            // 获取当前相机索引
            dji.sdk.keyvalue.value.common.ComponentIndexType cameraIndex = widgetModel.getCameraIndex();
            dji.sdk.keyvalue.value.common.CameraLensType lensType = widgetModel.getLensType();
            
            // 添加帧监听器来持续捕获视频帧
            dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener frameListener = new dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener() {
                private boolean recorderStarted = false;
                
                @Override
                public void onFrame(byte[] bytes, int offset, int length, int width, int height, dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat format) {
                    if (!isRecordingToPhone) {
                        return;
                    }
                    
                    try {
                        // 第一帧时启动录制器
                        if (!recorderStarted) {
                            boolean started = videoStreamRecorder.startRecording(width, height);
                            if (started) {
                                recorderStarted = true;
                                dji.v5.utils.common.LogUtils.i(TAG, "录制器已启动: " + width + "x" + height);
                            } else {
                                dji.v5.utils.common.LogUtils.e(TAG, "启动录制器失败");
                                isRecordingToPhone = false;
                                return;
                            }
                        }
                        
                        // 编码视频帧
                        if (format == dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.YUV420_888) {
                            videoStreamRecorder.encodeFrameYUV(bytes);
                        } else if (format == dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.RGBA_8888) {
                            // RGBA格式需要转换为Bitmap再编码
                            android.graphics.Bitmap bitmap = rgbaToBitmap(bytes, width, height);
                            if (bitmap != null) {
                                videoStreamRecorder.encodeFrame(bitmap);
                                bitmap.recycle();
                            }
                        }
                    } catch (Exception e) {
                        dji.v5.utils.common.LogUtils.e(TAG, "编码视频帧失败: " + e.getMessage());
                    }
                }
            };
            
            // 优先使用RGBA格式（质量更高）
            try {
                streamManager.addFrameListener(cameraIndex, dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.RGBA_8888, frameListener);
                dji.v5.utils.common.LogUtils.d(TAG, "使用RGBA_8888格式录制");
            } catch (Exception e) {
                // 降级到YUV420格式
                streamManager.addFrameListener(cameraIndex, dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.YUV420_888, frameListener);
                dji.v5.utils.common.LogUtils.d(TAG, "降级使用YUV420_888格式录制");
            }
            
        } catch (Exception e) {
            dji.v5.utils.common.LogUtils.e(TAG, "启动视频流录制失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止录制视频流
     */
    private void stopRecordingToPhone() {
        dji.v5.utils.common.LogUtils.d(TAG, "stopRecordingToPhone 被调用");
        
        if (videoStreamRecorder != null && videoStreamRecorder.isRecording()) {
            videoStreamRecorder.stopRecording();
        }
        
        isRecordingToPhone = false;
        
        // 通知Model停止录屏
        widgetModel.getScreenRecordRequest().firstOrError()
            .observeOn(SchedulerProvider.ui())
            .subscribe(isRequesting -> {
                if (isRequesting) {
                    // 如果Model还认为在录屏，通知它停止
                    // 这里不需要做什么，因为我们已经直接停止了录制器
                }
            }, error -> {
                dji.v5.utils.common.LogUtils.e(TAG, "检查录屏状态失败: " + error.getMessage());
            });
    }
    
    /**
     * 将RGBA数据转换为Bitmap
     */
    private android.graphics.Bitmap rgbaToBitmap(byte[] rgba, int width, int height) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rgba);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            dji.v5.utils.common.LogUtils.e(TAG, "RGBA转Bitmap失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 显示无存储空间提示弹窗
     */
    private void showNoStorageDialog() {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
            builder.setMessage("飞机无可用储存空间，将保存截屏或录屏至本地，生成低画质作品。")
                    .setPositiveButton("我知道了", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setCancelable(true);
            
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            dji.v5.utils.common.LogUtils.e(TAG, "显示弹窗失败: " + e.getMessage());
        }
    }
    
    //endregion

    /**
     * Enum indicating storage error state
     */
    public enum StorageIconState {
        /**
         * The storage is slow
         */
        SLOW,

        /**
         * The storage is full
         */
        FULL,

        /**
         * The storage is not inserted
         */
        NOT_INSERTED
    }
}

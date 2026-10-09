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

package dji.v5.ux.cameracore.widget.cameracapture.shootphoto;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import dji.sdk.keyvalue.value.camera.CameraShootPhotoMode;
import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.sdk.keyvalue.value.camera.SDCardLoadState;
import dji.sdk.keyvalue.value.camera.SSDOperationState;
import dji.sdk.keyvalue.value.common.CameraLensType;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.interfaces.IMediaDataCenter;
import dji.v5.manager.interfaces.ICameraStreamManager;
import dji.v5.utils.common.LogUtils;
import dji.v5.ux.R;
import dji.v5.ux.cameracore.ui.ProgressRingView;
import dji.v5.ux.cameracore.util.CameraActionSound;
import dji.v5.ux.cameracore.util.CameraResource;
import dji.v5.ux.cameracore.widget.cameracontrols.RemoteControllerButtonDownModel;
import dji.v5.ux.core.base.DJISDKModel;
import dji.v5.ux.core.base.ICameraIndex;
import dji.v5.ux.core.base.SchedulerProvider;
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget;
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore;
import dji.v5.ux.core.util.UxErrorHandle;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Shoot Photo Widget
 * <p>
 * Widget can be used for shooting photo. The widget displays the current photo mode. It also
 * displays the storage state and errors associated with it.
 */
public class ShootPhotoWidget extends ConstraintLayoutWidget<Object> implements View.OnClickListener, ICameraIndex {
    //region Fields
    private static final String TAG = "ShootPhotoWidget";
    private ShootPhotoWidgetModel widgetModel;
    private RemoteControllerButtonDownModel buttonDownModel;
    private ProgressRingView borderProgressRingView;
    private ImageView centerImageView;
    private ImageView storageStatusOverlayImageView;
    private Drawable startShootPhotoDrawable;
    private Drawable stopShootPhotoDrawable;
    private Drawable phoneStorageDrawable;
    @ColorInt
    private int progressRingColor;
    private Map<StorageIconState, Drawable> storageInternalIconMap;
    private Map<StorageIconState, Drawable> storageSSDIconMap;
    private Map<StorageIconState, Drawable> storageSDCardIconMap;
    private CameraActionSound cameraActionSound;
    private boolean isUsingPhoneStorage = false;
    //endregion

    //region Lifecycle
    public ShootPhotoWidget(Context context) {
        super(context);
    }

    public ShootPhotoWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShootPhotoWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        inflate(context, R.layout.uxsdk_widget_shoot_photo, this);
        borderProgressRingView = findViewById(R.id.progress_ring_view_border);
        centerImageView = findViewById(R.id.image_view_center);
        storageStatusOverlayImageView = findViewById(R.id.image_view_storage_status_overlay);
        storageInternalIconMap = new HashMap<>();
        storageSSDIconMap = new HashMap<>();
        storageSDCardIconMap = new HashMap<>();
        cameraActionSound = new CameraActionSound(context);
        if (!isInEditMode()) {
            centerImageView.setOnClickListener(this);
            widgetModel = new ShootPhotoWidgetModel(DJISDKModel.getInstance(), ObservableInMemoryKeyedStore.getInstance());
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
        addReaction(widgetModel.isShootingPhoto().observeOn(SchedulerProvider.ui())
                .subscribe(this::onIsShootingPhotoChange, UxErrorHandle.logErrorConsumer(TAG, "isShootingPhoto: ")));
        addReaction(reactToCanStartOrStopShootingPhoto());
        addReaction(reactToPhotoStateAndPhotoStorageState());
        addReaction(buttonDownModel.isShutterButtonDownProcessor().toFlowable()
                .observeOn(SchedulerProvider.ui())
                .subscribe(aBoolean -> {
                    if (aBoolean == Boolean.TRUE) {
                        actionOnShootingPhoto();
                    }
                }));
        // 监听截屏请求
        addReaction(widgetModel.getScreenCaptureRequest()
                .observeOn(SchedulerProvider.ui())
                .subscribe(needCapture -> {
                    if (needCapture) {
                        captureScreenshot();
                    }
                }, UxErrorHandle.logErrorConsumer(TAG, "screenCaptureRequest: ")));
        // 监听存储状态变化，更新图标
        addReaction(widgetModel.getStorageAvailability()
                .observeOn(SchedulerProvider.ui())
                .subscribe(this::updateStorageIcon, UxErrorHandle.logErrorConsumer(TAG, "storageAvailability: ")));
    }

    @NonNull
    @Override
    public String getIdealDimensionRatioString() {
        return getContext().getResources().getString(R.string.uxsdk_widget_default_ratio);
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
            actionOnShootingPhoto();
        }
    }

    private void actionOnShootingPhoto() {
        if (!widgetModel.isPhotoMode()) {
            return;
        }

        Single<Boolean> stop = widgetModel.canStopShootingPhoto().firstOrError();
        Single<Boolean> start = widgetModel.canStartShootingPhoto().firstOrError();

        addDisposable(Single.zip(stop, start, Pair::new).flatMapCompletable(pairs -> {
                    if (pairs.first) {
                        return widgetModel.stopShootPhoto();
                    } else if (pairs.second) {
                        return widgetModel.startShootPhoto();
                    }
                    return Completable.complete();
                }).observeOn(SchedulerProvider.ui())
                .subscribe(() -> {
                }, UxErrorHandle.logErrorConsumer(TAG, "Start Stop Shoot Photo")));
    }
    //endregion

    //region private helpers
    private Drawable getCameraResourceDrawable(int resourceId) {
        return getResources().getDrawable(resourceId);
    }

    private void updateCameraForegroundResource(@NonNull CameraPhotoState cameraPhotoState, @NonNull CameraPhotoStorageState cameraPhotoStorageState) {
        Drawable foregroundDrawable = updateCameraActionSound(cameraPhotoState);

        if (cameraPhotoStorageState instanceof CameraSDPhotoStorageState) {
            CameraSDPhotoStorageState sdStorageState = (CameraSDPhotoStorageState) cameraPhotoStorageState;
            if (cameraPhotoStorageState.getStorageLocation() == CameraStorageLocation.SDCARD) {
                foregroundDrawable = updateResourceWithStorageInSDCard(sdStorageState);
            } else if (cameraPhotoStorageState.getStorageLocation() == CameraStorageLocation.INTERNAL) {
                foregroundDrawable = updateResourceWithStorageInternal(sdStorageState);
            }
        } else if (cameraPhotoStorageState instanceof CameraSSDPhotoStorageState) {
            CameraSSDPhotoStorageState ssdStorageState = (CameraSSDPhotoStorageState) cameraPhotoStorageState;
            if (ssdStorageState.getStorageOperationState() == SSDOperationState.NOT_FOUND) {
                foregroundDrawable = getSSDStorageIcon(StorageIconState.NOT_INSERTED);
            } else if (ssdStorageState.getStorageOperationState() == SSDOperationState.FULL) {
                foregroundDrawable = getSSDStorageIcon(StorageIconState.FULL);
            }
        }

        storageStatusOverlayImageView.setImageDrawable(foregroundDrawable);
    }

    private Drawable updateResourceWithStorageInternal(CameraSDPhotoStorageState sdStorageState) {
        Drawable foregroundDrawable = null;
        if (sdStorageState.getStorageOperationState() == SDCardLoadState.NOT_INSERTED) {
            foregroundDrawable = getInternalStorageIcon(StorageIconState.NOT_INSERTED);
        }
        return foregroundDrawable;
    }

    private Drawable updateResourceWithStorageInSDCard(CameraSDPhotoStorageState sdStorageState) {
        Drawable foregroundDrawable = null;
        if (sdStorageState.getStorageOperationState() == SDCardLoadState.NOT_INSERTED) {
            foregroundDrawable = getSDCardStorageIcon(StorageIconState.NOT_INSERTED);
        }
        return foregroundDrawable;
    }

    private Drawable updateCameraActionSound(CameraPhotoState cameraPhotoState) {
        Drawable foregroundDrawable = null;
        if (cameraPhotoState instanceof CameraPanoramaPhotoState) {
            foregroundDrawable = getCameraResourceDrawable(CameraResource.getPhotoModeImgResId(cameraPhotoState.getShootPhotoMode().value(),
                    ((CameraPanoramaPhotoState) cameraPhotoState).getPhotoPanoramaMode().value()));
            cameraActionSound.setShutterCount(CameraActionSound.ShutterSoundCount.ONE);
        } else if (cameraPhotoState instanceof CameraAEBPhotoState) {
            int photoCount = ((CameraAEBPhotoState) cameraPhotoState).getPhotoAEBCount().value();
            foregroundDrawable = getCameraResourceDrawable(CameraResource.getPhotoModeImgResId(cameraPhotoState.getShootPhotoMode().value(),
                    photoCount));
            cameraActionSound.setShutterCount(CameraActionSound.ShutterSoundCount.find(photoCount));
        } else if (cameraPhotoState instanceof CameraBurstPhotoState) {
            int photoCount = ((CameraBurstPhotoState) cameraPhotoState).getPhotoBurstCount().value();
            foregroundDrawable = getCameraResourceDrawable(CameraResource.getPhotoModeImgResId(cameraPhotoState.getShootPhotoMode().value(),
                    photoCount));
            cameraActionSound.setShutterCount(CameraActionSound.ShutterSoundCount.find(photoCount));
        } else if (cameraPhotoState instanceof CameraIntervalPhotoState) {
            foregroundDrawable = getCameraResourceDrawable(CameraResource.getPhotoModeImgResId(cameraPhotoState.getShootPhotoMode().value(),
                    ((CameraIntervalPhotoState) cameraPhotoState).getTimeIntervalInSeconds()));
            cameraActionSound.setShutterCount(CameraActionSound.ShutterSoundCount.ONE);
        } else {
            if (cameraPhotoState.getShootPhotoMode() != CameraShootPhotoMode.NORMAL) {
                foregroundDrawable = getCameraResourceDrawable(CameraResource.getPhotoModeImgResId(cameraPhotoState.getShootPhotoMode().value(),
                        0));
                cameraActionSound.setShutterCount(CameraActionSound.ShutterSoundCount.ONE);
            }
        }
        return foregroundDrawable;
    }

    private void onIsShootingPhotoChange(boolean isShootingPhoto) {

        borderProgressRingView.setIndeterminate(isShootingPhoto);
        if (isShootingPhoto) {
            cameraActionSound.playCapturePhoto();
        }
    }

    private Disposable reactToPhotoStateAndPhotoStorageState() {
        return Flowable.combineLatest(widgetModel.getCameraPhotoState(), widgetModel.getCameraStorageState(), Pair::new)
                .observeOn(SchedulerProvider.ui())
                .subscribe(values -> updateCameraForegroundResource(values.first, values.second),
                        UxErrorHandle.logErrorConsumer(TAG, "reactToPhotoStateAndPhotoStorageState "));
    }

    private Disposable reactToCanStartOrStopShootingPhoto() {
        return Flowable.combineLatest(widgetModel.canStartShootingPhoto(), widgetModel.canStopShootingPhoto(), Pair::new)
                .observeOn(SchedulerProvider.ui())
                .subscribe(values -> updateImages(values.first, values.second),
                        UxErrorHandle.logErrorConsumer(TAG, "reactToCanStartOrStopShootingPhoto: "));
    }

    private void checkAndUpdatePhotoStateAndPhotoStorageState() {
        if (!isInEditMode()) {
            addDisposable(Flowable.combineLatest(widgetModel.getCameraPhotoState(),
                            widgetModel.getCameraStorageState(),
                            Pair::new)
                    .firstOrError()
                    .observeOn(SchedulerProvider.ui())
                    .subscribe(values -> updateCameraForegroundResource(values.first, values.second),
                            UxErrorHandle.logErrorConsumer(TAG, "checkAndUpdatePhotoStateAndPhotoStorageState ")));
        }
    }

    private void checkAndUpdateCanStartOrStopShootingPhoto() {
        if (!isInEditMode()) {
            addDisposable(Flowable.combineLatest(
                            widgetModel.canStartShootingPhoto(),
                            widgetModel.canStopShootingPhoto(),
                            Pair::new)
                    .firstOrError()
                    .observeOn(SchedulerProvider.ui())
                    .subscribe(values -> updateImages(values.first, values.second),
                            UxErrorHandle.logErrorConsumer(TAG, "checkAndUpdateCanStartOrStopShootingPhoto: ")));
        }
    }

    private void updateImages(boolean canStartShootingPhoto, boolean canStopShootingPhoto) {
        if (!canStopShootingPhoto) {
            enableAction(canStartShootingPhoto);
            // 根据存储状态选择图标
            if (isUsingPhoneStorage) {
                centerImageView.setImageDrawable(phoneStorageDrawable);
            } else {
                centerImageView.setImageDrawable(startShootPhotoDrawable);
            }
        } else {
            enableAction(true);
            centerImageView.setImageDrawable(stopShootPhotoDrawable);
        }

        storageStatusOverlayImageView.setVisibility(canStopShootingPhoto ? View.GONE : View.VISIBLE);
        borderProgressRingView.setRingColor(progressRingColor);
    }
    
    /**
     * 更新存储图标
     * @param hasAvailableStorage true表示有可用的飞机存储（SD卡或内部存储），false表示需要使用手机存储
     */
    private void updateStorageIcon(boolean hasAvailableStorage) {
        isUsingPhoneStorage = !hasAvailableStorage;
        LogUtils.d(TAG, "存储状态更新 - 有可用存储: " + hasAvailableStorage + ", 使用手机存储: " + isUsingPhoneStorage);
        
        // 更新图标显示
        checkAndUpdateCanStartOrStopShootingPhoto();
    }

    private void enableAction(boolean isEnabled) {
        centerImageView.setEnabled(isEnabled);
    }

    private void initDefaults() {
        startShootPhotoDrawable = getResources().getDrawable(R.drawable.uxsdk_shape_circle);
        stopShootPhotoDrawable = getResources().getDrawable(R.drawable.uxsdk_ic_shutter_stop);
        phoneStorageDrawable = getResources().getDrawable(R.drawable.uxsdk_ic_phone_storage);
        setProgressRingColor(Color.WHITE);
        setInternalStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_internal_storage_not_inserted);
        setInternalStorageIcon(StorageIconState.SLOW, R.drawable.uxsdk_ic_internal_storage_slow);
        setInternalStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_internal_storage_full);
        setSDCardStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_sdcard_not_inserted);
        setSDCardStorageIcon(StorageIconState.SLOW, R.drawable.uxsdk_ic_sdcard_slow);
        setSDCardStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_sdcard_full);
        setSSDStorageIcon(StorageIconState.NOT_INSERTED, R.drawable.uxsdk_ic_ssd_not_inserted);
        setSSDStorageIcon(StorageIconState.FULL, R.drawable.uxsdk_ic_ssd_full);
    }

    private void initAttributes(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShootPhotoWidget);
        updateCameraSource(ComponentIndexType.find(typedArray.getInt(R.styleable.ShootPhotoWidget_uxsdk_cameraIndex, 0)), CameraLensType.UNKNOWN);

        setForegroundIconBackground(typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_foregroundIconBackground));
        setProgressRingColor(typedArray.getColor(R.styleable.ShootPhotoWidget_uxsdk_progressRingColor, Color.WHITE));

        initShootPhotoDrawable(typedArray);
        initInternalStorageIcon(typedArray);
        initSSDStorageIcon(typedArray);
        initSDCardStorageIcon(typedArray);
        typedArray.recycle();
    }

    private void initSSDStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdNotInsertedIcon) != null) {
            setSSDStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdFullIcon) != null) {
            setSSDStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdSlowIcon) != null) {
            setSSDStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_ssdSlowIcon));
        }
    }

    private void initSDCardStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardNotInsertedIcon) != null) {
            setSDCardStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardFullIcon) != null) {
            setSDCardStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardSlowIcon) != null) {
            setSDCardStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_sdCardSlowIcon));
        }
    }

    private void initInternalStorageIcon(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageNotInsertedIcon) != null) {
            setInternalStorageIcon(StorageIconState.NOT_INSERTED, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageNotInsertedIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageFullIcon) != null) {
            setInternalStorageIcon(StorageIconState.FULL, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageFullIcon));
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageSlowIcon) != null) {
            setInternalStorageIcon(StorageIconState.SLOW, typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_internalStorageSlowIcon));
        }
    }

    private void initShootPhotoDrawable(TypedArray typedArray) {
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_shootPhotoStartIcon) != null) {
            startShootPhotoDrawable = typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_shootPhotoStartIcon);
        }
        if (typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_shootPhotoStopIcon) != null) {
            stopShootPhotoDrawable = typedArray.getDrawable(R.styleable.ShootPhotoWidget_uxsdk_shootPhotoStopIcon);
        }
    }
    //endregion

    //region customizations

    /**
     * Get the current start shooting photo icon
     *
     * @return Drawable currently used
     */
    public Drawable getStartShootPhotoDrawable() {
        return startShootPhotoDrawable;
    }

    /**
     * Set the start shoot photo icon
     *
     * @param resourceId to be used
     */
    public void setStartShootPhotoDrawable(@DrawableRes int resourceId) {
        setStartShootPhotoDrawable(getResources().getDrawable(resourceId));
    }

    /**
     * Set the start shoot photo icon
     *
     * @param drawable to be used
     */
    public void setStartShootPhotoDrawable(@Nullable Drawable drawable) {
        startShootPhotoDrawable = drawable;
        checkAndUpdateCanStartOrStopShootingPhoto();
    }

    /**
     * Get the current stop shooting photo icon
     *
     * @return Drawable currently used
     */
    @Nullable
    public Drawable getStopShootPhotoDrawable() {
        return stopShootPhotoDrawable;
    }

    /**
     * Set stop shoot photo icon
     *
     * @param resourceId to be used
     */
    public void setStopShootPhotoDrawable(@DrawableRes int resourceId) {
        setStopShootPhotoDrawable(getResources().getDrawable(resourceId));
    }

    /**
     * Set stop shoot photo icon
     *
     * @param drawable to be used
     */
    public void setStopShootPhotoDrawable(@Nullable Drawable drawable) {
        stopShootPhotoDrawable = drawable;
        checkAndUpdateCanStartOrStopShootingPhoto();
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
        checkAndUpdatePhotoStateAndPhotoStorageState();
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
        checkAndUpdatePhotoStateAndPhotoStorageState();
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
        checkAndUpdatePhotoStateAndPhotoStorageState();
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
     * Get current background of foreground icon
     *
     * @return Drawable being used
     */
    @Nullable
    public Drawable getForegroundIconBackground() {
        return storageStatusOverlayImageView.getBackground();
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
     * Get the color of the progress ring
     *
     * @return integer representing color
     */
    @ColorInt
    public int getProgressRingColor() {
        return progressRingColor;
    }

    /**
     * Set the color of the progress ring
     *
     * @param color integer value
     */
    public void setProgressRingColor(@ColorInt int color) {
        progressRingColor = color;
        checkAndUpdateCanStartOrStopShootingPhoto();
    }
    
    /**
     * 从DJI视频流中捕获当前帧并保存到手机
     * 使用DJI SDK的ICameraStreamManager来获取实时视频帧
     * 优化：使用最高质量视频流 + PNG无损格式
     */
    private void captureScreenshot() {
        try {
            LogUtils.d(TAG, "开始从视频流捕获帧（最高质量模式）");
            
            // 获取MediaDataCenter的CameraStreamManager
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();
            ICameraStreamManager streamManager = mediaDataCenter.getCameraStreamManager();
            
            if (streamManager == null) {
                LogUtils.e(TAG, "CameraStreamManager为null");
                return;
            }
            
            // 获取当前相机索引对应的视频流
            ComponentIndexType cameraIndex = widgetModel.getCameraIndex();
            CameraLensType lensType = widgetModel.getLensType();
            
            LogUtils.d(TAG, "相机索引: " + cameraIndex + ", 镜头类型: " + lensType);
            
            // 添加帧监听器来捕获一帧
            ICameraStreamManager.CameraFrameListener frameListener = new ICameraStreamManager.CameraFrameListener() {
                @Override
                public void onFrame(byte[] bytes, int offset, int length, int width, int height, ICameraStreamManager.FrameFormat format) {
                    try {
                        LogUtils.d(TAG, "收到视频帧: " + width + "x" + height + ", 格式: " + format);
                        
                        // 将YUV数据转换为Bitmap（使用最高质量转换）
                        Bitmap bitmap = null;
                        if (format == ICameraStreamManager.FrameFormat.YUV420_888) {
                            bitmap = yuv420ToBitmapHighQuality(bytes, width, height);
                        } else if (format == ICameraStreamManager.FrameFormat.RGBA_8888) {
                            bitmap = rgbaToBitmap(bytes, width, height);
                        }
                        
                        if (bitmap != null) {
                            LogUtils.d(TAG, "成功转换为Bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                            
                            // 播放拍照音效
                            cameraActionSound.playCapturePhoto();
                            
                            Bitmap finalBitmap = bitmap;
                            // 在后台线程保存（使用PNG无损格式）
                            new Thread(() -> {
                                boolean success = widgetModel.saveBitmapToPhoneHighQuality(finalBitmap);
                                if (success) {
                                    // 保存成功后显示提示弹窗
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        showNoStorageDialog();
                                    });
                                }
                                finalBitmap.recycle();
                            }).start();
                            
                            // 移除监听器(只捕获一帧)
                            streamManager.removeFrameListener(this);
                        } else {
                            LogUtils.e(TAG, "转换Bitmap失败");
                        }
                    } catch (Exception e) {
                        LogUtils.e(TAG, "处理视频帧失败: " + e.getMessage());
                    }
                }
            };
            
            // 优化1：尝试使用RGBA格式（通常质量更高）
            // 如果RGBA不可用，会自动降级到YUV420
            try {
                streamManager.addFrameListener(cameraIndex, ICameraStreamManager.FrameFormat.RGBA_8888, frameListener);
                LogUtils.d(TAG, "使用RGBA_8888格式（最高质量）");
            } catch (Exception e) {
                // 降级到YUV420格式
                streamManager.addFrameListener(cameraIndex, ICameraStreamManager.FrameFormat.YUV420_888, frameListener);
                LogUtils.d(TAG, "降级使用YUV420_888格式");
            }
            
            // 5秒后自动移除监听器(防止内存泄漏)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    streamManager.removeFrameListener(frameListener);
                    LogUtils.d(TAG, "自动移除帧监听器");
                } catch (Exception e) {
                    LogUtils.e(TAG, "移除监听器失败: " + e.getMessage());
                }
            }, 5000);
            
        } catch (Exception e) {
            LogUtils.e(TAG, "捕获视频帧失败: " + e.getMessage());
        }
    }
    
    /**
     * 将YUV420数据转换为Bitmap（高质量版本）
     * 优化：使用100%质量的JPEG中间转换
     */
    private Bitmap yuv420ToBitmapHighQuality(byte[] yuv, int width, int height) {
        try {
            // 使用Android的YuvImage类来转换
            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                yuv, 
                android.graphics.ImageFormat.NV21, 
                width, 
                height, 
                null
            );
            
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            // 优化2：使用100%质量压缩（原来是90）
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, out);
            byte[] imageBytes = out.toByteArray();
            
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            LogUtils.e(TAG, "YUV转Bitmap失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 将RGBA数据转换为Bitmap
     */
    private Bitmap rgbaToBitmap(byte[] rgba, int width, int height) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rgba);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            LogUtils.e(TAG, "RGBA转Bitmap失败: " + e.getMessage());
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
            LogUtils.e(TAG, "显示弹窗失败: " + e.getMessage());
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

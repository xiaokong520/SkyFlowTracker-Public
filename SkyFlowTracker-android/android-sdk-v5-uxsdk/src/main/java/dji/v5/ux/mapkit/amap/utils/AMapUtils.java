package dji.v5.ux.mapkit.amap.utils;

import android.content.ContentProvider;
import android.content.Context;
import android.util.Log;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.PolygonOptions;
import com.amap.api.maps.model.PolylineOptions;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdate;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory;
import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor;
import dji.v5.ux.mapkit.core.models.DJICameraPosition;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.DJILatLngBounds;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygonOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;
import dji.v5.ux.mapkit.core.utils.DJIGpsUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by joeyang on 2/10/18.
 */

public class AMapUtils {

    private AMapLocationClient mLocationClient = null;
    private AMapLocationListener mLocationListener = null;

    private AMapUtils(){}

    public static final LatLng fromDJILatLng(DJILatLng latLng) {
        DJILatLng gcjLatLng = DJIGpsUtils.wgs2gcjInChina(latLng);
        return new LatLng(gcjLatLng.getLatitude(), gcjLatLng.getLongitude());
    }

    public static final DJILatLng fromLatLng(LatLng latLng) {
        DJILatLng ll = new DJILatLng(latLng.latitude, latLng.longitude);
        DJILatLng transformed = DJIGpsUtils.gcj2wgsInChina(ll);
        return transformed;
    }

    public static final CameraUpdate fromDJICameraUpdate(DJICameraUpdate cameraUpdate) {
        CameraUpdate u = CameraUpdateFactory.newLatLng(new LatLng(0.0, 0.0));
        if (cameraUpdate instanceof DJICameraUpdateFactory.CameraBoundsUpdate) {
            final DJICameraUpdateFactory.CameraBoundsUpdate boundsUpdate
                    = (DJICameraUpdateFactory.CameraBoundsUpdate) cameraUpdate;
            int width = boundsUpdate.getWidth();
            int height = boundsUpdate.getHeight();
            int padding = boundsUpdate.getPadding();
            int paddingLeft = boundsUpdate.getPaddingLeft();
            int paddingRight = boundsUpdate.getPaddingRight();
            int paddingBottom = boundsUpdate.getPaddingBottom();
            int paddingTop = boundsUpdate.getPaddingTop();
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            DJILatLngBounds bounds = boundsUpdate.getBounds();
            DJILatLng northeast = bounds.getNortheast();
            DJILatLng southwest = bounds.getSouthwest();

            builder.include(fromDJILatLng(northeast))
                    .include(fromDJILatLng(southwest));

            if (width == 0 || height == 0) {
                u = CameraUpdateFactory.newLatLngBoundsRect(builder.build(),
                        padding + paddingLeft,
                        padding + paddingRight,
                        padding + paddingTop,
                        padding + paddingBottom);
            } else {
                u = CameraUpdateFactory.newLatLngBounds(builder.build(), width, height, padding);
            }
        } else if (cameraUpdate instanceof DJICameraUpdateFactory.CameraPositionUpdate) {
            final DJICameraUpdateFactory.CameraPositionUpdate positionUpdate
                    = (DJICameraUpdateFactory.CameraPositionUpdate) cameraUpdate;
            CameraPosition p;
            LatLng target = fromDJILatLng(positionUpdate.getTarget());
            p = new CameraPosition.Builder().target(target)
                    .zoom(positionUpdate.getZoom())
                    .tilt(positionUpdate.getTilt())
                    .bearing(positionUpdate.getBearing())
                    .build();
            u = CameraUpdateFactory.newCameraPosition(p);
        }
        return u;
    }

    public static final DJICameraPosition fromCameraPosition(CameraPosition cameraPosition) {
        DJICameraPosition.Builder builder = new DJICameraPosition.Builder();
        LatLng target = cameraPosition.target;
        builder.target(fromLatLng(target))
                .zoom(cameraPosition.zoom)
                .tilt(cameraPosition.tilt)
                .bearing(cameraPosition.bearing);
        return builder.build();
    }

    public static final BitmapDescriptor fromDJIBitmapDescriptor(DJIBitmapDescriptor descriptor) {
        BitmapDescriptor bitmapDescriptor;
        String path = descriptor.getPath();
        switch (descriptor.getType()) {
            case BITMAP:
                bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(descriptor.getBitmap());
                break;
            case PATH_ABSOLUTE:
                bitmapDescriptor = BitmapDescriptorFactory.fromPath(path);
                break;
            case PATH_ASSET:
                bitmapDescriptor = BitmapDescriptorFactory.fromAsset(path);
                break;
            case PATH_FILEINPUT:
                bitmapDescriptor = BitmapDescriptorFactory.fromFile(path);
                break;
            case RESOURCE_ID:
                bitmapDescriptor = BitmapDescriptorFactory.fromResource(descriptor.getResourceId());
                break;
            case VIEW:
                bitmapDescriptor = BitmapDescriptorFactory.fromView(descriptor.getView());
                break;
            default:
                throw new AssertionError();
        }
        return bitmapDescriptor;
    }

    public static final PolygonOptions fromDJIPolygonOptions(DJIPolygonOptions options) {
        PolygonOptions result = new PolygonOptions();
        List<LatLng> points = new ArrayList<>();
        for (DJILatLng point : options.getPoints()) {
            points.add(fromDJILatLng(point));
        }
        result.strokeWidth(options.getStrokeWidth())
                .zIndex(options.getZIndex())
                .strokeColor(options.getStrokeColor())
                .fillColor(options.getFillColor())
                .visible(options.isVisible())
                .addAll(points);
        return result;
    }

    public static final PolylineOptions fromDJIPolylineOptions(DJIPolylineOptions options) {
        PolylineOptions result = new PolylineOptions();
        List<LatLng> points = new ArrayList<>();
        List<DJILatLng> originPoints = options.getPoints();
        // 暂弃用此航点抽稀算法 缩放级别较大时，航迹绘不连贯，弯弯曲曲, 并且高德之外的地图都没有用
//        List<DJILatLng> compressPoints = originPoints;
//        // 若首尾两个点坐标相同，使用道格拉斯算法会有问题，需要排除这种情况
//        if (!originPoints.isEmpty() && !originPoints.get(0).equals(originPoints.get(originPoints.size() - 1))) {
//            compressPoints = DouglasUtils.compress(originPoints, 0.5);
//        }
        for (DJILatLng point : originPoints) {
            points.add(fromDJILatLng(point));
        }
        result.width(options.getWidth())
                .zIndex(options.getZIndex())
                .color(options.getColor())
                .visible(options.isVisible())
                .geodesic(options.isGeodesic())
                .addAll(points);
        // 高德地图不能设置虚线间隔和长短， 只能选择dot类型：圆形或方形, 这里默认选择方形
        if (options.isDashed()) {
            result.setDottedLine(true).setDottedLineType(PolylineOptions.DOTTEDLINE_TYPE_SQUARE);
        }
        if (options.isEnableTexture() && options.getBitmapDescriptor() != null){
            result.setUseTexture(true);
            BitmapDescriptor bitmapDescriptor = fromDJIBitmapDescriptor(options.getBitmapDescriptor());
            result.setCustomTexture(bitmapDescriptor);
        }
        return result;
    }

    //用于初始化定位信息
    public static AMapLocationClient initLocation(Context context, DJIMap map){
        Log.d("AMapUtils", "初始化地图定位");
        FileLogger.d("AMapUtils", "初始化地图定位");
        try {
            //初始化定位
            AMapLocationClient locationClient = new AMapLocationClient(context);
            //标记是否首次定位
            final boolean[] isFirstLocation = {true};
            //存储定位蓝点Maker
            final DJIMarker[] locationMaker = {null};
            //记录GPS失败次数
            final int[] gpsFailCount = {0};
            //标记当前定位模式
            //优先使用GPS
            final AMapLocationClientOption.AMapLocationMode[] currentMode = {
              AMapLocationClientOption.AMapLocationMode.Device_Sensors
            };
            //创建定位配置
            AMapLocationClientOption locationOption = new AMapLocationClientOption();
            locationOption.setLocationMode(currentMode[0]);
            locationOption.setOnceLocation(false);
            locationOption.setInterval(2000); //2秒更新一次
            locationOption.setNeedAddress(false);
            locationOption.setHttpTimeOut(2000);
            locationOption.setGpsFirst(true); //优先GPS

            //设置定位回调
            AMapLocationListener locationListener = aMapLocation -> {
                FileLogger.d("AMapUtils", "定位回调被触发");
                if (aMapLocation != null){
                    int errorCode = aMapLocation.getErrorCode();
                    FileLogger.d("AMapUtils", "定位错误码: " + errorCode);
                    if (errorCode == 0){
                        //定位成功
                        double latitude = aMapLocation.getLatitude();
                        double longitude = aMapLocation.getLongitude();
                        int locationType = aMapLocation.getLocationType();
                        Log.d("AMapUtils", "定位更新: " + latitude + ", " + longitude + "定位类型：" + locationType);
                        //获取定位类型描述
                        String locationTypeDesc = getLocationTypeDesc(locationType);
                        Log.d("AMapUtils", "定位类型: " + locationTypeDesc);
                        FileLogger.d("AMapUtils", "定位成功: lat=" + latitude + ", lng=" + longitude
                                + ", 类型=" + locationTypeDesc + "(" + locationType + ")");
                        //重置GPS失败计数
                        gpsFailCount[0] = 0;
                        //创建DJI位置对象
                        DJILatLng currentLocation = new DJILatLng(latitude,longitude);
                        //更新或创建定位蓝点Maker
                        if (locationMaker[0] == null){
                            //首次创建Maker
                            DJIMarkerOptions makerOptions = new DJIMarkerOptions();
                            makerOptions.position(currentLocation);
                            makerOptions.title("手机位置：" + locationTypeDesc);
                            makerOptions.anchor(0.5f,0.5f); //设置锚点为中心
                            makerOptions.zIndex(100); //设置层数

                            locationMaker[0] = map.addMarker(makerOptions);
                            Log.d("AMapUtils", "创建定位蓝点Maker");
                            FileLogger.d("AMapUtils", "创建定位蓝点 Marker: " + (locationMaker[0] != null ? "成功" : "失败"));
                        }else {
                            //更新蓝点Maker位置
                            locationMaker[0].setPosition(currentLocation);
                            locationMaker[0].setTitle("手机位置：" + locationTypeDesc);
                            Log.d("AMapUtils", "更新定位蓝点Maker位置");
                            FileLogger.d("AMapUtils", "更新 Marker 位置: " + latitude + ", " + longitude);
                        }
                        //首次定位时移动到当前位置
                        if (isFirstLocation[0]){
                            isFirstLocation[0] = false;
                            //创建DJI相机位置对象
                            DJICameraPosition cameraPosition = new DJICameraPosition.Builder()
                                    .target(currentLocation)
                                    .zoom(15f)
                                    .tilt(0f)
                                    .bearing(0f)
                                    .build();
                            //将camera移动到当前位置
                            map.animateCamera(DJICameraUpdateFactory.newCameraPosition(cameraPosition));
                            Log.d("AMapUtils", "移动到当前位置");
                            FileLogger.d("AMapUtils", "首次定位，地图已移动");
                        }

                    }else {
                        //定位失败
                        String errorInfo = aMapLocation.getErrorInfo();
                        Log.e("AMapUtils", "定位失败: errorCode=" + errorCode + ", info=" + errorInfo);
                        FileLogger.e("AMapUtils", "定位失败: errorCode=" + errorCode + ", info=" + errorInfo);

                        // 错误码8：经纬度错误，通常是定位服务未就绪或权限问题，跳过处理
                        if (errorCode == 8) {
                            Log.w("AMapUtils", "定位返回无效坐标，等待下次定位");
                            FileLogger.w("AMapUtils", "错误码8：坐标无效，跳过本次定位");
                            return;
                        }

                        // 错误码14：GPS信号差，尝试切换到高精度定位
                        if (errorCode == 14){
                            gpsFailCount[0]++;
                            Log.w("AMapUtils", "GPS信号差，进行第" + gpsFailCount[0] + "次重试");
                            FileLogger.w("AMapUtils", "GPS信号差，失败次数: " + gpsFailCount[0]);
                            try {
                                FileLogger.w("AMapUtils", "切换到高精度定位模式");
                                if (gpsFailCount[0] >= 1 && currentMode[0] == AMapLocationClientOption.AMapLocationMode.Device_Sensors){
                                    //更新定位配置
                                    currentMode[0] = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy;
                                    AMapLocationClientOption highAccOption = new AMapLocationClientOption();
                                    highAccOption.setLocationMode(currentMode[0]);
                                    highAccOption.setOnceLocation(false);
                                    highAccOption.setInterval(2000);
                                    highAccOption.setNeedAddress(false);
                                    highAccOption.setHttpTimeOut(20000);
                                    highAccOption.setGpsFirst(false);
                                    // 重启定位以应用新策略
                                    locationClient.stopLocation();
                                    locationClient.setLocationOption(highAccOption);
                                    locationClient.startLocation();
                                    FileLogger.d("AMapUtils", "已切换到高精度模式并重启定位");
                                }else {
                                    // 小于阈值时仅重启定位以重试 GPS
                                    locationClient.stopLocation();
                                    // 重新使用原始配置（currentMode[0] 未变）重新启动
                                    AMapLocationClientOption retryOption = new AMapLocationClientOption();
                                    retryOption.setLocationMode(currentMode[0]);
                                    retryOption.setOnceLocation(false);
                                    retryOption.setInterval(2000);
                                    retryOption.setNeedAddress(false);
                                    retryOption.setHttpTimeOut(20000);
                                    retryOption.setGpsFirst(true);
                                    locationClient.setLocationOption(retryOption);
                                    locationClient.startLocation();
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                                Log.e("AMapUtils", "切换定位模式异常: " + e.getMessage());
                                FileLogger.e("AMapUtils", "切换定位模式异常: " + e.getMessage());
                            }
                        }
                    }
                }else {
                    Log.w("AMapUtils", "定位回调: aMapLocation 为 null");
                    FileLogger.w("AMapUtils", "定位回调: aMapLocation 为 null");
                }
            };
            //启动定位
            locationClient.setLocationOption(locationOption);
            locationClient.setLocationListener(locationListener);
            locationClient.startLocation();
            Log.d("AMapUtils", "开始定位");
            FileLogger.d("AMapUtils", "定位客户端已启动，模式: " + currentMode[0]);
            return locationClient;
        }catch (Exception e){
            Log.e("AMapUtils", "initLocation: " + e.getMessage());
            FileLogger.e("AMapUtils", "initLocation 异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 添加辅助方法：获取定位类型描述
    private static String getLocationTypeDesc(int locationType) {
        switch (locationType) {
            case 1: return "GPS";
            case 2: return "前次定位";
            case 4: return "缓存";
            case 5: return "WiFi";
            case 6: return "基站";
            case 8: return "离线";
            case 9: return "最后位置";
            default: return "未知";
        }
    }
}
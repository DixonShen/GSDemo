package com.dji.GSDemo.GaodeMap;

import com.amap.api.maps2d.model.LatLng;


/**
 * Created by DixonShen on 2016/7/13.
 */
public class GCJ2WGS {
    private double PI = 3.14159265358979324;
    private double x_pi = 0;

    public GCJ2WGS(){
        this.x_pi = PI * 3000.0 / 180.0;
    }

    /**
     * WGS-84 to GCJ-02
     * @param point
     * @return
     */
    public LatLng gcj_encrypt(LatLng point){
        if (this.outOfChina(point))
            return point;
        LatLng dPoint = this.delta(point);
        LatLng gPoint = new LatLng(point.latitude+dPoint.latitude,point.longitude+dPoint.longitude);
        return gPoint;
    }

    /**
     * GCJ-02 to WGS-84
     * @param point
     * @return
     */
    public LatLng gcj2wgsExact(LatLng point){
        double initDelta = 0.01;
        double threhold = 0.000000001;
        double dLat = initDelta;
        double dLng = initDelta;
        double mLat = point.latitude - dLat;
        double mLng = point.longitude - dLng;
        double pLat = point.latitude + dLat;
        double pLng = point.longitude + dLng;
        double wgsLat = 0;
        double wgsLng = 0;
        int i =0;
        while (true){
            wgsLat = (mLat + pLat) / 2;
            wgsLng = (mLng + pLng) / 2;
            LatLng temp = this.gcj_encrypt(new LatLng(wgsLat,wgsLng));
            dLat = temp.latitude - point.latitude;
            dLng = temp.longitude - point.longitude;
            if ((Math.abs(dLat) < threhold) && (Math.abs(dLng) < threhold)){
                break;
            }
            if (dLat > 0)
                pLat = wgsLat;
            else
                mLat = wgsLat;
            if (dLng > 0)
                pLng = wgsLng;
            else
                mLng = wgsLng;

            if ( ++i > 10000) break;
        }
        return new LatLng(wgsLat,wgsLng);
    }

    /**
     * calculate two points' distance
     * @param latA
     * @param lngA
     * @param latB
     * @param lngB
     * @return
     */
    public double distance(double latA, double lngA, double latB, double lngB){
        double earthR = 6371000.0;
        double x = Math.cos(latA * this.PI / 180.0) * Math.cos(latB * this.PI / 180.0) *
                Math.cos((lngA - lngB) * this.PI / 180.0);
        double y = Math.sin(latA * this.PI / 180.0) * Math.sin(latB * this.PI / 180.0);
        double s = x + y;
        if (s > 1) s = 1 ;
        if (s < -1) s = -1;
        double alpha = Math.acos(s);
        double distance = alpha * earthR;
        return distance;
    }

    private LatLng delta(LatLng point){
        // Krasovsky 1940
        //
        // a = 6378245.0, 1/f = 298.3
        // b = a * (1 - f)
        // ee = (a^2 - b^2) / a^2;
        double a = 6378245.0;  //a: 卫星椭球坐标投影到平面地图坐标系的投影因子。
        double ee = 0.00669342162296594323; // ee: 椭球的偏心率。
        double dLat = this.transformLat(point.longitude - 105.0, point.latitude - 35.0);
        double dLng = this.transformLng(point.longitude - 105.0, point.latitude - 35.0);
        double radLat = point.latitude / 180.0 * this.PI;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1-ee)) / (magic * sqrtMagic) * this.PI);
        dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * this.PI);
        return new LatLng(dLat,dLng);
    }

    /**
     *
     * @param point
     * @return
     */
    private boolean outOfChina(LatLng point){

        if (point.longitude < 72.004 || point.longitude > 137.8347)
            return true;
        if (point.latitude < 0.8293 || point.latitude > 55.8271)
            return true;
        return false;
    }

    private double transformLat(double x, double y){

        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * this.PI) + 20.0 * Math.sin(2.0 * x * this.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * this.PI) + 40.0 * Math.sin(y / 3.0 * this.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * this.PI) + 320.0 * Math.sin(y * this.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private double transformLng(double x, double y){

        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * this.PI) + 20.0 * Math.sin(2.0 * x * this.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * this.PI) + 40.0 * Math.sin(x / 3.0 * this.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * this.PI) + 300.0 * Math.sin(x * this.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }
}

package com.dji.GSDemo.GaodeMap;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.CoordinateConverter;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.FlightController.DJIFlightControllerDelegate;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;

public class MainActivity extends FragmentActivity implements View.OnClickListener,AMap.OnMapClickListener,
        DJIMissionManager.MissionProgressStatusCallback, DJIBaseComponent.DJICompletionCallback{

    protected static final String TAG = "MainActivity";

    private MapView mapView;
    private AMap aMap;

    private Button locate, add, clear;
    private Button config, prepare, start, stop;

    private boolean isAdd = false;
    private final Map<Integer, Marker> aMarkers = new ConcurrentHashMap<Integer, Marker>();

    private double droneLocationLat = 90, droneLocationLng = 180;
    private Marker droneMarker = null;
    private DJIFlightController mFlightController;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;
    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.NoAction;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;
    private DJIWaypointMission mWaypointMission;
    private DJIMissionManager mMissionManager;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
        initMissionManager();
    }
    @Override
    protected void onPause(){
        super.onPause();
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    /**
     * @Description : RETURN BTN RESPONSE FUNCTION
     * @param view
     */
    public void onReturn(View view){
        Log.d(TAG,"onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI(){
        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        prepare = (Button) findViewById(R.id.prepare);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        prepare.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
    }

    private void initMapView(){
        if (aMap == null){
            aMap = mapView.getMap();
            aMap.setOnMapClickListener(this);  // add the listener for click for amap object
        }
        LatLng suzhou = new LatLng(31.3,120.6);
        aMap.addMarker(new MarkerOptions().position(suzhou).title("Marker in Suzhou"));
        aMap.moveCamera(CameraUpdateFactory.newLatLng(suzhou));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        //When the compile and target version is higher than 22, please request the
        //following permissions at runtime to ensure the
        //SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE,Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    ,1);
        }

        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);

        initMapView();
        initUI();
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange(){
        initFlightController();
        initMissionManager();
    }

    private void initMissionManager(){
        DJIBaseProduct product = DJIDemoApplication.getProductInstance();
        if (product == null || !product.isConnected()){
            setResultToToast("Product Not Connected");
            mMissionManager = null;
            return;
        }else {
            setResultToToast("Product Connected");
            mMissionManager = product.getMissionManager();
            mMissionManager.setMissionProgressStatusCallback(this);
            mMissionManager.setMissionExecutionFinishedCallback(this);
        }
        mWaypointMission = new DJIWaypointMission();
    }
    @Override
    public void missionProgressStatus(DJIMission.DJIMissionProgressStatus progressStatus){

    }
    @Override
    public void onResult(DJIError error){
        setResultToToast("Execution finished: " + (error == null ? "Success" : error.getDescription()));
    }

    private void initFlightController(){

        setResultToToast("init FlightController!");

        DJIBaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()){
            if (product instanceof DJIAircraft){
                setResultToToast("Connected");
                mFlightController = ((DJIAircraft) product).getFlightController();
            }
        }
        else {
            setResultToToast("Disconnected");
        }
        if (mFlightController != null){
            mFlightController.setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {
                @Override
                public void onResult(DJIFlightControllerDataType.DJIFlightControllerCurrentState state) {
                    droneLocationLat = state.getAircraftLocation().getLatitude();
                    droneLocationLng = state.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                }
            });
        }
        else {
            setResultToToast("cannot access");
        }
    }

    public static boolean checkGpsCoordinates(double latitude, double longitude){
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void updateDroneLocation(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        pos = WGS2GCJ(pos);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null){
                    droneMarker.remove();
                }
                if (checkGpsCoordinates(droneLocationLat,droneLocationLng)){
                    droneMarker = aMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);
        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId){
                //TODO Auto-generated method stub
                Log.d(TAG, "Select Speed finish");
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                }else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                }else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }
        });
        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId){
                //TODO Auto-gengerated method stub
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.NoAction;
                }else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoHome;
                }else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.AutoLand;
                }else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoFirstWaypoint;
                }
            }
        });
        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId){
                //TODO Auto-generated method stub
                Log.d(TAG, "Select heading");
                if (checkedId == R.id.headingNext){
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;
                }else if (checkedId  == R.id.headingInitDirec){
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.UsingInitialDirection;
                }else if (checkedId == R.id.headingRC){
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.ControlByRemoteController;
                }else if (checkedId == R.id.headingWP){
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.UsingWaypointHeading;
                }
            }
        });
        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){
                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefault(altitudeString));
                        Log.e(TAG,"altitude"+altitude);
                        Log.e(TAG,"speed"+mSpeed);
                        Log.e(TAG,"mFinishedAction"+mFinishedAction);
                        Log.e(TAG,"mHeadingMode"+mHeadingMode);
                        configWayPointMission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }
    String nulltoIntegerDefault(String value){
        if (!isIntValue(value))  value = "0";
        return value;
    }
    boolean isIntValue(String val){
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e){
            return false;
        }
        return true;
    }

    private void configWayPointMission(){
        if (mWaypointMission != null){
            mWaypointMission.finishedAction = mFinishedAction;
            mWaypointMission.headingMode = mHeadingMode;
            mWaypointMission.autoFlightSpeed = mSpeed;
            if (mWaypointMission.waypointsList.size() > 0){
                for (int i=0; i<mWaypointMission.waypointsList.size();i++){
                    mWaypointMission.getWaypointAtIndex(i).altitude = altitude;
                }
                setResultToToast("Set Waypoint altitude success");
            }
        }
    }

    @Override
    public void onClick(View v){
        //TODO Auto-generated method stub
        switch (v.getId()){
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate();
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aMap.clear();
                    }
                });
                if (mWaypointMission != null){
                    mWaypointMission.removeAllWaypoints(); // Remove all the waypoints added to the task
                }
                break;
            }
            case R.id.prepare:{
                prepareWaypointMission();
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    private void enableDisableAdd(){
        if (isAdd == false){
            isAdd = true;
            add.setText("Exit");
        }else {
            isAdd = false;
            add.setText("Add");
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        pos = WGS2GCJ(pos);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        aMap.moveCamera(cu);
    }

    @Override
    public void onMapClick(LatLng point){
        if (isAdd == true){
            markWaypoint(point);
            point = new GCJ2WGS().gcj2wgsExact(point);
//            markWaypoint(point);
            DJIWaypoint mWaypoint = new DJIWaypoint(point.latitude,point.longitude,altitude);
            //Add waypoints to Waypoint arraylist;
            if (mWaypointMission != null){
                mWaypointMission.addWaypoint(mWaypoint);
                setResultToToast("AddWaypoint");
            }
        }else {
            setResultToToast("cannot add waypoint");
        }
    }

    private void prepareWaypointMission(){
        if (mMissionManager != null && mWaypointMission != null){
            DJIMission.DJIMissionProgressHandler progressHandler = new DJIMission.DJIMissionProgressHandler(){
                @Override
                public void onProgress(DJIMission.DJIProgressType type, float progress){
                }
            };
            mMissionManager.prepareMission(mWaypointMission,progressHandler,new DJIBaseComponent.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error){
                    setResultToToast(error == null ? "Success" : error.getDescription());
                }
            });
        }
    }

    private  void markWaypoint(LatLng point){
        //create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = aMap.addMarker(markerOptions);
        aMarkers.put(aMarkers.size(),marker);
    }

    private void startWaypointMission(){
        if (mMissionManager != null){
            mMissionManager.startMissionExecution(new DJIBaseComponent.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error){
                    setResultToToast("Start: " + (error == null ? "Success" : error.getDescription()));
                }
            });
        }
    }

    private void stopWaypointMission(){
        if (mMissionManager != null){
            mMissionManager.stopMissionExecution(new DJIBaseComponent.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error){
                    setResultToToast("Stop: " + (error == null ? "Success" : error.getDescription()));
                }
            });
        }if (mWaypointMission != null){
            mWaypointMission.removeAllWaypoints();
        }
    }

    private static LatLng WGS2GCJ(LatLng point){
        CoordinateConverter converter = new CoordinateConverter();
        converter.from(CoordinateConverter.CoordType.GPS);
//        converter.from(CoordinateConverter.CoordType.GOOGLE)
        converter.coord(point);
        return converter.convert();
    }

//    private static LatLng GCJ2WGS(LatLng point){
//        double PI = 3.14159265358979324;
//        double initDelta = 0.01;
//        double threhold = 0.000000001;
//        double dLat = initDelta;
//        double dLng = initDelta;
//        double mLat = point.latitude - dLat;
//        double mLng = point.longitude - dLng;
//        double pLat = point.latitude + dLat;
//        double pLng = point.longitude + dLng;
//        double wgsLat = 0;
//        double wgsLng = 0;
//        int i =0;
//        while (true){
//            wgsLat = (mLat + pLat) / 2;
//            wgsLng = (mLng + pLng) / 2;
//
//        }
//    }
}

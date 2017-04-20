package com.demomtp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String ACTION_CUSTOM = "com.yhr.action.custom";

    Context mContext;
    //UsbManager mUsbManager;
    UsbDevice mUsbDevice;
    MtpDevice mMtpDevice;
    AlertDialog mAlert;
    //boolean mIsOpenMtp = false;

    BroadcastReceiver mtpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent data) {
            switch ( data.getAction() ){
                case ACTION_BOOT_COMPLETED:
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    UsbDevice usbDevice = data.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    handleMtpDevice(usbDevice);
                    //attachedUsb(data);
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    if(mMtpDevice!=null)mMtpDevice.close();
                    break;
                case ACTION_USB_PERMISSION:
                    if ( data.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ) {
                        usbDevice = data.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        handleMtpDevice(usbDevice);
                    }
                    break;
                case ACTION_CUSTOM:
                    log("接收到广播..."+ACTION_CUSTOM);
                    break;
            }
        }
    };

    void registerReceiverMtp(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(ACTION_USB_STATE);
        intentFilter.addAction(ACTION_CUSTOM);
        registerReceiver(mtpReceiver, intentFilter);
    }

    void handleMtpDevice(UsbDevice usbDevice ){
        UsbManager manager = (UsbManager)getSystemService(Context.USB_SERVICE);
        boolean isOpenMtp;
        if( manager.hasPermission(usbDevice) ){
            UsbDeviceConnection usbDeviceConnection = manager.openDevice(usbDevice);
            mUsbDevice = usbDevice;
            mMtpDevice = new MtpDevice(usbDevice);
            isOpenMtp = mMtpDevice.open(usbDeviceConnection);
        }else{
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            manager.requestPermission(usbDevice, mPermissionIntent);
            return;
        }

        log("isOpenMtp==="+isOpenMtp);
        if( isOpenMtp ){
            mAlert.hide();
            Observable.interval(5, TimeUnit.SECONDS)
                    .flatMap(new Func1<Long, Observable<Integer>>() {
                        @Override
                        public Observable<Integer> call(Long aLong) {
                            log("start1==="+aLong);
                            int[] ids = mMtpDevice.getStorageIds();
                            Integer[] intIds = convert(ids);
                            return Observable.from(intIds);
                        }
                    })
                    .flatMap(new Func1<Integer, Observable<Integer>>() {
                        @Override
                        public Observable<Integer> call(Integer storageId ) {
                            log("start2==="+storageId);
                            int[] objectHandles = mMtpDevice.getObjectHandles(storageId, MtpConstants.FORMAT_EXIF_JPEG, 0);
                            Integer[] ohs = convert(objectHandles);
                            //log("objectHandles.length="+ohs.length+", time="+new Date().getTime());
                            return Observable.from(ohs);
                        }
                    })
                    .flatMap(new Func1<Integer, Observable<File>>() {
                        @Override
                        public Observable<File> call(Integer objectHandle) {
                            log("start3==="+objectHandle);
                            MtpObjectInfo info = mMtpDevice.getObjectInfo(objectHandle);
                            if( info!=null && info.getProtectionStatus() != MtpConstants.PROTECTION_STATUS_NON_TRANSFERABLE_DATA ){
                                String filename = String.valueOf(new Date().getTime())+".jpg";
                                String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                                File fileJpg = new File(path, "demomtp" + File.separator + filename );
                                fileJpg.getParentFile().mkdirs();
                                log("fileJpg==="+fileJpg.getAbsolutePath()+", exists==="+fileJpg.getParentFile().exists());
                                mMtpDevice.importFile( objectHandle, fileJpg.getAbsolutePath() );
                                mMtpDevice.deleteObject(objectHandle);
                                return Observable.just(fileJpg);
                            }
                            return Observable.empty();
                        }
                    })
                    .subscribe(new Subscriber<File>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onNext(File file) {

                        }
                    });
        }else{
            mAlert.setMessage("与MTP建立连接失败，请重新插入MTP设备");
            mAlert.show();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mMtpDevice!=null)mMtpDevice.close();
        mAlert.dismiss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        registerReceiverMtp();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mAlert = builder.create();

        TextView tvUsbDevice = (TextView)findViewById(R.id.tv_usbdevice);
        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);

        tvUsbDevice.setText("目前有"+usbManager.getDeviceList().size()+"个设备接入");
    }

    public void showMtpInfo( View view ){

        /**
         * 此处若mUsbManager.getDeviceList()始终未0，
         * 参考http://blog.csdn.net/zmy12007/article/details/11021855
         * 参考http://www.jianshu.com/p/eca9a8ad4996
         */
        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        HashMap<String,UsbDevice> map =  usbManager.getDeviceList();
        if( map.size()==0 ){
            Toast.makeText(this, "没有USB设备接入", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbDevice usbDevice = null;
        List<String> list = new ArrayList<>();
        for (UsbDevice device : map.values()) {
            usbDevice = device;
            list.add("getDeviceName="+usbDevice.getDeviceName());
            /*list.add("getProductName="+usbDevice.getProductName());
            list.add("getManufacturerName="+usbDevice.getManufacturerName());
            list.add("getSerialNumber="+usbDevice.getSerialNumber());*/
            list.add("getDeviceId="+usbDevice.getDeviceId());
            list.add("getDeviceProtocol="+usbDevice.getDeviceProtocol());
            list.add("getProductId="+usbDevice.getProductId());
            list.add("getVendorId="+usbDevice.getVendorId());
        }
        log(TextUtils.join(",", list));

        handleMtpDevice(usbDevice);
    }

    static void log( String msg ){
        Log.d("debug_yhr", msg);
    }

    public void sendBroadCast( View view ){
        Intent intent  = new Intent(ACTION_CUSTOM);
        sendBroadcast(intent);
    }

    public void interval( View view ){
        Toast.makeText(this, "此方法还未实现", Toast.LENGTH_SHORT).show();
    }

    Integer[] convert( int[] ids ){
        ids = ids==null? new int[]{}: ids;
        Integer[] intIds = new Integer[ids.length];
        for (int i = 0; i < ids.length; i++) {
            intIds[i] = ids[i];
        }
        return intIds;
    }

}

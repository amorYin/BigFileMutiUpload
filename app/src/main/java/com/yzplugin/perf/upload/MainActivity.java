package com.yzplugin.perf.upload;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.yzplugin.perf.uploadlibrary.UUPConfig;
import com.yzplugin.perf.uploadlibrary.UUPItem;
import com.yzplugin.perf.uploadlibrary.UUPItemType;
import com.yzplugin.perf.uploadlibrary.UUPItf;
import com.yzplugin.perf.uploadlibrary.UUPManager;

import java.io.File;
import java.util.List;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity implements UUPItf {

    TextView textView;
    public static final int REQUEST_IMAGE = 1000;
    protected RxPermissions mRxPermissions;
    private UUPItem mItem;
    private Uri contentUri;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);
        mRxPermissions = new RxPermissions(this);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPhotoAlbum();
            }
        });
    }


    private void openPhotoAlbum() {
        mRxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean granted) throws Exception {
                        if (granted) {
                            Intent takePhotoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

                            if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
                                File newFile = createTakePhotoFile();
                                contentUri = Uri.fromFile(newFile);
//                                contentUri = FileProvider.getUriForFile(getApplicationContext(), getPackageName(), newFile);
//                                Log.i("TAG", "contentUri = " + contentUri.toString());
//                                List<ResolveInfo> resInfoList= getPackageManager().queryIntentActivities(takePhotoIntent, PackageManager.MATCH_DEFAULT_ONLY);
//                                for (ResolveInfo resolveInfo : resInfoList) {
//                                    String packageName = resolveInfo.activityInfo.packageName;
//                                    grantUriPermission(packageName, contentUri,
//                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//                                }
                                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
//                                takePhotoIntent.setType("video/*");
                                startActivityForResult(takePhotoIntent, REQUEST_IMAGE);
                            }
                        }
                    }
                });
    }


    /**
     * @return 拍照之后存储的文件
     */
    private File createTakePhotoFile() {
        File imagePath = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "take_photo");
        if (!imagePath.exists()) {
            imagePath.mkdirs();
        }
        File file = new File(imagePath, "default_image.mp4");
        return file;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case REQUEST_IMAGE:
//                if(contentUri != null){
                    contentUri = data.getData();
                    mItem = new UUPItem(getApplicationContext(),contentUri, UUPItemType.VIDEO);
                    Log.d("UUPItem",mItem.toString());
                    UUPManager.shareInstance(this).strat(mItem,true);
//                }
                break;
            default:
                break;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
//        if(mItem != null)UUPManager.shareInstance(this).strat(mItem,true);
    }

    @Override
    public UUPConfig onConfigure(){
        return new UUPConfig();
    }

    @Override
    public void onUPStart(UUPItem item) {

    }

    @Override
    public void onUPProgress(UUPItem item) {
//        Log.d("UUPItem", "onUPProgress: "+ item);
    }

    @Override
    public void onUPPause(UUPItem item) {

    }

    @Override
    public void onUPFinish(UUPItem item) {
//        Log.d("UUPItem", "onUPFinish: "+ item);
    }

    @Override
    public void onUPFaild(UUPItem item) {

    }

    @Override
    public void onUPError(UUPItem item) {

    }
}
package com.yzplugin.perf.upload;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.yzplugin.perf.uploadlibrary.UUPConfig;
import com.yzplugin.perf.uploadlibrary.UUPItem;
import com.yzplugin.perf.uploadlibrary.UUPItemType;
import com.yzplugin.perf.uploadlibrary.UUPItf;
import com.yzplugin.perf.uploadlibrary.UUPManager;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity implements UUPItf {

    TextView textView;
    public static final int REQUEST_IMAGE = 1000;
    protected RxPermissions mRxPermissions;
    private UUPItem mItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);
        mRxPermissions = new RxPermissions(this);

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPhotoAlbum();
            }
        });
    }


    private void openPhotoAlbum() {
        mRxPermissions.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean granted) throws Exception {
                        if (granted) {
                            Intent intent = new Intent();
                            // 如果要限制上传到服务器的图片类型时可以直接写如："image/jpeg 、 image/png等的类型"
                            intent.setType("video/*");
                            intent.setAction(Intent.ACTION_PICK);
                            startActivityForResult(intent, REQUEST_IMAGE);
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case REQUEST_IMAGE:
                if(data != null){
                    Uri originalUri = data.getData(); // 获得图片的uri
                    mItem = new UUPItem(getApplicationContext(),originalUri, UUPItemType.AUDIO);
                    Log.d("UUPItem",mItem.toString());
                    UUPManager.shareInstance(this).strat(mItem,true);
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
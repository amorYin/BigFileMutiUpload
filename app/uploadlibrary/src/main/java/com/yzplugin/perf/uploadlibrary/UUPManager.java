package com.yzplugin.perf.uploadlibrary;

import android.annotation.SuppressLint;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

public class UUPManager implements UUPItf {
    private WeakReference<UUPItf> mDelegate;
    private UUPConfig mConfig;
    private Vector<UUPItem> mUploading;
    private HashMap<UUPItem,WeakReference<UUPItf>> mRecords;
    private boolean isPause;

    @SuppressLint("StaticFieldLeak")
    private static UUPManager instance = null;

    public static UUPManager shareInstance(UUPItf delegate){
        if (instance == null){
            instance = new UUPManager();
        }
        instance.reset(delegate);
        return  instance;
    }

    public static void destoryManager(){
        if (instance != null){
            instance.destory();
        }
        instance = null;
    }

    private UUPManager(){
        initSet();
    }

    private void initSet(){
        mConfig = new UUPConfig();
        mUploading = new Vector<>();
        mRecords = new HashMap<>();
    }

    //重置
    private void reset(UUPItf delegate){
        if(mDelegate !=null && delegate == mDelegate.get())return;
        if(mRecords.containsValue(mDelegate)){
            for (UUPItem item: mRecords.keySet()) {
                if(mRecords.get(item) == mDelegate || mRecords.get(item) == null){
                    item.cancle();
                    mRecords.remove(item);
                }
            }
        }

        if(mDelegate!=null)mDelegate = null;
        mDelegate = new WeakReference<>(delegate);
        mConfig = delegate.onConfigure();
    }

    public UUPConfig getConfig() {
        return mConfig;
    }

    //销毁单个Activity
    public void destory(UUPItf delegate){
        Log.d("UUPItem", "destory!-cancle-cancle: "+ this);
        if (mRecords != null) {
            synchronized (this) {
                for (UUPItem item: mRecords.keySet()) {
                    WeakReference<UUPItf> _mDelegate = mRecords.get(item);
                    if(_mDelegate == null || _mDelegate.get() == delegate){
                        if(!item.isCancel)item.cancle();
                        mRecords.remove(item);
                    }
                }
            }
        }
    }

    //销毁UUPManager
    public void destory(){
        Log.d("UUPItem", "destory!!!-cancle-cancle: "+ this);
        synchronized (this) {
            if (mRecords != null) {
                for (UUPItem item : mRecords.keySet()) {
                    if (!item.isCancel) item.cancle();
                }
                mRecords.clear();
                mRecords = null;
            }

            if (mUploading != null) {
                for (UUPItem item : mUploading) {
                    if (!item.isCancel) item.cancle();
                }
                mUploading.clear();
                mUploading = null;
            }

            mConfig = null;
            mDelegate = null;
        }
    }

    ///
    public void strat(UUPItem item,boolean immediately){
        synchronized (this) {
            if(immediately){
                if(!mUploading.contains(item)) {
                    mUploading.remove(item);
                    item.isStartting = false;
                }
                mUploading.add(0,item);
            }else{
                if(!mUploading.contains(item))
                    mUploading.add(item);
            }
            mRecords.put(item,mDelegate);
            isPause = false;
            action();
        }
    }

    private void action(){
        List<UUPItem> _mUploading = mUploading;
        if (_mUploading.size()>0){
            int count = 0;
            for (int i = 0;i<_mUploading.size();i++){
                UUPItem item = _mUploading.get(i);
                if(count < mConfig.live && !item.isFinish){
                    if(item.isStartting){
                        count++;
                    }else {
                        item.setDelegate(this);
                        if (item.isValidate){
                            if(!item.isStartting)item.start();
                        }else {
                            Log.d("UUPItem", "isValidate-cancle-cancle: "+ this);
                            //文件不存在
                            if(!item.isCancel)item.cancle();
                            synchronized (this) {
                                mUploading.remove(item);
                                mRecords.remove(item);
                            }
                        }
                    }
                }else if(item.isFinish){
                    synchronized (this) {
                        mUploading.remove(item);
                        mRecords.remove(item);
                    }
                }else {
                    if(!item.isPaused)item.pause();
                }
            }
        }
    }

    public void pause(UUPItem item){
        if(item != null){
            if(!item.isPaused)item.pause();
            action();
        }
    }

    public void cancle(UUPItem item){
        if(item !=null){
            synchronized (this){
                mRecords.remove(item);
                mUploading.remove(item);
            }
            if(!item.isCancel)item.cancle();
            action();
        }
    }
    @Override
    public UUPConfig onConfigure(){
        if (mConfig == null){
            mConfig = new UUPConfig();
        }
        return mConfig;
    }

    @Override
    public void onUPStart(UUPItem item) {
        if (mDelegate != null && mDelegate.get() != null ){
            mDelegate.get().onUPStart(item);
        }
    }

    @Override
    public void onUPProgress(UUPItem item) {
        if (mDelegate != null && mDelegate.get() != null ){
            mDelegate.get().onUPProgress(item);
        }
    }

    @Override
    public void onUPPause(UUPItem item) {
        if (mDelegate != null && mDelegate.get() != null ){
            mDelegate.get().onUPPause(item);
        }
        action();
    }

    @Override
    public void onUPFinish(UUPItem item) {
        synchronized (this){
            mRecords.remove(item);
            mUploading.remove(item);
        }

        if (mDelegate != null && mDelegate.get() != null ){
            mDelegate.get().onUPFinish(item);
        }
        action();
    }

    @Override
    public void onUPError(UUPItem item) {
        if (mDelegate != null && mDelegate.get() != null ){
            mDelegate.get().onUPError(item);
        }
        action();
    }
}
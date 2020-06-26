package com.yzplugin.perf.uploadlibrary;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.UploadService;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.Timer;
import java.util.TimerTask;

public class UUPItem {

    public String mRemoteUri; //上传后返回的地址
    public Uri mContentUri;
    public String mDisplayName;
    public String mFilePath;
    public String mThumbnailsPath;
    public String mMimeType;
    public long mDuration;
    public long mSize;
    public float mProgress = 0.0f; //计算进度
    public long mSpeed = 0;
    public String mSpeedStr;
    public String mSizeStr;
    public UUPItemType mType;
    ////////////////////////////
    protected UUPSlicedItem mCurrentItem;
    protected String mUploadFileName;
    protected Context mContext;
    protected MultipartUploadRequest request;
    protected UUPReceiver mReceiver;
    protected boolean isStartting;
    protected boolean isPaused;
    protected boolean isCancel;
    protected boolean isFinish;
    protected boolean isValidate = true;
    protected float mPProgress = 0.0f; //分片上传真实进度
    protected String mRequestID;
    protected WeakReference<UUPItf> mDelegate;
    protected UUPConfig mConfig;
    protected UUPSliced mSliced;
    private float mLastProgress = 0.0f;
    private Timer mSpeedTimer;

    public UUPItem(Context context, Uri path, UUPItemType type){
        mContext = context;
        mContentUri = path;
        mType = type;
        if(mContentUri != null){initSet();}
        else {isValidate = false;}
    }

    private void initSet(){

        try {
            if("file".equals(mContentUri.getScheme())){

                if(mContentUri.getPath()!=null) {
                    File mFile = new File(mContentUri.getPath());
                    if (mFile.exists()) {
                        if (mType == UUPItemType.VIDEO) {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(mContext, mContentUri);
                            mDuration = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                            mThumbnailsPath = UUPUtil.getThumbnailsPath(mContext, mFile, mmr.getFrameAtTime(0));
                            mMimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                            mmr.release();
                        } else if (mType == UUPItemType.AUDIO) {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(mContext, mContentUri);
                            mDuration = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                            mThumbnailsPath = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                            mMimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                            mmr.release();
                        } else {
                            mThumbnailsPath = mFile.getAbsolutePath();
                            mMimeType = UUPUtil.getMimeType(mFile);
                        }
                        mFilePath = mFile.getAbsolutePath();
                        mDisplayName = mFile.getName();
                        mSize = mFile.length();
                    }
                }
            }else if("content".equals(mContentUri.getScheme())){
                String[] projection = {
                        MediaStore.MediaColumns.DATA
                        ,MediaStore.MediaColumns.SIZE
                        ,MediaStore.MediaColumns.DISPLAY_NAME
                        ,MediaStore.MediaColumns.MIME_TYPE };

                Cursor cursor = null;
//                String selection = MediaStore.MediaColumns.DATA+" like ?";
//                String[] selectionArgs = {displayName()};
                ContentResolver contentResolver = mContext.getContentResolver();

                cursor = contentResolver.query(mContentUri, projection, null, null, null);

                if(cursor != null && cursor.moveToFirst()){
                    int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
                    mFilePath = cursor.getString(columnIndex);
                    Log.d("UUPItem: ",mFilePath);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[1]);
                    mSize = cursor.getInt(columnIndex);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[2]);
                    mDisplayName = ""+cursor.getString(columnIndex);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[3]);
                    mMimeType = cursor.getString(columnIndex);
                }
                if(cursor!=null)cursor.close();
                if (mFilePath != null && mType != UUPItemType.IMAGE){
                    File mFile = new File(mFilePath);
                    if (mType == UUPItemType.VIDEO){
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(mContext,mContentUri);
                        mDuration = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        mThumbnailsPath = UUPUtil.getThumbnailsPath(mContext,mFile,mmr.getFrameAtTime(0));
                        mmr.release();
                    }else if(mType == UUPItemType.AUDIO){
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(mContext,mContentUri);
                        mDuration = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        mThumbnailsPath = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        mmr.release();
                    }
                }
            }else {
                isValidate = false;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        if(mFilePath == null || mSize < 1) {isValidate = false;}
        mUploadFileName = UUPUtil.randomName() + mDisplayName;
        mSizeStr = UUPUtil.calculateSize(mSize);
        Log.d("UUPItem: ","mDisplayName:"+mDisplayName+" mMimeType:"+mMimeType+" mDuration:"+mDuration+" mSize:"+mSize+" mFilePath:"+mFilePath+" mThumbnailsPath:"+mThumbnailsPath);
    }

    private String displayName(){
        String file = mContentUri.getPath();
        if (file == null) return "*";
        String[] paths = file.split("/");
        return "%"+paths[paths.length - 1];
    }

    protected void setDelegate(UUPItf delegate){
        mDelegate = new WeakReference<>(delegate);
    }

    protected void check(){
        if(mDelegate == null)
            return;

        if (mConfig == null){
            mConfig = mDelegate.get().onConfigure();
        }

        if (mSliced == null){
            mSliced = new UUPSliced(new WeakReference<>(this));
            if(isValidate)mSliced.makeChunks();
        }

        if(mReceiver == null){
            mReceiver = new UUPReceiver(this);
            mReceiver.register(mContext);
        }
        if(mSize > mConfig.size){isValidate = false;}
    }

    protected void start() {
        this.isStartting = true;
        if (!this.isPaused) {
            this.check();
        }

        if (this.isValidate) {
            this.next();
            this.calculateSpeed();
        }
    }

    protected void next(){
        //暂停的开始
        if(isPaused && request != null){
            try {
                mRequestID = request.startUpload();
                isPaused = false;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                if(mDelegate.get() != null){
                    mDelegate.get().onUPError(this);
                }
            }
        }
        //并行一个
        if(mCurrentItem!=null && !mCurrentItem.isFinish) return;
        mCurrentItem = mSliced.nextSliced();
        if (mCurrentItem == null) return;
        if (request != null) {
            if(mRequestID != null)
                UploadService.stopUpload(mRequestID);
            request = null;
        }
        Log.d("UUPItem", "next: "+ mCurrentItem.mChunkIndex+" "+mCurrentItem.isSuspend);
        try {
            request = new MultipartUploadRequest(mContext,UUPUtil.randomName() + mDisplayName,mConfig.serverUri);
            request.addFileToUpload(mCurrentItem.mChunkFile.getAbsolutePath(), "file");
        } catch (Exception e) {
            e.printStackTrace();
            if(mDelegate.get() != null)
                mDelegate.get().onUPError(this);
            return;
        }

        request.addHeader("Auth-Sign", mConfig.authSign);
        request.addParameter("card", mConfig.card);
        request.addParameter("chunk", String.valueOf(mCurrentItem.mChunkIndex));
        request.addParameter("chunks", String.valueOf(mSliced.mTotalChunks));
        request.addParameter("name", mDisplayName);
        request.setAutoDeleteFilesAfterSuccessfulUpload(false);
        request.setMaxRetries(mConfig.retryTimes);


        try {
            mRequestID = request.startUpload();
            isPaused = false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            if(mDelegate.get() != null){
                mDelegate.get().onUPError(this);
            }
        }
        Log.d("UUPItem", "start: "+ this);
    }

    protected void pause(){
        Log.d("UUPItem", "pause-cancle: "+ this);
        if (request != null && !isFinish && !isPaused && mRequestID != null){
            UploadService.stopUpload(mRequestID);
            isPaused = true;
            isStartting = false;
            mRequestID = null;
        }
    }

    protected void cancle(){
        Log.d("UUPItem", "cancle-cancle: "+ this);
        isCancel = true;
        if( mRequestID != null){
            UploadService.stopUpload(mRequestID);
        }
        if (request != null){
            request = null;
        }
        mRequestID = null;
        isPaused = false;
        mCurrentItem = null;
        if(mSliced!=null)mSliced.destroy();
        mSliced = null;
        UUPUtil.deleteThumbnail(mThumbnailsPath);
        if(mReceiver!=null)mReceiver.unregister(mContext);
        mReceiver = null;
        if(mSpeedTimer!=null){
            mSpeedTimer.cancel();
        }
        mSpeedTimer = null;
    }

    private void calculateSpeed(){
        if (mSpeedTimer == null){
            mSpeedTimer = new Timer();
            WeakReference<UUPItem> weakReference = new WeakReference<>(this);
            final UUPItem weakSelf = weakReference.get();
            mSpeedTimer.schedule(new TimerTask() {
                @SuppressLint("DefaultLocale")
                @Override
                public void run() {
                    double tmp = (mProgress - mLastProgress) * mSize;
                    mSpeed = (long) tmp;
                    mSpeedStr = UUPUtil.calculateSpeed(tmp);
                    mLastProgress = mProgress;
                    Log.d("UUPItem", "calculateSpeed: "+ mSpeedStr +" "+mSpeed +" "+tmp);
                    if(mDelegate.get() != null)
                        mDelegate.get().onUPProgress(weakSelf);
                    if(!UUPUtil.isNetworkConnected(mContext)){
                        pause();
                    }else {
                        start();
                    }
                }
            },0,1000);
        }
    }

    //仅供recevier使用
    protected void pCalculateSpeed(){
        double tmp = (mProgress - mLastProgress) * mSize;
        mSpeed = (long) tmp;
        mSpeedStr = UUPUtil.calculateSpeed(tmp);
        mLastProgress = mProgress;
        Log.d("UUPItem", "calculateSpeed: "+ mSpeedStr +" "+mSpeed +" "+tmp);
        if(mDelegate.get() != null)
            mDelegate.get().onUPProgress(this);
    }

    @Override
    public String toString() {
        return "UUPItem{" +
                "mRemoteUri='" + mRemoteUri + '\'' +
                ", mContentUri=" + mContentUri +
                ", mDisplayName='" + mDisplayName + '\'' +
                ", mFilePath='" + mFilePath + '\'' +
                ", mThumbnailsPath='" + mThumbnailsPath + '\'' +
                ", mMimeType='" + mMimeType + '\'' +
                ", mDuration=" + mDuration +
                ", mSize=" + mSize +
                ", mProgress=" + mProgress +
                ", mSpeed=" + mSpeed +
                ", mSpeedStr='" + mSpeedStr + '\'' +
                ", mType=" + mType +
                ", mContext=" + mContext +
                '}';
    }
}
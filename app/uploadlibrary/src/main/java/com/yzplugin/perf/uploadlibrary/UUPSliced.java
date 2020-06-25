package com.yzplugin.perf.uploadlibrary;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

class UUPSliced {
    private List<UUPSlicedItem> mChunkList;
    private UUPConfig mConfig;
    private String mPath;
    private File mTempRoot;
    protected int mTotalChunks;
    protected UUPItem mItem;

    UUPSliced(WeakReference<UUPItem> item){
        mItem = item.get();
        if(mItem != null){
            mPath = mItem.mFilePath;
            String path = mItem.mUploadFileName.replace(".","~");
            mTempRoot = mItem.mContext.getExternalFilesDir("Sliced/"+path);
            UUPUtil.isFilesExist(mTempRoot);
            if(mItem.mConfig != null){
                mConfig = mItem.mConfig;
            }else {
                mConfig = new UUPConfig();
            }
            mChunkList = new ArrayList<>();
        }
    }

    void makeChunks(){
        if(mItem == null) return;
        if(mPath == null) return;
        File file =  new File(mPath);
        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            // TODO: 16/4/15 buffer is too large
            byte[] buffer = new byte[mConfig.perChunks];
            int index = 0;
            int count;
            while ((count = bufferedInputStream.read(buffer)) != -1) {
                String filePath = mTempRoot.getAbsolutePath()+"/"+index+".tmp";
                File chunkFile = new File(filePath);
                Log.d("分片地址",filePath);
                //noinspection ResultOfMethodCallIgnored
                chunkFile.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(chunkFile);
                bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                bufferedOutputStream.write(buffer, 0, count);
                bufferedOutputStream.flush();
                bufferedOutputStream.close();
                fileOutputStream.close();
                UUPSlicedItem item = new UUPSlicedItem();
                item.mChunkIndex = index;
                item.mChunkFile = chunkFile;
                item.mChunkSize = count;
                item.mProgress = count * 1.0f/mItem.mSize;
                mChunkList.add(item);
                index++;
            }
            bufferedInputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedInputStream != null) {
                try {
                    bufferedInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bufferedOutputStream != null) {
                try {
                    bufferedOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        mTotalChunks = mChunkList.size();
    }

    UUPSlicedItem nextSliced(){
        UUPSlicedItem returnItem = null;
        for (UUPSlicedItem item: mChunkList) {
            if (!item.isSuspend) {
                item.isSuspend = true;
                returnItem = item;
                break;
            }
        }
        return returnItem;
    }

    protected int remainChunk(){
        if(mChunkList == null)return 0;
        return mChunkList.size();
    }

    void clean(UUPSlicedItem item){
        if(mItem == null || item == null) return;
        if(item.mChunkFile != null){
            //noinspection ResultOfMethodCallIgnored
            item.mChunkFile.delete();
        }
        mChunkList.remove(item);
        if(mChunkList.size() <1){
            destroy();
        }
    }

    // 清理缓存
    void destroy(){
        for (UUPSlicedItem item: mChunkList) {
            if(item.mChunkFile != null){
                //noinspection ResultOfMethodCallIgnored
                item.mChunkFile.delete();
            }
        }
        mChunkList.clear();
        //noinspection ResultOfMethodCallIgnored
        mTempRoot.delete();
    }
}

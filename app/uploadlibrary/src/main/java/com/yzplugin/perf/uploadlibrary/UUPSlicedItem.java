package com.yzplugin.perf.uploadlibrary;

import java.io.File;

class UUPSlicedItem {
    protected int mChunkIndex;
    protected File mChunkFile;
    protected long mChunkSize;
    protected float mProgress;
    protected float mPProgress;
    protected boolean isFinish;
    protected boolean isSuspend;
}

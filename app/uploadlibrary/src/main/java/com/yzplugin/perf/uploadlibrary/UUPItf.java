package com.yzplugin.perf.uploadlibrary;

public interface UUPItf {
    void onUPStart(UUPItem item);
    void onUPProgress(UUPItem item);
    void onUPPause(UUPItem item);
    void onUPFinish(UUPItem item);
    void onUPError(UUPItem item);
    UUPConfig onConfigure();
}

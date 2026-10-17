package com.alphacorp.instaloader.data.python;

@FunctionalInterface
public interface ProgressCallback {
    void onProgress(
            String phase,
            String total,
            String completed,
            String failed,
            String skipped,
            String remaining,
            String current,
            String message,
            String transferFile,
            String transferCompletedBytes,
            String transferTotalBytes,
            String downloadedFilePath
    );
}

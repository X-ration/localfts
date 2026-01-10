package com.adam.localfts.webserver.common.compress;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderCompressingContextHolder {
    private Long compressSize;
    private Thread executeThread;
    private long startTime;
    private long finishTime;

    public FolderCompressingContextHolder(Long compressSize) {
        this.compressSize = compressSize;
        this.executeThread = Thread.currentThread();
        this.startTime = System.currentTimeMillis();
        this.finishTime = -1L;
    }

    /**
     * 通过ShutdownListener对线程池调用shutdownNow触发中断
     * 或通过手动调用此方法
     * @return 是否实际触发中断
     */
    public boolean interruptThread() {
        Thread thread = executeThread;
        if(thread != null && thread.isAlive()) {
            thread.interrupt();
            return true;
        } else {
            return false;
        }
    }

    public void updateFinishTimeNow() {
        this.finishTime = System.currentTimeMillis();
    }
}

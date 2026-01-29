package com.adam.localfts.webserver.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneIndexThread extends Thread{

    private final Logger logger = LoggerFactory.getLogger(LuceneIndexThread.class);
    private final ThreadLocal<Boolean> stopThreadLocal = new ThreadLocal<>();
    private static volatile LuceneIndexThread INSTANCE = null;

    private LuceneIndexThread() {
        super("LI-Thread");
        this.stopThreadLocal.set(false);
    }

    public static LuceneIndexThread getInstance() {
        if(INSTANCE == null) {
            synchronized (LuceneIndexThread.class) {
                if(INSTANCE == null) {
                    INSTANCE = new LuceneIndexThread();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void run() {
        logger.info("Ready to index");
        /*if(Thread.interrupted() && this.stopThreadLocal.get()) {
        }*/
    }

    public void tryStop() {
        this.stopThreadLocal.set(true);
        INSTANCE.interrupt();
    }

}

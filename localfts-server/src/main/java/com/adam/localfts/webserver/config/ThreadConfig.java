package com.adam.localfts.webserver.config;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadConfig {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;

    private final Logger logger = LoggerFactory.getLogger(ThreadConfig.class);

    @Bean("searchThreadPool")
    @ConditionalOnProperty(prefix = "localfts.search", name = "enabled", havingValue = "true", matchIfMissing = false)
    public ThreadPoolExecutor searchThreadPool() {
        logger.debug("Creating search thread pool");
        int physicalAvailableProcessors = Constants.PHYSICAL_AVAILABLE_PROCESSORS;
        int coreThreads;
        if(physicalAvailableProcessors == 1) {
            logger.warn("[Performance warning]Search thread pool takes 1 only available cpu core! Requests may wait long.");
            coreThreads = 1;
        } else {
            coreThreads = (int)(1.0 * physicalAvailableProcessors / 2);
        }
        int maxThreads = coreThreads;
        if(ftsServerConfigService.getLocalFtsProperties().getSearch().getActiveTaskThreshold() != null) {
            maxThreads = ftsServerConfigService.getLocalFtsProperties().getSearch().getActiveTaskThreshold();
        }
        if(maxThreads >= physicalAvailableProcessors) {
            logger.warn("[Performance warning]Search thread pool max thread num reaches physical available processors! Requests may wait long.");
        }
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                coreThreads, maxThreads, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("search-task-thread-" + (++count));
                        thread.setDaemon(false);
                        return thread;
                    }
                }
        );
        threadPoolExecutor.prestartAllCoreThreads();
        return threadPoolExecutor;
    }

}

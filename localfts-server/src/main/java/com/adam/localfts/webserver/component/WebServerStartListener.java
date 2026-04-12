package com.adam.localfts.webserver.component;

import com.adam.localfts.webserver.service.FtsServerConfigService;
import com.adam.localfts.webserver.task.BrowseLocalftsRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Component
public class WebServerStartListener {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private boolean webServerStarted = false;
    private WebServer webServer;
    private final List<Thread> asyncTasks = Collections.synchronizedList(new LinkedList<>());
    private final List<Runnable> syncTasks = Collections.synchronizedList(new LinkedList<>());
    private final Logger logger = LoggerFactory.getLogger(WebServerStartListener.class);

    @EventListener
    public void setWebServer(WebServerInitializedEvent event) {
        if(event.getWebServer() != null) {
            this.webServerStarted = true;
            this.webServer = event.getWebServer();
            logger.debug("WebServer {} started", event.getWebServer());
            Boolean autoOpenBrowser = ftsServerConfigService.getLocalFtsProperties().getAutoOpenBrowser();
            if(autoOpenBrowser != null && autoOpenBrowser) {
                addSyncTask(new BrowseLocalftsRunnable(webServer.getPort(), contextPath));
            }

            List<Thread> asyncTasks = Collections.unmodifiableList(this.asyncTasks);
            List<Runnable> syncTasks = Collections.unmodifiableList(this.syncTasks);
            if(!asyncTasks.isEmpty()) {
                logger.info("Executing async tasks (count:{})", asyncTasks.size());
                for(Thread thread: asyncTasks) {
                    if(thread != null && !thread.isAlive()) {
                        thread.start();
                    }
                }
            }
            if(!syncTasks.isEmpty()) {
                logger.info("Executing sync tasks (count:{})", syncTasks.size());
                for(int i=0;i<syncTasks.size();i++) {
                    Runnable runnable = syncTasks.get(i);
                    if(runnable != null) {
                        logger.info("Executing sync task [{}]", i+1);
                        runnable.run();
                    }
                }
            }
        }
    }

    public void addAsyncTask(Thread thread) {
        asyncTasks.add(thread);
    }

    public void addAsyncTask(Runnable runnable) {
        asyncTasks.add(new Thread(runnable, "WSSL-Thread-" + (asyncTasks.size() + 1)));
    }

    public void addSyncTask(Runnable runnable) {
        syncTasks.add(runnable);
    }

    public boolean isWebServerStarted() {
        return webServerStarted;
    }

    public WebServer getWebServer() {
        return webServer;
    }
}

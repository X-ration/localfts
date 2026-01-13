package com.adam.localfts.webserver.component;

import com.adam.localfts.webserver.service.FtsService;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Component
public class ShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private FtsService ftsService;
    @Autowired
    private WebServerStartListener webServerStartListener;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if(webServerStartListener.isWebServerStarted()) {
            logger.debug("Preparing shutdown");
            TomcatWebServer tomcatWebServer = (TomcatWebServer) webServerStartListener.getWebServer();
            for (Connector connector : tomcatWebServer.getTomcat().getService().findConnectors()) {
                Executor executor = connector.getProtocolHandler().getExecutor();
                if (executor instanceof ExecutorService) {
                    ExecutorService executorService = (ExecutorService) executor;
                    logger.debug("Preparing force shutdown connector={},executorService={}", connector, executorService);
                    executorService.shutdownNow();
                } else {
                    logger.warn("failed to shutdown executor,connector={},executor={}", connector, executor);
                }
            }
//
//        try {
//            ftsService.shutdown();
//        } catch (Exception e) {
//            logger.warn("failed to shutdown FtsService:{} {}", e.getClass(), e.getMessage());
//        }
        }
    }

}

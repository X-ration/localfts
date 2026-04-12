package com.adam.localfts.webserver.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class BrowseLocalftsRunnable implements Runnable{

    private final Integer serverPort;
    private final String contextPath;
    private final Logger logger = LoggerFactory.getLogger(BrowseLocalftsRunnable.class);

    public BrowseLocalftsRunnable(Integer serverPort, String contextPath) {
        this.serverPort = serverPort;
        this.contextPath = contextPath;
    }

    @Override
    public void run() {
        if(Desktop.isDesktopSupported()) {
            StringBuilder stringBuilder = new StringBuilder("http://localhost");
            if (serverPort != null && serverPort != 80) {
                stringBuilder.append(":").append(serverPort);
            }
            if (!StringUtils.isEmpty(contextPath)) {
                stringBuilder.append("/").append(contextPath);
            }
            String url = stringBuilder.toString();
            try {
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(new URI(url));
            } catch (UnsupportedOperationException e) {
                logger.warn("获取Desktop对象出现异常，ex.type={},ex.msg={}", e.getClass().getName(), e.getMessage());
            } catch (IOException | URISyntaxException e) {
                logger.warn("打开链接时出现异常，ex.type={},ex.msg={}", e.getClass().getName(), e.getMessage());
            }
        } else {
            logger.warn("不支持打开链接");
        }
    }
}

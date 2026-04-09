package com.adam.localfts.webserver;

import com.adam.localfts.webserver.config.properties.LocalFtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.net.SocketException;

@SpringBootApplication
@EnableConfigurationProperties(LocalFtsProperties.class)
public class LocalFtsWebServer {

	private static final Logger logger = LoggerFactory.getLogger(LocalFtsWebServer.class);

	public static void main(String[] args) throws SocketException {
		SpringApplication.run(LocalFtsWebServer.class, args);
	}


}

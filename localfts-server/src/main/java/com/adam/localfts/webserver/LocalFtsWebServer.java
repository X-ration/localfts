package com.adam.localfts.webserver;

import com.adam.localfts.webserver.config.properties.LocalFtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties(LocalFtsProperties.class)
public class LocalFtsWebServer {

	private static final Logger logger = LoggerFactory.getLogger(LocalFtsWebServer.class);

	public static void main(String[] args) {
		SpringApplication springApplication = new SpringApplication(LocalFtsWebServer.class);
		setDefaultProperties(springApplication);
		springApplication.run(args);
	}

	private static void setDefaultProperties(SpringApplication springApplication) {
		Map<String, Object> defaultProperties = new HashMap<>();
		defaultProperties.put("server.error.include-exception",true);
		defaultProperties.put("server.error.include-stack-trace", ErrorProperties.IncludeStacktrace.ALWAYS);
		defaultProperties.put("server.error.whitelabel.enabled", false);
		defaultProperties.put("spring.output.ansi.enabled", AnsiOutput.Enabled.ALWAYS);
		defaultProperties.put("spring.thymeleaf.cache", false);
		defaultProperties.put("spring.mvc.static-path-pattern", "/static/**");
		defaultProperties.put("spring.resources.static-locations", "classpath:/resources");
		springApplication.setDefaultProperties(defaultProperties);
	}

}

package com.adam.localfts.webserver.config;

import com.adam.localfts.webserver.component.CheckCriticalConfigFilter;
import com.adam.localfts.webserver.component.NoCacheInterceptor;
import com.adam.localfts.webserver.component.ShutdownInterceptor;
import com.adam.localfts.webserver.component.ZipRequestPathFilter;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private NoCacheInterceptor noCacheInterceptor;
    @Autowired
    private ShutdownInterceptor shutdownInterceptor;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Autowired
    private FtsServerConfigService ftsServerConfigService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(noCacheInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/search");
        registry.addInterceptor(shutdownInterceptor).addPathPatterns("/**");
    }

    @Bean
    public FilterRegistrationBean<CheckCriticalConfigFilter> checkCriticalConfigFilterBean() {
        FilterRegistrationBean<CheckCriticalConfigFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        CheckCriticalConfigFilter filter = new CheckCriticalConfigFilter(ftsServerConfigService);
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(2); //数字越小执行越早
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<ZipRequestPathFilter> zipRequestPathFilterBean() {
        FilterRegistrationBean<ZipRequestPathFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        ZipRequestPathFilter filter = new ZipRequestPathFilter(ftsServerConfigService);
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/compressManagement", "/compressFolder", "/cancelCompress", "/deleteCompressFile");
        filterRegistrationBean.setOrder(1);
        return filterRegistrationBean;
    }

}

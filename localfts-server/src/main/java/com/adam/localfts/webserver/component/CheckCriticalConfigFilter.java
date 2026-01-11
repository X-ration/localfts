package com.adam.localfts.webserver.component;

import com.adam.localfts.webserver.service.FtsServerConfigService;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 检查关键配置，如果不通过则抛出异常
 */
public class CheckCriticalConfigFilter extends OncePerRequestFilter {

    private FtsServerConfigService ftsServerConfigService;
    public CheckCriticalConfigFilter(FtsServerConfigService ftsServerConfigService) {
        this.ftsServerConfigService = ftsServerConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        ftsServerConfigService.checkCriticalConfig();
        filterChain.doFilter(request, response);
    }
}

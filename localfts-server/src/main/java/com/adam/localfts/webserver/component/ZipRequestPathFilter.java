package com.adam.localfts.webserver.component;

import com.adam.localfts.webserver.service.FtsServerConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 当压缩功能开关关闭时，让对压缩相关路径的请求返回404
 */
@WebFilter(urlPatterns = {"/compress*", "/*Compress*"})
public class ZipRequestPathFilter extends OncePerRequestFilter {

    private FtsServerConfigService ftsServerConfigService;
    public ZipRequestPathFilter(FtsServerConfigService ftsServerConfigService) {
        this.ftsServerConfigService = ftsServerConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
//            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            response.sendError(HttpStatus.NOT_FOUND.value());
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

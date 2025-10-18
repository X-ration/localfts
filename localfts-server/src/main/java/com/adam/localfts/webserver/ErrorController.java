package com.adam.localfts.webserver;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import ua_parser.Client;
import ua_parser.Parser;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    private ErrorAttributes errorAttributes;
    private final ServerProperties serverProperties;
    private final List<ErrorViewResolver> errorViewResolvers;
    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;

    public ErrorController(ErrorAttributes errorAttributes, ServerProperties serverProperties,
                                     ObjectProvider<ErrorViewResolver> errorViewResolvers) {
        this.errorAttributes = errorAttributes;
        this.serverProperties = serverProperties;
        this.errorViewResolvers = errorViewResolvers.orderedStream()
                .collect(Collectors.toList());
    }

    @RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView errorHtml(HttpServletRequest request,
                                  HttpServletResponse response) {
        String userAgent = IOUtil.getHeaderIgnoreCase(request, "User-Agent");
        Parser uaParser = new Parser();
        Client uaClient = uaParser.parse(userAgent);
        HttpStatus status, realStatus;
        //IE 6会无法处理错误状态的http状态码（只有对话框提示不展示错误页面）
        if(uaClient.userAgent.family.equals("IE") && Integer.parseInt(uaClient.userAgent.major) <= 6) {
            status = HttpStatus.OK;
            realStatus = getStatus(request);
        } else {
            status = getStatus(request);
            realStatus = status;
        }
        response.setStatus(status.value());
//        Map<String, Object> model = Collections.unmodifiableMap(getErrorAttributes(
//                request, isIncludeStackTrace(request, MediaType.TEXT_HTML)));
        Map<String, Object> modifiableModel = getErrorAttributes(request,
                isIncludeStackTrace(request, MediaType.TEXT_HTML));
        modifiableModel.put("status", realStatus.value());
        modifiableModel.put("error", realStatus.getReasonPhrase());
        modifiableModel.put("timestamp", Util.getServerTimeFormattedString(Locale.US));
        Map<String, Object> model = Collections.unmodifiableMap(modifiableModel);
        ModelAndView modelAndView = resolveErrorView(request, response, status, model);
        return (modelAndView != null) ? modelAndView : new ModelAndView("error", model);
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request
                .getAttribute("javax.servlet.error.status_code");
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(statusCode);
        }
        catch (Exception ex) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    protected ModelAndView resolveErrorView(HttpServletRequest request,
                                            HttpServletResponse response, HttpStatus status, Map<String, Object> model) {
        for (ErrorViewResolver resolver : this.errorViewResolvers) {
            ModelAndView modelAndView = resolver.resolveErrorView(request, status, model);
            if (modelAndView != null) {
                return modelAndView;
            }
        }
        return null;
    }

    private Map<String, Object> getErrorAttributes(HttpServletRequest request,
                                                     boolean includeStackTrace) {
        WebRequest webRequest = new ServletWebRequest(request);
        return this.errorAttributes.getErrorAttributes(webRequest, includeStackTrace);
    }

    private boolean isIncludeStackTrace(HttpServletRequest request,
                                          MediaType produces) {
        ErrorProperties.IncludeStacktrace include = serverProperties.getError().getIncludeStacktrace();
        if (include == ErrorProperties.IncludeStacktrace.ALWAYS) {
            return true;
        }
        if (include == ErrorProperties.IncludeStacktrace.ON_TRACE_PARAM) {
            return getTraceParameter(request);
        }
        return false;
    }

    private boolean getTraceParameter(HttpServletRequest request) {
        String parameter = request.getParameter("trace");
        if (parameter == null) {
            return false;
        }
        return !"false".equalsIgnoreCase(parameter);
    }

    @Override
    public String getErrorPath() {
        return errorPath;
    }
}

package com.adam.localfts.webserver.controller;

import com.adam.localfts.webserver.common.ReturnObject;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import ua_parser.Client;
import ua_parser.Parser;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    private final Logger logger = LoggerFactory.getLogger(ErrorController.class);

    public ErrorController(ErrorAttributes errorAttributes, ServerProperties serverProperties,
                                     ObjectProvider<ErrorViewResolver> errorViewResolvers) {
        this.errorAttributes = errorAttributes;
        this.serverProperties = serverProperties;
        this.errorViewResolvers = errorViewResolvers.orderedStream()
                .collect(Collectors.toList());
    }

    @RequestMapping
    public Object error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userAgent = IOUtil.getHeaderIgnoreCase(request, "User-Agent");
        String accept = IOUtil.getHeaderIgnoreCase(request, "Accept");
        Parser uaParser = new Parser();
        Client uaClient = uaParser.parse(userAgent);
//        Map<String, Object> model = Collections.unmodifiableMap(getErrorAttributes(
//                request, isIncludeStackTrace(request, MediaType.TEXT_HTML)));
        Map<String, Object> modifiableModel = getErrorAttributes(request, isIncludeStackTrace(request, MediaType.TEXT_HTML));
        postHandleException(request, modifiableModel);
        HttpStatus realStatus = dealWithResponseStatusException(modifiableModel);
        if(realStatus == null) {
            realStatus = getStatus(request);
        }
        HttpStatus status = realStatus;
        //IE 6会无法处理错误状态的http状态码（只有对话框提示不展示错误页面），因此强制状态码改为200
        if(uaClient.userAgent.family.equals("IE") && Integer.parseInt(uaClient.userAgent.major) <= 6) {
            status = HttpStatus.OK;
        }
        response.setStatus(status.value());
        modifiableModel.put("status", realStatus.value());
        modifiableModel.put("error", realStatus.getReasonPhrase());
        modifiableModel.put("timestamp", Util.getServerTimeFormattedString(Locale.US));
        Map<String, Object> model = Collections.unmodifiableMap(modifiableModel);
        if(accept == null || accept.contains(MediaType.ALL_VALUE) || accept.contains(MediaType.TEXT_HTML_VALUE)) {
            ModelAndView modelAndView = resolveErrorView(request, response, status, model);
            return (modelAndView != null) ? modelAndView : new ModelAndView("error", model);
        } else {
            model = model.entrySet().stream()
                    .filter(entry -> !entry.getKey().equalsIgnoreCase("trace"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            ObjectMapper objectMapper = new ObjectMapper();
            ReturnObject<Map<String, Object>> returnObject = ReturnObject.fail(realStatus.getReasonPhrase(), model);
            String json = objectMapper.writeValueAsString(returnObject);
            response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getWriter().write(json);
            return null;
        }
    }

    private HttpStatus dealWithResponseStatusException(Map<String, Object> modifiableModel) {
        if("org.springframework.web.server.ResponseStatusException".equals(modifiableModel.get("exception"))) {
            HttpStatus httpStatus = HttpStatus.valueOf(((String) modifiableModel.get("message")).split(" ")[1]);
            modifiableModel.remove("exception");
            modifiableModel.put("message", "No message available");
            modifiableModel.remove("trace");
            return httpStatus;
        }
        return null;
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

    private void postHandleException(HttpServletRequest request, Map<String, Object> modifiableModel) {
        WebRequest webRequest = new ServletWebRequest(request);
        Throwable throwable = this.errorAttributes.getError(webRequest);
        if(throwable instanceof BindException) {
            BindException bindException = (BindException) throwable;
            StringBuilder stringBuilder = new StringBuilder();
            List<FieldError> fieldErrorList = bindException.getBindingResult().getFieldErrors();
            if(!CollectionUtils.isEmpty(fieldErrorList)) {
                for(int i=0;i<fieldErrorList.size();i++) {
                    stringBuilder.append(",[").append(i+1).append("]");
                    FieldError fieldError = fieldErrorList.get(i);
                    if(fieldError != null) {
                        Throwable source = null;
                        try {
                            source = fieldError.unwrap(Throwable.class);
                        } catch (IllegalArgumentException e) {
                            logger.warn("failed to unwrap FieldError '{}' to Throwable", fieldError);
                            stringBuilder.append("Field=").append(fieldError.getField()).append(",rejected value=").append(fieldError.getRejectedValue());
                            continue;
                        }
                        Throwable cause = source.getCause();
                        stringBuilder.append(cause.getClass().getName()).append(":").append(cause.getMessage());
                    } else {
                        stringBuilder.append("FieldError not found");
                    }
                }
                modifiableModel.put("message", stringBuilder.substring(1));
                modifiableModel.remove("trace");
                modifiableModel.remove("errors");
            }
        } else if(throwable instanceof MethodArgumentTypeMismatchException) {
            modifiableModel.remove("trace");
        }
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

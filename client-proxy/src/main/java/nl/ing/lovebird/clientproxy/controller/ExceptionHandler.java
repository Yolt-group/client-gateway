package nl.ing.lovebird.clientproxy.controller;

import com.netflix.zuul.exception.ZuulException;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.exception.*;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.errorhandling.ErrorInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ExceptionHandler {

    private final Map<Class, ExceptionMapping> exceptionMappings = new HashMap<>();

    public static final String ERROR_CODE_PREFIX = "CP";

    public ExceptionHandler() {
        init();
    }

    private void init() {
        addExceptionMapping(UnknownClientUserException.class, ErrorConstants.UNKNOWN_CLIENT_USER_PROFILE, HttpStatus.NOT_FOUND);
        addExceptionMapping(IllegalArgumentException.class, ErrorConstants.ILLEGAL_ARGUMENT, HttpStatus.BAD_REQUEST);
        addExceptionMapping(SocketTimeoutException.class, ErrorConstants.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        addExceptionMapping(UnknownHostException.class, ErrorConstants.UNKNOWN_HOST, HttpStatus.BAD_GATEWAY);
        addExceptionMapping(JsonSerializationException.class, ErrorConstants.UNKNOWN_GENERIC, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    protected void addExceptionMapping(Class exceptionClass, ErrorInfo errorInfo, HttpStatus httpStatus) {
        this.exceptionMappings.put(exceptionClass, new ExceptionMapping(errorInfo, httpStatus));
    }

    public @NotNull ErrorResponse handleException(Exception e) {
        ExceptionMapping exceptionMapping = null;
        if (e instanceof ZuulException && e.getCause() != null) {
            exceptionMapping = this.exceptionMappings.get(e.getCause().getClass());
        }

        if (exceptionMapping != null) {
            ErrorInfo errorInfo = exceptionMapping.getErrorInfo();
            ErrorDTO errorDTO = new ErrorDTO(ERROR_CODE_PREFIX + errorInfo.getCode(), errorInfo.getMessage());
            return new ErrorResponse(errorDTO, exceptionMapping.getHttpStatus().value());
        }

        ErrorDTO errorDTO = new ErrorDTO(ERROR_CODE_PREFIX + ErrorConstants.UNKNOWN_GENERIC.getCode(), ErrorConstants.UNKNOWN_GENERIC.getMessage());
        return new ErrorResponse(errorDTO, org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}

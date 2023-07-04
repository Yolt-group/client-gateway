package nl.ing.lovebird.clientproxy.controller;

import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.exception.ErrorResponse;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static nl.ing.lovebird.clientproxy.controller.ErrorConstants.NOT_FOUND;

/**
 * Will be called by the {@link org.springframework.cloud.netflix.zuul.filters.post.SendErrorFilter} when an exception
 * is thrown.
 */
@Controller
@Slf4j
public class ExceptionController extends BackCompatibilityClientProxyController implements ErrorController {

    static final String SERVLET_EXCEPTION = "javax.servlet.error.exception";
    static final String SERVLET_REQUEST_URI = "javax.servlet.error.request_uri";

    private final String errorPath;
    private final ExceptionHandler exceptionHandler;

    @Autowired
    public ExceptionController(@Value("${error.path:/error}") String errorPath, ExceptionHandler exceptionHandler) {
        this.errorPath = errorPath;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public String getErrorPath() {
        return errorPath;
    }

    @RequestMapping(value = "${error.path:/error}", produces = {"application/vnd.error+json", "application/json"})
    public @ResponseBody
    ResponseEntity error(HttpServletRequest request) {
        ServletRequestAttributes requestAttributes = new ServletRequestAttributes(request);

        Optional<Exception> exceptionOptional = getRequestAttribute(requestAttributes, SERVLET_EXCEPTION);
        if (!exceptionOptional.isPresent()) {
            // This happens when there is no exception thrown in Zuul.
            // For example, if you navigate to a path that doesn't exist in this application. ie. /client-proxy/something-unknown
            Optional<String> requestUriOptional = getRequestAttribute(requestAttributes, SERVLET_REQUEST_URI);
            String originIpName = request.getRemoteAddr();

            if (requestUriOptional.isPresent()) {
                log.error("No exception on request attribute. Request to {} from ip {}", requestUriOptional.get(), originIpName);
            } else {
                log.error("Neither exception nor request_uri found in request attributes for request {} from ip {}", requestAttributes.getRequest(),
                        originIpName);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(ExceptionHandler.ERROR_CODE_PREFIX + NOT_FOUND.getCode(), NOT_FOUND.getMessage()));
        }

        ErrorResponse errorResponse = exceptionHandler.handleException(exceptionOptional.get());
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse.getErrorDTO());
    }

    private <T> Optional<T> getRequestAttribute(RequestAttributes requestAttributes, String name) {
        return (Optional<T>) Optional.ofNullable(requestAttributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST));
    }
}

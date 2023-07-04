package nl.ing.lovebird.clientproxy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.exception.JsonSerializationException;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.errorhandling.ErrorInfo;
import org.springframework.http.HttpStatus;

import static nl.ing.lovebird.clientproxy.controller.ExceptionHandler.ERROR_CODE_PREFIX;

/**
 * Use this class to reject requests based on incorrect usage of the API.
 * For example when the access token is expired.
 * The ExceptionController/Handler is used for when an internal error occurs, Zuul will then log errors.
 * We don't want to see errors for when a user uses our API incorrectly.
 */
public class ZuulRequestRejecter {
    private ZuulRequestRejecter() {}

    public static void reject(final RequestContext requestContext, final ErrorInfo errorInfo, final HttpStatus httpStatus) {
        ErrorDTO errorDTO = new ErrorDTO(ERROR_CODE_PREFIX + errorInfo.getCode(), errorInfo.getMessage());
        String responseBody = serializeError(errorDTO);

        requestContext.setSendZuulResponse(false);
        requestContext.setResponseStatusCode(httpStatus.value());
        requestContext.setResponseBody(responseBody);
    }

    private static String serializeError(final ErrorDTO errorDTO) {
        try {
            return new ObjectMapper().writeValueAsString(errorDTO);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
    }
}

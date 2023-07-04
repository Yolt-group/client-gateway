package nl.ing.lovebird.clientproxy.controller;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.exception.ErrorResponse;
import nl.ing.lovebird.clientproxy.exception.UnknownClientUserException;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;

import static nl.ing.lovebird.clientproxy.controller.ErrorConstants.NOT_FOUND;
import static nl.ing.lovebird.clientproxy.controller.ExceptionController.SERVLET_EXCEPTION;
import static nl.ing.lovebird.clientproxy.controller.ExceptionController.SERVLET_REQUEST_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionControllerTest {

    @Mock
    private Appender<ILoggingEvent> appenderMock;

    @Mock
    private ExceptionHandler mockExceptionHandler;

    @Mock
    private HttpServletRequest mockHttpServletRequest;

    private ExceptionController exceptionController;

    @BeforeEach
    void setUp() {
        this.exceptionController = new ExceptionController("/error", mockExceptionHandler);

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appenderMock);
    }

    @AfterEach
    void tearDown() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void whenNoException_shouldPrintRequestUri_andReturnNotFound() {
        when(mockHttpServletRequest.getAttribute(SERVLET_EXCEPTION)).thenReturn(null);
        when(mockHttpServletRequest.getAttribute(SERVLET_REQUEST_URI)).thenReturn("fake_uri");

        ResponseEntity<?> responseEntity = exceptionController.error(mockHttpServletRequest);

        verifyLogMessage("No exception on request attribute. Request to fake_uri from ip null");
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseEntity.getBody()).isEqualTo(notFound());
    }

    @Test
    void whenNoExceptionAndNoRequestUri_shouldPrintAttributes_andReturnNotFound() {
        ResponseEntity<?> responseEntity = exceptionController.error(mockHttpServletRequest);

        verifyLogMessage("Neither exception nor request_uri found in request attributes for request mockHttpServletRequest from ip null");
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseEntity.getBody()).isEqualTo(notFound());
    }

    @Test
    void whenTokenOk_shouldNotAddAuthHeader() {
        when(mockHttpServletRequest.getAttribute(SERVLET_EXCEPTION)).thenReturn(new Exception("test exception"));
        when(mockExceptionHandler.handleException(any(Exception.class))).thenReturn(errorResponse(ErrorConstants.DESTINATION_NOT_AVAILABLE));

        ResponseEntity<?> responseEntity = exceptionController.error(mockHttpServletRequest);

        assertThat(responseEntity.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNullOrEmpty();
    }

    private static ErrorDTO notFound() {
        return new ErrorDTO(ExceptionHandler.ERROR_CODE_PREFIX + NOT_FOUND.getCode(), NOT_FOUND.getMessage());
    }

    private static ErrorResponse errorResponse(ErrorConstants error) {
        return new ErrorResponse(new ErrorDTO(ExceptionHandler.ERROR_CODE_PREFIX + error.getCode(), error.getMessage()), 0);
    }

    @Test
    void shouldCallExceptionHandlerAndReturnResultCorrectly() {
        RuntimeException exception = new RuntimeException("This is a testing exception");
        ErrorResponse errorResponseToReturn = new ErrorResponse(
                new ErrorDTO(ExceptionHandler.ERROR_CODE_PREFIX + ErrorConstants.UNKNOWN_GENERIC.getCode(), ErrorConstants.UNKNOWN_GENERIC.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        when(mockHttpServletRequest.getAttribute(eq(SERVLET_EXCEPTION)))
                .thenReturn(exception);
        when(mockExceptionHandler.handleException(eq(exception)))
                .thenReturn(errorResponseToReturn);

        ResponseEntity<?> response = exceptionController.error(mockHttpServletRequest);
        verify(mockExceptionHandler, times(1)).handleException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(errorResponseToReturn.getErrorDTO(), response.getBody());
    }

    @Test
    void shouldNotCallExceptionHandlerAndReturnNotFoundWhenAttributeEmpty() {
        String expectedCode = ExceptionHandler.ERROR_CODE_PREFIX + ErrorConstants.NOT_FOUND.getCode();
        String expectedMessage = ErrorConstants.NOT_FOUND.getMessage();

        ResponseEntity<?> response = exceptionController.error(mockHttpServletRequest);
        verifyNoInteractions(mockExceptionHandler);

        assertTrue(response.getBody() instanceof ErrorDTO);
        ErrorDTO errorDtoResult = (ErrorDTO) response.getBody();
        assertEquals(expectedCode, errorDtoResult.getCode());
        assertEquals(expectedMessage, errorDtoResult.getMessage());
    }

    @Test
    void shouldCallExceptionHandlerAndReturnMatchingError() {
        UnknownClientUserException exception = new UnknownClientUserException("some message");
        String expectedCode = ExceptionHandler.ERROR_CODE_PREFIX + ErrorConstants.UNKNOWN_CLIENT_USER_PROFILE.getCode();
        String expectedMessage = ErrorConstants.UNKNOWN_CLIENT_USER_PROFILE.getMessage();
        HttpStatus expectedStatus = HttpStatus.UNAUTHORIZED;

        when(mockHttpServletRequest.getAttribute(eq(SERVLET_EXCEPTION)))
                .thenReturn(exception);
        when(mockExceptionHandler.handleException(eq(exception)))
                .thenReturn(new ErrorResponse(new ErrorDTO(expectedCode, expectedMessage), expectedStatus.value()));

        ResponseEntity<?> response = exceptionController.error(mockHttpServletRequest);

        verify(mockExceptionHandler, times(1)).handleException(exception);
        assertTrue(response.getBody() instanceof ErrorDTO);
        ErrorDTO errorDtoResult = (ErrorDTO) response.getBody();
        assertEquals(expectedCode, errorDtoResult.getCode());
        assertEquals(expectedMessage, errorDtoResult.getMessage());
        assertEquals(expectedStatus, response.getStatusCode());
    }

    private void verifyLogMessage(final String message) {
        verify(appenderMock).doAppend(argThat(argument -> message.equals(argument.getFormattedMessage())));
    }
}

package nl.ing.lovebird.clientproxy.filter.filters.post;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.service.kafka.IncomingRequestEventProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.MalformedURLException;
import java.net.URL;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggingFilterTest {

    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    LoggingFilter loggingFilter;
    RequestContext ctx;

    @Mock
    Appender<ILoggingEvent> mockAppender;

    @Mock
    IncomingRequestEventProducer incomingRequestEventProducer;

    @BeforeEach
    void setup() {
        loggingFilter = new LoggingFilter(incomingRequestEventProducer);

        ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.addAppender(mockAppender);
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void shouldLogEmptyRequestAndResponse() {
        loggingFilter.run();
        verifyLogMessage("Request URI : , Routed to: NO_ROUTE, Response status : NO_RESPONSE,  Filter summary ");
    }

    @Test
    void shouldLogRequestAndResponse() throws MalformedURLException {
        ctx.setResponseStatusCode(500);
        ctx.setRouteHost(new URL("http://example.com/pages"));
        ctx.set("requestURI", "/this/is/some/uri"); // will be put there by the spring 'PredecorationFilter'.
        request.setRequestURI("/this/is/some/uri");
        loggingFilter.run();
        verifyLogMessage("Request URI : /this/is/some/uri, Routed to: http://example.com/pages/this/is/some/uri, Response status : 500,  Filter summary ");
    }

    private void verifyLogMessage(final String message) {
        verify(mockAppender).doAppend(argThat(argument -> message.equals(argument.getFormattedMessage())));
    }
}

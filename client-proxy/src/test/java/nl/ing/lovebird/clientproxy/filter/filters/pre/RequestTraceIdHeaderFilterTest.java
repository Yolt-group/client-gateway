package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestTraceIdHeaderFilterTest {

    @Mock
    private HttpServletRequest request;

    private RequestTraceIdHeaderFilter requestTraceIdHeaderFilter;

    @BeforeEach
    void setup() {
        requestTraceIdHeaderFilter = new RequestTraceIdHeaderFilter();
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void shouldHaveFilterTypePre() {
        assertEquals(FilterType.PRE.getType(), requestTraceIdHeaderFilter.filterType());
    }

    @Test
    void shouldHaveFilterOrder() {
        assertEquals(ApplicationConfiguration.FILTER_ORDER_REQUEST_TRACE_ID_FILTER, requestTraceIdHeaderFilter.filterOrder());
    }

    @Test
    void shouldHaveTraceIdInRequestHeader() {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.setRequest(request);

        requestTraceIdHeaderFilter.run();

        assertNotNull(RequestContext.getCurrentContext().getZuulRequestHeaders().get(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME));
    }

    @Test
    void shouldReturnDifferentValueForTwoConsecutiveInvocations() {
        RequestContext ctx = RequestContext.getCurrentContext();

        // First invocation
        ctx.clear();
        ctx.setRequest(request);
        requestTraceIdHeaderFilter.run();
        String firstTraceId = ctx.getZuulRequestHeaders().get(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME);

        // Second invocation
        ctx.clear();
        ctx.setRequest(request);
        requestTraceIdHeaderFilter.run();
        String secondTraceId = ctx.getZuulRequestHeaders().get(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME);

        assertNotEquals(firstTraceId, secondTraceId);
    }

    @Test
    void requestWithExistingTraceId() {
        RequestContext ctx = RequestContext.getCurrentContext();
        final String requestTraceId = UUID.randomUUID().toString();
        when(request.getHeader(eq(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME))).thenReturn(requestTraceId);
        ctx.setRequest(request);

        requestTraceIdHeaderFilter.run();
        String firstTraceId = ctx.getZuulRequestHeaders().get(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME);
        assertEquals(requestTraceId, firstTraceId);
    }
}

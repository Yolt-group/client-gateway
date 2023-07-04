package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import org.jboss.logging.MDC;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_REQUEST_TRACE_ID_FILTER;
import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME;

@Component
public class RequestTraceIdHeaderFilter extends ZuulFilter {

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public boolean shouldFilter() {
        return RequestContext.getCurrentContext().sendZuulResponse();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_REQUEST_TRACE_ID_FILTER;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        final String header = request.getHeader(REQUEST_TRACE_ID_HEADER_NAME);
        final UUID requestTraceId = header == null ? UUID.randomUUID() : UUID.fromString(header);
        ctx.addZuulRequestHeader(REQUEST_TRACE_ID_HEADER_NAME, requestTraceId.toString());
        MDC.put("request_trace_id", requestTraceId.toString());

        return null;
    }

}

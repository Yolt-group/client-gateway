package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Scope("prototype")
@Slf4j
public class ZuulHeadersHelper {

    static final String IGNORED_HEADERS_SET = "ignoredHeaders";

    /**
     * Zuul by default will ignore any headers specified as 'ignoredHeaders'
     * Currently we ignore "client-id, client-user-id, user-id, x-user-context" headers and enable them back if we set
     * values for them ourselves. It prevents processing headers which were set by external parties in order to try to
     * penetrate our system
     *
     * @param ctx         - Zuul request context
     * @param headerName  - header name to be added and enabled
     * @param headerValue - header value
     */
    public void setAndEnable(RequestContext ctx, String headerName, String headerValue) {
        ctx.addZuulRequestHeader(headerName, headerValue);
        Set<String> ignoredHeaders = (Set<String>) ctx.get(IGNORED_HEADERS_SET);
        if (ignoredHeaders == null || ignoredHeaders.isEmpty()) {
            log.debug("Zuul ignored headers are empty, doing nothing");
        } else {
            ignoredHeaders.remove(headerName);
            log.debug("Removing header {} from list of ignored headers", headerName);
        }
    }
}

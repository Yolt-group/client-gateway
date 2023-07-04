package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import org.springframework.stereotype.Component;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.FILTER_ORDER_REMOVE_PROXY_FORWARDED_PREFIX;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.X_FORWARDED_PREFIX_HEADER;

@Component
public class RemoveProxyForwardedPrefixFilter extends ZuulFilter {

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
        return FILTER_ORDER_REMOVE_PROXY_FORWARDED_PREFIX;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.getZuulRequestHeaders().remove(X_FORWARDED_PREFIX_HEADER.toLowerCase());
        return null;
    }

}

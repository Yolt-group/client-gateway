package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.requester.service.ClientTokenRequesterService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClientTokenFilter extends ZuulFilter {

    private final ZuulHeadersHelper zuulHeadersHelper;
    private final ClientTokenRequesterService clientTokenRequesterService;

    @Override
    public String filterType() {
        return FilterType.PRE.getType();
    }

    @Override
    public int filterOrder() {
        return FILTER_ORDER_CLIENTTOKEN_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        String clientId = ctx.getZuulRequestHeaders().get(CLIENT_ID_HEADER_NAME);
        return ctx.sendZuulResponse() && !StringUtils.isEmpty(clientId) && ctx.get(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME) == null;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        String clientId = ctx.getZuulRequestHeaders().get(CLIENT_ID_HEADER_NAME);
        ClientToken clientToken = clientTokenRequesterService.getClientToken(UUID.fromString(clientId));
        ctx.set(CLIENT_TOKEN_HEADER_NAME, clientToken);
        zuulHeadersHelper.setAndEnable(ctx, CLIENT_TOKEN_HEADER_NAME, clientToken.getSerialized());
        return null;
    }
}

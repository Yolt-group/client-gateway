package nl.ing.lovebird.clientproxy.configuration;

import io.micrometer.core.instrument.Tag;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.CLIENT_ID_HEADER_NAME;

@Configuration
public class ClientIdMetricTagConfiguration {

    @Bean
    public WebMvcTagsContributor clientIdTagContributor() {
        return new WebMvcTagsContributor() {
            @Override
            public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                String clientId = (String) request.getAttribute(CLIENT_ID_HEADER_NAME);
                return Collections.singletonList(Tag.of("client-id", clientId == null ? "" : clientId));
            }

            @Override
            public Iterable<Tag> getLongRequestTags(HttpServletRequest request, Object handler) {
                return Collections.emptyList();
            }
        };
    }

}

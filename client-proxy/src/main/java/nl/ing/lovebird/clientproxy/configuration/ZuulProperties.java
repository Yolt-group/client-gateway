package nl.ing.lovebird.clientproxy.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@ConfigurationProperties("zuul")
public class ZuulProperties {

    private Map<String, ZuulRoute> routes = new LinkedHashMap<>();

    @Data
    public static class ZuulRoute {

        private String path;

        private Set<ZuulSubPath> subPaths = new LinkedHashSet<>();
    }

    @Data
    public static class ZuulSubPath {

        private String subPath;

        private Set<String> openHttpMethods;

        private Set<String> authenticatedHttpMethods;

        private Set<String> overrideHttpMethods;

        private String overrideUrl;
    }
}

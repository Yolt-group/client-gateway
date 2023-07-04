package nl.ing.lovebird.clientproxy.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Map / (root) to static resource /rootlinks.json in lieu of an easy way to do this in Zuul config only.
 */
@RestController
@ConditionalOnProperty(prefix = "yolt.hateos", name = "rootlinks")
public class RootLinksController extends BackCompatibilityClientProxyController {
    @GetMapping(produces = "application/hal+json;charset=UTF-8")
    public ClassPathResource root() {
        return new ClassPathResource("/rootlinks.json");
    }

    @GetMapping(value = "/rootlinks.json", produces = "application/hal+json;charset=UTF-8")
    public ClassPathResource rootlinks() {
        return new ClassPathResource("/rootlinks.json");
    }
}

package nl.ing.lovebird.clientproxy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

// TODO: Remove after back compatibility for "client-proxy" wouldn't be needed
@RequestMapping(path = {"/", "/client-proxy"})
@Controller
public abstract class BackCompatibilityClientProxyController {
}

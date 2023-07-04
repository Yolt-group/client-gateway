package nl.ing.lovebird.clientproxy.configuration;

import org.springframework.core.annotation.Order;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement
 */
@ControllerAdvice
@Order
public class Spring4ShellMitigatingControllerAdvice {

    @InitBinder
    public void setAllowedFields(WebDataBinder dataBinder) {
        dataBinder.setDisallowedFields("class.*", "Class.*", "*.class.*", "*.Class.*");
    }

}

package nl.ing.lovebird.clientproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.kafka.annotation.EnableKafka;

@EnableZuulProxy
@SpringBootApplication
@EnableKafka
public class ClientProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientProxyApplication.class, args);
    }

}

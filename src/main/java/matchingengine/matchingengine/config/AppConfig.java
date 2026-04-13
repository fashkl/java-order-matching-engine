package matchingengine.matchingengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderBookRepository;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderRepository;
import matchingengine.matchingengine.application.service.OrderApplicationService;
import matchingengine.matchingengine.domain.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Matching Engine API")
                        .description("Price-time priority limit order matching engine")
                        .version("0.0.1-SNAPSHOT"));
    }

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }

    @Bean
    public InMemoryOrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    public InMemoryOrderBookRepository orderBookRepository() {
        return new InMemoryOrderBookRepository();
    }

    @Bean
    public OrderApplicationService orderApplicationService() {
        return new OrderApplicationService(
                matchingEngine(),
                orderRepository(),
                orderBookRepository()
        );
    }
}

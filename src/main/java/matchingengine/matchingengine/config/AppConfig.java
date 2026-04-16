package matchingengine.matchingengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import matchingengine.matchingengine.adapter.out.eventbus.SpringDomainEventPublisher;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderBookRepository;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderRepository;
import matchingengine.matchingengine.application.service.OrderApplicationService;
import matchingengine.matchingengine.domain.MatchingEngine;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
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
    public SpringDomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringDomainEventPublisher(applicationEventPublisher);
    }

    @Bean
    public OrderApplicationService orderApplicationService(SpringDomainEventPublisher domainEventPublisher) {
        return new OrderApplicationService(
                matchingEngine(),
                orderRepository(),
                orderBookRepository(),
                domainEventPublisher
        );
    }
}

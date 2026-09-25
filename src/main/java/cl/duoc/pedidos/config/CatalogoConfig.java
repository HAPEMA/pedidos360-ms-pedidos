package cl.duoc.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CatalogoConfig {

    @Bean
    public RestClient catalogoRestClient(@Value("${catalogo.url}") String catalogoUrl) {
        // Timeouts cortos: si ms-catalogo se cuelga, respondemos 502 en vez de retener hilos indefinidamente.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(catalogoUrl)
                .requestFactory(factory)
                .build();
    }
}

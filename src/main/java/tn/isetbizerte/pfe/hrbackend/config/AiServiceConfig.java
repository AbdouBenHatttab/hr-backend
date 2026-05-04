package tn.isetbizerte.pfe.hrbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * AiServiceConfig
 * ---------------
 * Provides a dedicated RestTemplate bean for communication with the FastAPI AI service.
 *
 * This bean is separate from the global RestTemplate in AppConfig.
 * It carries explicit connect and read timeouts so that an unresponsive
 * FastAPI service cannot hang a Spring Boot thread indefinitely.
 *
 * The timeout values are configured via application.properties and
 * can be overridden through environment variables in production.
 */
@Configuration
public class AiServiceConfig {

    @Value("${app.ai-service.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Dedicated RestTemplate for the FastAPI AI service.
     * Named "aiServiceRestTemplate" to avoid ambiguity with the global bean.
     */
    @Bean("aiServiceRestTemplate")
    public RestTemplate aiServiceRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }
}

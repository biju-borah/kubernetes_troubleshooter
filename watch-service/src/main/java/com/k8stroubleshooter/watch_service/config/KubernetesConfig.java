package com.k8stroubleshooter.watch_service.config;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.io.IOException;

@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient getApiClient() throws IOException {
        ApiClient client = ClientBuilder.standard().build();
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    public CoreV1Api getCoreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public SharedInformerFactory getSharedInformerFactory(){
        return new SharedInformerFactory();
    }
}

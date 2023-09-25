package com.softvarivm.pocs.webclient.configuration;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties
public class GeneralConfiguration {
  @Value("${service.remote.baseUrl}")
  private String baseUrl;
  @Value("${service.remote.port}")
  private int port;
  public static final int TIMEOUT = 1000;

  @Bean("webClientNoSsl")
  public WebClient webClientWithTimeout() {
    HttpClient httpClient =
        HttpClient.create()
            .baseUrl(baseUrl)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT)
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)));
    // .wiretap(true);
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }

  @Bean("webClientSslTrustAllCerts")
  public WebClient webClienSsltWithTimeout() throws SSLException {
    var sslContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    HttpClient httpClient =
        HttpClient.create()
            .baseUrl(baseUrl).port(port)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT)
            .doOnConnected(
                conn -> conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT, TimeUnit.MILLISECONDS)))
            .secure(
                spec -> {
                  SslProvider.Builder builder = spec.sslContext(sslContext);
                });
    // .wiretap(true);
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }
}

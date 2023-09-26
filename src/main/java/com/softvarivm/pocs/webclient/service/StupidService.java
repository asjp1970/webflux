package com.softvarivm.pocs.webclient.service;

import com.softvarivm.pocs.webclient.entities.ProblemDetails;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class StupidService {
  private static final Logger LOG = LogManager.getLogger(StupidService.class);
  private final WebClient webClient;

  private volatile String instance;
  private volatile int errSc;

  private final AtomicInteger pendingResponsesCtr = new AtomicInteger();

  public StupidService(@Qualifier("webClientSslTrustAllCerts") final WebClient wc) {
    webClient = wc;
  }

  public Mono<Object> saySomething(final String scenario) {
    LOG.info("Entering service to fetch remote resources with WebClient (scenario '{}')", scenario);
    pendingResponsesCtr.incrementAndGet();
    instance = String.format("/mock/%s", scenario);
    return webClient
        .get()
        .uri(instance)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError, response -> handleClientErrors(response.statusCode()))
        .onStatus(
            HttpStatusCode::is5xxServerError, response -> handleServerErrors(response.statusCode()))
        .bodyToMono(Object.class)
        .doOnError(err -> LOG.info("Error Occurred: {}", err.getMessage()))
        .doOnSuccess(
            b ->
                LOG.info(
                    "Successful response arrived: {}. There are {} requests waiting for an answer",
                    b,
                    pendingResponsesCtr.decrementAndGet()))
        .retryWhen(
            Retry.backoff(3, Duration.ofMillis(500L))
                .filter(t -> t instanceof ServiceRetryErrException))
        .onErrorResume(err -> Mono.just(createProblemDetails(err)));
  }

  private Mono<? extends Throwable> handleClientErrors(final HttpStatusCode statusCode) {
    pendingResponsesCtr.decrementAndGet();
    errSc = statusCode.value();
    final HttpStatus httpSc = HttpStatus.resolve(statusCode.value());
    if (httpSc == null) {
      return Mono.error(new ServiceException("Unknown status code received from Server", errSc));
    }
    final Mono<? extends Throwable> monoErrResult;
    switch (httpSc) {
      case BAD_REQUEST -> monoErrResult =
          Mono.error(new ServiceException("Bad Request, try again with something better", errSc));
      case NOT_FOUND -> monoErrResult =
          Mono.error(new ServiceException("Resource not found", errSc));
      default -> monoErrResult =
          Mono.error(new ServiceException("Unexpected status code received from Server", errSc));
    }
    return monoErrResult;
  }

  private Mono<? extends Throwable> handleServerErrors(final HttpStatusCode statusCode) {
    pendingResponsesCtr.decrementAndGet();
    errSc = statusCode.value();
    final HttpStatus httpSc = HttpStatus.resolve(statusCode.value());
    if (httpSc == null) {
      return Mono.error(new ServiceException("Unknown status code received from Server", errSc));
    }
    final Mono<? extends Throwable> monoErrResult;
    switch (httpSc) {
      case SERVICE_UNAVAILABLE -> monoErrResult =
          Mono.error(new ServiceRetryErrException("Remote server is unavailable", errSc));
      case GATEWAY_TIMEOUT -> monoErrResult =
          Mono.error(new ServiceRetryErrException("Gateway timeout", errSc));
      default -> monoErrResult =
          Mono.error(new ServiceException("Unexpected status code received from Server", errSc));
    }
    return monoErrResult;
  }

  private ProblemDetails createProblemDetails(final Throwable t) {
    final ProblemDetails p = new ProblemDetails();
    p.setTitle("Problem accessing/updating remote resources hosted in Wiremock cloud");
    p.setInstance(instance);
    if (t instanceof ServiceException e) {
      p.setStatus(e.getSc());
    } else {
      p.setStatus(this.errSc);
    }
    p.setCause(t.getMessage() != null ? t.getMessage() : null);
    p.setDetail("WireMock was told to fail in this case: you got what you mocked");
    return p;
  }

  int getPendingRequestsCounter() {
    return pendingResponsesCtr.get();
  }
}

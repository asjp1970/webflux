package com.softvarivm.pocs.webclient.service;

import com.softvarivm.pocs.webclient.configuration.GeneralConfiguration;
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

/**
 * This service uses {@link WebClient} to send a http GET operation to a remote service. The {@link
 * WebClient} is a bean injected, and it is configured in {@link GeneralConfiguration}.<br>
 * The remote service is an instance of <a href="https://app.wiremock.cloud/">WireMock Cloud</a>
 * programmed like this:
 *
 * <ul>
 *   <li>url {@code /mock/ok} will return a {@code 201} with some nice body
 *   <li>url {@code /mock/fail} will return whatever error is configured in WireMock on cloud
 * </ul>
 *
 * This service retries up to {@link #MAX_RETRIES} in case any of the following situations occur:
 *
 * <ul>
 *   <li>the response comes with http status code 500 SERVER_ERROR
 *   <li>the response comes with http status code 503 SERVICE_UNAVAILABLE
 * </ul>
 */
@Service
public class StupidService {
  private static final Logger LOG = LogManager.getLogger(StupidService.class);
  private final WebClient webClient;

  private volatile String instance;
  private volatile int errSc;

  private final AtomicInteger pendingResponsesCtr = new AtomicInteger();
  private static final int MAX_RETRIES = 3;

  public StupidService(@Qualifier("webClientSslTrustAllCerts") final WebClient wc) {
    webClient = wc;
  }

  /**
   * send a http GET operation to a remote service.
   *
   * @param scenario
   * @return a {@link Mono} wrapping the body of the response received, or a {@link Throwable}
   *     representing the error returned.
   */
  public Mono<Object> saySomething(final String scenario) {
    LOG.info("Entering service to fetch remote resources with WebClient (scenario '{}')", scenario);
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
        .doFirst(pendingResponsesCtr::incrementAndGet)
        .doOnError(err -> LOG.info("Error Occurred: {}", err.getMessage()))
        .doOnSuccess(b -> LOG.info("Successful response arrived with body: {}", b))
        .retryWhen(
            Retry.backoff(MAX_RETRIES, Duration.ofMillis(500L))
                .filter(t -> t instanceof ServiceRetryErrException)
                // takes care of keeping the balance of pending requests waiting for an answer in
                // every retry
                .doAfterRetry(retrySignal -> pendingResponsesCtr.decrementAndGet()))
        .doFinally(
            // decrement the counter for the first request pending og and answer (and the only one
            // if there were no retries)
            signalType -> {
              LOG.info(
                  "There are {} requests waiting for an answer",
                  pendingResponsesCtr.decrementAndGet());
            })
        .onErrorResume(
            err -> {
              LOG.info("Error: {}", err.getMessage());
              return Mono.just(createProblemDetails(err));
            });
  }

  private Mono<? extends Throwable> handleClientErrors(final HttpStatusCode statusCode) {
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

  // VisibleForTesting
  int getPendingRequestsCounter() {
    LOG.info("Counter of pending responses is: {}", pendingResponsesCtr.get());
    return pendingResponsesCtr.get();
  }
}

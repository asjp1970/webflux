package com.softvarivm.pocs.webclient.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllScenarios;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

/**
 * httpsPort MUST match the value set in property {@code service.remote.httpsPort} of {@code
 * application-test.yaml}
 */
@WireMockTest(httpsEnabled = true, httpsPort = 8473)
@SpringBootTest
@ActiveProfiles("test")
class StupidServiceTest {

  private static final Logger LOG = LogManager.getLogger(StupidServiceTest.class);
  private static final String TEST_OK_PATH = "/mock/ok";
  private static final String TEST_FAIL_PATH = "/mock/fail";
  private static final String PARAM_SCENARIO_OK = "ok";
  private static final String PARAM_SCENARIO_FAIL = "fail";
  private static final String OK_RESULT_BODY =
      "{\"scenario\":\"ok\",\"value\":\"here you have a value\"}";
  private static final String PROBLEM_DETAILS_BODY =
      "{\"status\":503,\"title\":\"Problem accessing/updating remote resources hosted in Wiremock cloud\",\"instance\":\"/mock/fail\",\"cause\":\"Retries exhausted: 3/3\",\"detail\":\"WireMock was told to fail in this case: you got what you mocked\"}";

  private static final String RETRY_SCENARIO = "retry_scenario";
  private static final String RETRY_AGAIN_STATE = "retry_again";
  private static final String RECOVERED_STATE = "recovered_state";
  @Autowired private StupidService serviceUt;

  @AfterEach
  void resetAfterEach() {
    resetAllScenarios();
  }

  /**
   * test that the request sent with WebClient is properly built, and it matches expectations. We
   * wait until the response arrives, by calling {@code block()}. <br>
   * Everything works as if we were using the old {@code RestTemplate}: blocking and synchronous,
   * easy to understand.
   */
  @Test
  void testSaySomethingOkBlocking() {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt.saySomething(PARAM_SCENARIO_OK).block();

    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  /**
   * same test but this time we subscribe to the {@link Mono} returned by the service.
   * "Surprisingly" it doesn't work... why?
   */
  @Disabled("To be executed manually for teaching purposes")
  @Test
  void testSaySomethingOkNonBlockingBadApproach() {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt.saySomething(PARAM_SCENARIO_OK).subscribe();

    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  /**
   * same test. This variant shows callbacks passed in the subscribe() method call and the test now
   * works... But the approach is not the best. What can be improved?
   *
   * @throws InterruptedException
   */
  @Test
  void testSaySomethingOkNotSoGoodButWorkingApproach_DontDoThis() throws InterruptedException {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt
        .saySomething(PARAM_SCENARIO_OK)
        .flatMap(this::getJsonPrettyString)
        .subscribe(this::bodyConsumer, this::errConsumer);

    Thread.sleep(2000L);

    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  /**
   * same test properly done: wait for asynchronous operations to finish, without blocking, during,
   * at most, a certain amount of time until a certain condition is. In this case we use <a
   * href="http://www.awaitility.org/">Awaitility</a> library, a DSL that allows you to express
   * expectations of an asynchronous system in a concise and easy to read manner.
   */
  @Test
  void testSaySomethingOkBestApproach() {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt
        .saySomething(PARAM_SCENARIO_OK)
        .flatMap(this::getJsonPrettyString)
        .subscribe(this::bodyConsumer, this::errConsumer);

    LOG.info("We have subscribed and execution continues here, while WebClient does its job async");

    // wait - at most - until... Comments are nearly offensive
    await().atMost(2L, TimeUnit.SECONDS).until(() -> serviceUt.getPendingRequestsCounter() == 0);

    LOG.info("Reactive stream finished, successfully in this case");

    // now we are ready to do assessments
    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  @Test
  void testSaySomethingOkWitErrResponseBestApproach() {
    stubFor(get(TEST_OK_PATH).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

    serviceUt
        .saySomething(PARAM_SCENARIO_OK)
        .flatMap(this::getJsonPrettyString)
        .subscribe(this::bodyConsumer, this::errConsumer);

    // wait - at most - until... Comments are nearly offensive
    await().atMost(2L, TimeUnit.SECONDS).until(() -> serviceUt.getPendingRequestsCounter() == 0);

    // now we are ready to do assessments
    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  @Test
  void testSaySomethingFailWithRetries() {
    stubFor(
        get(TEST_FAIL_PATH)
            .inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()))
            .willSetStateTo(RETRY_AGAIN_STATE));

    stubFor(
        get(TEST_FAIL_PATH)
            .inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(RETRY_AGAIN_STATE)
            .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()))
            .willSetStateTo(RETRY_AGAIN_STATE));

    serviceUt
        .saySomething(PARAM_SCENARIO_FAIL)
        .flatMap(this::getJsonPrettyString)
        .subscribe(this::bodyConsumer, this::errConsumer);

    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(8L, TimeUnit.SECONDS)
        .until(() -> serviceUt.getPendingRequestsCounter() == 0);

    verify(4, getRequestedFor(urlEqualTo(TEST_FAIL_PATH)));
  }

  @Test
  void testSaySomethingFailWithRetriesAndRecover() {
    stubFor(
        get(TEST_FAIL_PATH)
            .inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()))
            .willSetStateTo(RETRY_AGAIN_STATE));
    stubFor(
        get(TEST_FAIL_PATH)
            .inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(RETRY_AGAIN_STATE)
            .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()))
            .willSetStateTo(RECOVERED_STATE));
    stubFor(
        get(TEST_FAIL_PATH)
            .inScenario(RETRY_SCENARIO)
            .whenScenarioStateIs(RECOVERED_STATE)
            .willReturn(okJson(OK_RESULT_BODY))
            .willSetStateTo(RETRY_AGAIN_STATE));

    serviceUt
        .saySomething(PARAM_SCENARIO_FAIL)
        .flatMap(this::getJsonPrettyString)
        .subscribe(this::bodyConsumer, this::errConsumer);

    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(8L, TimeUnit.SECONDS)
        .until(() -> serviceUt.getPendingRequestsCounter() == 0);

    verify(3, getRequestedFor(urlEqualTo(TEST_FAIL_PATH)));
  }

  private Mono<String> getJsonPrettyString(final Object o) {
    final var ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return Mono.just(ow.writeValueAsString(o));
    } catch (JsonProcessingException e) {
      return Mono.error(e);
    }
  }

  private void bodyConsumer(final String b) {
    LOG.info("Body received: {}", b);
  }

  private void errConsumer(final Throwable e) {
    LOG.info("Error received: ", e);
  }
}

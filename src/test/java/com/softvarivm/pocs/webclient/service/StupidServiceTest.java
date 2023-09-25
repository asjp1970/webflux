package com.softvarivm.pocs.webclient.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@WireMockTest(httpsEnabled = true, httpsPort = 8066)
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

  @Autowired private StupidService serviceUt;

  @BeforeEach
  void beforeEach(final WireMockRuntimeInfo wmRuntimeInfo) {
    LOG.info(wmRuntimeInfo.getHttpsBaseUrl());
  }
  /**
   * test that the request sent with WebClient is properly built, and it matches expectations.
   * But this will fail... why?
   */
  @Test
  void testSaySomethingBadApproach() {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt.saySomething(PARAM_SCENARIO_OK);

    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }

  @Test
  void testSaySomethingBetterApproach() {
    stubFor(get(TEST_OK_PATH).willReturn(okJson(OK_RESULT_BODY)));

    serviceUt.saySomething(PARAM_SCENARIO_OK).block();

    await().atLeast(2L, TimeUnit.SECONDS);

    verify(1, getRequestedFor(urlEqualTo(TEST_OK_PATH)));
  }
}

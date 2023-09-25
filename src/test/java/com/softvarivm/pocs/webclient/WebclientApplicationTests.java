package com.softvarivm.pocs.webclient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class WebclientApplicationTests {

  @Autowired private final ApplicationContext appCtxt;

  WebclientApplicationTests(ApplicationContext appCtxt) {
    this.appCtxt = appCtxt;
  }

  @Test
  void contextLoads() {
    assertNotNull(appCtxt.getBean("webClientNoSsl"));
    assertNotNull(appCtxt.getBean("webClientSslTrustAllCerts"));
  }
}

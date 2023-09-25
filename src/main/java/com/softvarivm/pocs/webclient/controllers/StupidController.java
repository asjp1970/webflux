package com.softvarivm.pocs.webclient.controllers;

import com.softvarivm.pocs.webclient.service.StupidService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class StupidController {

  private static final Logger LOG = LogManager.getLogger(StupidController.class);

  private final StupidService svc;

  @GetMapping(value = "/{scenario}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Object> getSomething(@PathVariable String scenario) {
    LOG.info("Request received");
    return svc.saySomething(scenario);
  }
}

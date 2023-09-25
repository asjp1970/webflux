package com.softvarivm.pocs.webclient.service;

import lombok.Getter;

public class ServiceException extends Exception {
  @Getter private final int sc;

  public ServiceException(final String msg, final int status) {
    super(msg);
    sc = status;
  }
}

package com.softvarivm.pocs.webclient.service;

public class ServiceRetryErrException extends ServiceException {
  public ServiceRetryErrException(String msg, int status) {
    super(msg, status);
  }
}

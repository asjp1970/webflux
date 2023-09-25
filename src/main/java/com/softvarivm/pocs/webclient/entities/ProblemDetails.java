package com.softvarivm.pocs.webclient.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProblemDetails {

  private int status;

  @JsonInclude(Include.NON_EMPTY)
  private String title;

  @JsonInclude(Include.NON_EMPTY)
  private String instance;

  @JsonInclude(Include.NON_EMPTY)
  private String cause;

  @JsonInclude(Include.NON_EMPTY)
  private String detail;
}

package br.com.ragro.controller.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProducerFilter {
  private String query;
  private Double minRating;
  private String sortBy = "rating";
}
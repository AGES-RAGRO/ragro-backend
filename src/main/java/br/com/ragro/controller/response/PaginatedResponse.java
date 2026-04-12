package br.com.ragro.controller.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

/**
 * Stable JSON wrapper for paginated results.
 * Replaces PageImpl serialization which Spring Data warns is unstable.
 */
@Getter
@Builder
public class PaginatedResponse<T> {

  private List<T> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;

  public static <T> PaginatedResponse<T> of(Page<T> page) {
    return PaginatedResponse.<T>builder()
        .content(page.getContent())
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }
}

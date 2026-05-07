package br.com.ragro.domain.specification;

import br.com.ragro.controller.request.StockMovementFilter;
import br.com.ragro.domain.StockMovement;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class StockMovementSpecification {

  private StockMovementSpecification() {}

  public static Specification<StockMovement> withFilter(UUID producerId, StockMovementFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      predicates.add(cb.equal(root.get("product").get("farmer").get("id"), producerId));

      if (filter.getProductId() != null) {
        predicates.add(cb.equal(root.get("product").get("id"), filter.getProductId()));
      }

      if (filter.getReason() != null) {
        predicates.add(cb.equal(root.get("reason"), filter.getReason()));
      }

      if (filter.getType() != null) {
        predicates.add(cb.equal(root.get("type"), filter.getType()));
      }

      if (filter.getFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
      }

      if (filter.getTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
      }

      query.orderBy(cb.desc(root.get("createdAt")));

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
package br.com.ragro.domain.specification;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class ProducerSpecification {

  private ProducerSpecification() {}

  public static Specification<Producer> withFilter(ProducerFilter filter) {
    return (root, query, cb) -> {
      query.distinct(true);

      Join<Producer, User> userJoin;
      if (Long.class.equals(query.getResultType())) {
        userJoin = root.join("user", JoinType.INNER);
      } else {
        @SuppressWarnings("unchecked")
        Join<Producer, User> fetchJoin =
            (Join<Producer, User>) (Object) root.fetch("user", JoinType.INNER);
        userJoin = fetchJoin;
      }

      List<Predicate> predicates = new ArrayList<>();

      predicates.add(cb.isTrue(userJoin.get("active")));

      if (filter.getName() != null && !filter.getName().isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("farmName")),
                "%" + filter.getName().toLowerCase() + "%"));
      }

      if (filter.getMinRating() != null) {
        predicates.add(
            cb.greaterThanOrEqualTo(
                root.get("averageRating"),
                BigDecimal.valueOf(filter.getMinRating())));
      }

      String sortBy = filter.getSortBy();
      if ("name".equals(sortBy)) {
        query.orderBy(cb.asc(root.get("farmName")));
      } else if ("orders".equals(sortBy)) {
        query.orderBy(cb.desc(root.get("totalOrders")));
      } else {
        query.orderBy(cb.desc(root.get("averageRating")));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}

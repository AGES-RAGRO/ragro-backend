package br.com.ragro.domain.specification;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
        Join<Producer, User> fetchUser = (Join<Producer, User>) (Object)
                root.fetch("user", JoinType.INNER);
        userJoin = fetchUser;
      }

      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isTrue(userJoin.get("active")));

      if (filter.getQuery() != null && !filter.getQuery().isBlank()) {
        String term = "%" + filter.getQuery().toLowerCase() + "%";

        var productSub = query.subquery(UUID.class);
        var product = productSub.from(Product.class);
        productSub.select(product.get("farmer").get("id"))
                .where(cb.like(cb.lower(product.get("name")), term));

        var categorySub = query.subquery(UUID.class);
        var catProduct = categorySub.from(Product.class);
        Join<Product, ProductCategory> categoryJoin =
                catProduct.join("categories", JoinType.INNER);
        categorySub.select(catProduct.get("farmer").get("id"))
                .where(cb.like(cb.lower(categoryJoin.get("name")), term));

        predicates.add(
                cb.or(
                        cb.like(cb.lower(root.get("farmName")), term),
                        cb.like(cb.lower(userJoin.get("name")), term),
                        root.get("id").in(productSub),
                        root.get("id").in(categorySub)
                )
        );
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
package com.fooddelivery.identity.entitlement.repository;

import com.fooddelivery.identity.entitlement.entity.EntitlementPublication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

public interface EntitlementPublicationRepository extends JpaRepository<EntitlementPublication,UUID> {
    @Query("select p from EntitlementPublication p order by p.userId")
    List<EntitlementPublication> pending(Pageable page);
    @Transactional
    @Modifying @Query("delete from EntitlementPublication p where p.userId=:id and p.entitlementsVersion=:version")
    int acknowledge(@Param("id") UUID userId,@Param("version") long version);
}

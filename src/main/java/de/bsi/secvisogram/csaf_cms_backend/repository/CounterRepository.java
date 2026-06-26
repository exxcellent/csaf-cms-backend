package de.bsi.secvisogram.csaf_cms_backend.repository;

import de.bsi.secvisogram.csaf_cms_backend.entity.CounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for sequential counters.
 * Provides atomic increment to replace the racy read-modify-write CouchDB pattern.
 */
public interface CounterRepository extends JpaRepository<CounterEntity, String> {

    /**
     * Atomically increment the counter and return the new value.
     * This eliminates the race condition present in the CouchDB implementation.
     */
    @Modifying
    @Query(value = "UPDATE counters SET count = count + 1 WHERE id = :id RETURNING count",
            nativeQuery = true)
    Long incrementAndGet(@Param("id") String id);
}

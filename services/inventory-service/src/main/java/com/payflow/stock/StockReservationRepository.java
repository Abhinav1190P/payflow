package com.payflow.stock;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, StockReservation.Key> {

    @Query("select count(r) > 0 from StockReservation r where r.id.orderId = :orderId")
    boolean existsForOrder(@Param("orderId") UUID orderId);
}

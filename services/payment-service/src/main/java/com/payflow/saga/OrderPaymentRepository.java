package com.payflow.saga;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID> {
}

package com.payflow.fraud;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
}

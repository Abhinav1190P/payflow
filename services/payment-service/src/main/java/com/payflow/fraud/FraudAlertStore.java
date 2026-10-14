package com.payflow.fraud;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FraudAlertStore {

    private final FraudAlertRepository alerts;

    public FraudAlertStore(FraudAlertRepository alerts) {
        this.alerts = alerts;
    }

    // Concept: propagation REQUIRES_NEW. The alert is written after the transfer already committed, so it
    // needs its own fresh transaction; it must never join (or roll back with) any other one.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FraudAlert save(FraudAlert alert) {
        return alerts.save(alert);
    }
}

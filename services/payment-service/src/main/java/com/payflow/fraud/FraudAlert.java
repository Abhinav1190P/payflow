package com.payflow.fraud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "wallet_path", nullable = false)
    private String walletPath;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected FraudAlert() {
    }

    public FraudAlert(UUID transactionId, String walletPath, Instant detectedAt) {
        this.transactionId = transactionId;
        this.walletPath = walletPath;
        this.detectedAt = detectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getWalletPath() {
        return walletPath;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}

package com.rawend.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_payment_reservation", 
           columnNames = {"reservation_id"}))
@Getter
@Setter
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flouci_payment_id", unique = true)
    private String flouciPaymentId;

    @Column(name = "payment_link")
    private String paymentLink;

    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "developer_tracking_id")
    private String developerTrackingId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToOne
    @JoinColumn(name = "reservation_id")
    private ReservationEntity reservation;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PaymentStatus.NON_PAYE;
        }
    }
}
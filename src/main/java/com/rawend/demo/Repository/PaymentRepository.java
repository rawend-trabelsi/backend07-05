package com.rawend.demo.Repository;

import com.rawend.demo.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByFlouciPaymentId(String flouciPaymentId);
    Optional<PaymentEntity> findTopByReservationIdOrderByCreatedAtDesc(Long reservationId);
    @Query("SELECT p FROM PaymentEntity p JOIN FETCH p.reservation WHERE p.reservation.id = :reservationId")
    Optional<PaymentEntity> findByReservationIdWithReservation(@Param("reservationId") Long reservationId);

    // Méthode 3: Pour vérifier l'existence
    boolean existsByReservationId(Long reservationId);

    // Méthode 4: Trouver le dernier paiement pour une réservation
    @Query("SELECT p FROM PaymentEntity p WHERE p.reservation.id = :reservationId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PaymentEntity> findLatestByReservationId(@Param("reservationId") Long reservationId);
    Optional<PaymentEntity> findByReservationId(Long reservationId);
    List<PaymentEntity> findAllByReservationId(Long reservationId);

    @Query("SELECT p FROM PaymentEntity p WHERE p.reservation.id = :reservationId ORDER BY p.id DESC LIMIT 1")
    PaymentEntity findTopByReservationIdOrderByIdDesc(@Param("reservationId") Long reservationId);
}
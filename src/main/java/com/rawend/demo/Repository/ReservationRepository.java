package com.rawend.demo.Repository;

import com.rawend.demo.entity.ReservationEntity;
import com.rawend.demo.entity.ReservationStatus;
import com.rawend.demo.entity.ServiceEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {
	List<ReservationEntity> findByTechnicienId(Long technicienId);
	@Query("SELECT r FROM ReservationEntity r WHERE r.user.email = :email ORDER BY r.dateCreation DESC")
	List<ReservationEntity> findByUserEmailOrderByDateCreationDesc(@Param("email") String email);
	// Dans ReservationRepository.java
	Optional<ReservationEntity> findByIdAndUserEmail(Long id, String email);
	List<ReservationEntity> findByTechnicienIdAndStatusNot(Long technicienId, ReservationStatus status);
	  @Query("SELECT r FROM ReservationEntity r " +
	           "WHERE (r.dateReservation < :end AND r.dateFinReelle > :start) " +
	           "AND r.id <> :excludeId")
	    List<ReservationEntity> findConflictingReservations(
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        @Param("excludeId") Long excludeId
	    );
	  List<ReservationEntity> findByEmailAndServiceIdAndStatus(
		        String email, 
		        Long serviceId, 
		        ReservationStatus status
		    );
	  List<ReservationEntity> findByService(ServiceEntity service);

	  @Query("SELECT COUNT(r) FROM ReservationEntity r WHERE r.technicienId = :technicienId AND r.status <> 'TERMINEE'")
	  long countEnCoursByTechnicienId(@Param("technicienId") Long technicienId);

	  @Query("SELECT COUNT(r) FROM ReservationEntity r WHERE r.technicienId = :technicienId AND r.status = 'TERMINEE'")
	  long countTermineesByTechnicienId(@Param("technicienId") Long technicienId);
	  
	}

	





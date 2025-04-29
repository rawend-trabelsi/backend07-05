package com.rawend.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservation")
@Getter
@Setter
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;  

    private Double prix; 

    @Enumerated(EnumType.STRING)
    private ModePaiement modePaiement;  

    private String localisation;  
    
    private LocalDateTime dateReservation;  
    private LocalDateTime dateCreation;  

    private String titreService;  
    private LocalDateTime dateFinReelle;
    private String email;  
    private String phone;
    private String duree;
    
    @Column(name = "technicien_id", nullable = true) 
    private Long technicienId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'EN_ATTENTE'")
    private ReservationStatus status = ReservationStatus.EN_ATTENTE;
    @Column(name = "latitude")
    private Double latitude;      // Nouveau champ pour la latitude
    
    @Column(name = "longitude")
    private Double longitude;     // Nouveau champ pour la longitude
    @OneToMany(
            mappedBy = "reservation", 
            cascade = CascadeType.ALL, 
            orphanRemoval = true
        )
        private List<AffectationTechnicien> affectations = new ArrayList<>();
    @PrePersist
    @PreUpdate
    public void initStatus() {
        if (this.status == null) {
            this.status = ReservationStatus.EN_ATTENTE;
        }
    
        if (this.service != null) {
            this.titreService = this.service.getTitre(); 
        }
        
        if (this.user != null) {
            this.email = this.user.getEmail();
            this.phone = this.user.getPhone();
        }
    }

}
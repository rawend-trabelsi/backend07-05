package com.rawend.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "technicien_emplois")
@Getter
@Setter
public class TechnicienEmploi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourRepos jourRepos;

    @Column(nullable = false)
    private LocalTime heureDebut = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime heureFin = LocalTime.of(22, 0);

    @OneToMany(mappedBy = "technicien", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AffectationTechnicien> affectations;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String username;

    @Column(nullable = true)
    private String phone;

    @Column(nullable = true)
    private Double latitude;

    @Column(nullable = true)
    private Double longitude;

    @Column(nullable = true)
    private String locationName;

    @Column(nullable = true)
    private LocalDateTime lastLocationUpdate;

    @Column(nullable = false)
    private Boolean locationTrackingEnabled = false;
}
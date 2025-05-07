package com.rawend.demo.Controller;

import com.rawend.demo.Repository.ReservationRepository;
import com.rawend.demo.Repository.TechnicienEmploiRepository;
import com.rawend.demo.dto.ReservationUpdateRequest;
import com.rawend.demo.Repository.AffectationTechnicienRepository;
import com.rawend.demo.Repository.PaymentRepository;
import com.rawend.demo.dto.ReservationRequest;

import com.rawend.demo.entity.AffectationTechnicien;
import com.rawend.demo.entity.JourRepos;
import com.rawend.demo.entity.ModePaiement;
import com.rawend.demo.entity.Notification;
import com.rawend.demo.entity.PaymentEntity;
import com.rawend.demo.entity.PaymentStatus;
import com.rawend.demo.entity.ReservationEntity;
import com.rawend.demo.entity.ReservationStatus;
import com.rawend.demo.entity.TechnicienEmploi;
import com.rawend.demo.services.NotificationService;
import com.rawend.demo.services.ReservationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;
    private final AffectationTechnicienRepository affectationTechnicienRepository;
    private final TechnicienEmploiRepository technicienEmploiRepository;
    @Autowired
    private ReservationRepository reservationRepository;



    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
   
    @Autowired
    public ReservationController(AffectationTechnicienRepository affectationTechnicienRepository,TechnicienEmploiRepository technicienEmploiRepository) {
        this.affectationTechnicienRepository = affectationTechnicienRepository;
        this.technicienEmploiRepository = technicienEmploiRepository;
    }
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PaymentRepository paymentRepository;


    @PreAuthorize("hasRole('TECHNICIEN')")
    @PutMapping("/{reservationId}/terminer")
    public ResponseEntity<Map<String, String>> terminerReservation(
            @PathVariable Long reservationId,
            Authentication authentication) {
        
        reservationService.marquerReservationTerminee(reservationId, authentication);
        
        return ResponseEntity.ok(Collections.singletonMap(
            "message", 
            "La réservation a été marquée comme terminée avec succès"
        ));
    }
   
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{reservationId}/annuler")
    public ResponseEntity<?> annulerReservation(
            @PathVariable Long reservationId,
            Authentication authentication) {
        return reservationService.annulerReservation(reservationId, authentication);
    }
    @PostMapping("/add")
    public Map<String, Object> addReservation(@RequestBody ReservationRequest request, Authentication authentication) {
        Map<String, Object> response = reservationService.createReservation(request, authentication);

        List<String> adminEmails = entityManager.createNativeQuery(
        	    "SELECT email FROM users WHERE role = 'ADMIN'")
        	    .getResultList();
        LocalDateTime dateDebut = (LocalDateTime) response.get("dateReservation");
        String dureeStr = (String) response.get("duree");

        // --- Calcul de la date de fin ---
        long dureeHeures = 0;
        long dureeMinutes = 0;

        if (dureeStr.contains("h")) {
            dureeHeures = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les heures
        } else if (dureeStr.contains("min")) {
            dureeMinutes = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les minutes
        }

        LocalDateTime dateFin = dateDebut.plusHours(dureeHeures).plusMinutes(dureeMinutes);

        // --- Envoi de la notification aux administrateurs ---
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String dateDebutStr = dateDebut.format(formatter);
        String dateFinStr = dateFin.format(formatter);

        // --- Création du message de notification ---
        String notificationMessage = "Vous avez une nouvelle réservation: " + response.get("reservationId") + 
                " pour le service: " + response.get("serviceTitre")  +
                                     " prévue du " + dateDebutStr +
                                     " au " + dateFinStr + ".";

        notificationService.sendNotificationToAdmins(notificationMessage);

        return response;
    }

    private LocalDateTime calculerDateFin(LocalDateTime dateDebut, String duree) {
        // Vérification des paramètres
        if (dateDebut == null || duree == null || duree.isEmpty()) {
            return dateDebut;
        }

        // Extraction des heures et minutes
        int heures = 0;
        int minutes = 0;

        // Utilisation d'une expression régulière pour parser la durée
        Pattern pattern = Pattern.compile("(\\d+)h\\s*(\\d+)min|(\\d+)h|(\\d+)min");
        Matcher matcher = pattern.matcher(duree.toLowerCase());

        if (matcher.find()) {
            if (matcher.group(1) != null && matcher.group(2) != null) {
                // Format "Xh Ymin"
                heures = Integer.parseInt(matcher.group(1));
                minutes = Integer.parseInt(matcher.group(2));
            } else if (matcher.group(3) != null) {
                // Format "Xh"
                heures = Integer.parseInt(matcher.group(3));
            } else if (matcher.group(4) != null) {
                // Format "Ymin"
                minutes = Integer.parseInt(matcher.group(4));
            }
        }

        return dateDebut.plusHours(heures).plusMinutes(minutes);
    }
  

    @GetMapping("/dates-indisponibles")
    public ResponseEntity<List<Map<String, LocalDateTime>>> getCreneauxCompletementOccupees() {
        // 1. Get all technicians and reservations
        List<TechnicienEmploi> techniciens = technicienEmploiRepository.findAll();
        List<ReservationEntity> reservations = reservationRepository.findAll();

        // 2. Count reservations per time slot
        Map<LocalDateTime, Integer> reservationsParCreneau = new HashMap<>();
        for (ReservationEntity res : reservations) {
            LocalDateTime fin = calculerDateFin(res.getDateReservation(), res.getDuree());
            LocalDateTime creneau = res.getDateReservation();
            
            while (creneau.isBefore(fin)) {
                reservationsParCreneau.merge(creneau, 1, Integer::sum);
                creneau = creneau.plusMinutes(30);
            }
        }

        // 3. Identify fully booked slots
        List<Map<String, LocalDateTime>> creneauxSatures = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Integer> entry : reservationsParCreneau.entrySet()) {
            LocalDateTime creneau = entry.getKey();
            int nbReservations = entry.getValue();
            int techniciensDisponibles = calculerTechniciensDisponibles(techniciens, creneau);
            
            if (techniciensDisponibles > 0 && nbReservations >= techniciensDisponibles) {
                Map<String, LocalDateTime> slot = new HashMap<>();
                slot.put("dateDebut", creneau);
                slot.put("dateFin", creneau.plusMinutes(30));
                creneauxSatures.add(slot);
            }
        }

        // 4. Merge adjacent slots
        List<Map<String, LocalDateTime>> resultats = fusionnerCreneaux(creneauxSatures);
        return ResponseEntity.ok(resultats);
    }

      
    private int calculerTechniciensDisponibles(List<TechnicienEmploi> techniciens, LocalDateTime creneau) {
        int disponibles = 0;
        
        for (TechnicienEmploi tech : techniciens) {
            // Vérifier jour de repos
            if (tech.getJourRepos() != null && 
                tech.getJourRepos().equals(convertDayOfWeekToJourRepos(creneau.getDayOfWeek()))) {
                continue;
            }
            
            // Vérifier plage horaire
            if (tech.getHeureDebut() != null && tech.getHeureFin() != null) {
                LocalTime heure = creneau.toLocalTime();
                if (heure.isBefore(tech.getHeureDebut()) || heure.isAfter(tech.getHeureFin())) {
                    continue;
                }
            }
            
            disponibles++;
        }
        
        return disponibles;
    }

   
    private List<Map<String, LocalDateTime>> fusionnerCreneaux(List<Map<String, LocalDateTime>> creneaux) {
        if (creneaux.isEmpty()) return creneaux;
        
        // Trier par date de début
        creneaux.sort(Comparator.comparing(c -> c.get("dateDebut")));
        
        List<Map<String, LocalDateTime>> fusionnes = new ArrayList<>();
        Map<String, LocalDateTime> current = creneaux.get(0);
        
        for (int i = 1; i < creneaux.size(); i++) {
            Map<String, LocalDateTime> next = creneaux.get(i);
            
            if (current.get("dateFin").equals(next.get("dateDebut"))) {
                // Fusionner les créneaux adjacents
                current.put("dateFin", next.get("dateFin"));
            } else {
                fusionnes.add(current);
                current = next;
            }
        }
        fusionnes.add(current);
        
        return fusionnes;
    }


    // Méthode utilitaire pour convertir DayOfWeek en JourRepos
    private JourRepos convertDayOfWeekToJourRepos(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return JourRepos.LUNDI;
            case TUESDAY: return JourRepos.MARDI;
            case WEDNESDAY: return JourRepos.MERCREDI;
            case THURSDAY: return JourRepos.JEUDI;
            case FRIDAY: return JourRepos.VENDREDI;
            case SATURDAY: return JourRepos.SAMEDI;
            case SUNDAY: return JourRepos.DIMANCHE;
            default: return null;
        }
    }

    private Map<String, LocalDateTime> creerPeriode(LocalDateTime debut, LocalDateTime fin) {
        Map<String, LocalDateTime> periode = new HashMap<>();
        periode.put("dateDebut", debut);
        periode.put("dateFin", fin);
        return periode;
    }

   
   
  
    @PutMapping("/{reservationId}/modifier-affectation/{emailTechnicien}")
    public ResponseEntity<String> updateTechnicienReservation(
            @PathVariable Long reservationId,
            @PathVariable String emailTechnicien) {

        Optional<ReservationEntity> reservationOpt = reservationRepository.findById(reservationId);
        Optional<TechnicienEmploi> newTechnicienOpt = technicienEmploiRepository.findByEmail(emailTechnicien);

        if (reservationOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Réservation non trouvée !");
        }

        if (newTechnicienOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Nouveau technicien non trouvé !");
        }

        ReservationEntity reservation = reservationOpt.get();
        TechnicienEmploi newTechnicien = newTechnicienOpt.get();

        // Récupération de l'ancien technicien
        Long oldTechnicienId = reservation.getTechnicienId();
        Optional<TechnicienEmploi> oldTechnicienOpt = (oldTechnicienId != null) 
                ? technicienEmploiRepository.findById(oldTechnicienId) 
                : Optional.empty();

        // Mise à jour du technicien affecté à la réservation
        reservationService.modifierAffectationTechnicien(reservationId, emailTechnicien);

        // Formatage des dates pour la notification
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String dateDebut = reservation.getDateReservation().format(formatter);
   

        // Vérifier si la durée extraite est vide (au cas où)
        String dureeStr = reservation.getDuree().replaceAll("[^0-9hmin]", ""); 

        long dureeHeures = 0;
        long dureeMinutes = 0;

        // Vérification si la durée contient "h" (heures) ou "min" (minutes)
        if (dureeStr.contains("h")) {
            dureeHeures = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les heures
        } else if (dureeStr.contains("min")) {
            dureeMinutes = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les minutes
        }

        // Calcul de la date de fin
        String dateFin = reservation.getDateReservation()
                .plusHours(dureeHeures)
                .plusMinutes(dureeMinutes)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));


        String newTechNotificationMessage = "vous avez une nouvelle réservation "  +
                " prévue du " + dateDebut + " au " + dateFin + ".";

        Notification newTechNotification = notificationService.createNotification(emailTechnicien, newTechNotificationMessage);
        messagingTemplate.convertAndSend("/topic/notifications/" + emailTechnicien, newTechNotification);

        
        if (oldTechnicienOpt.isPresent()) {
            String oldTechnicienEmail = oldTechnicienOpt.get().getEmail();
            String oldTechNotificationMessage = "Votre affectation à la réservation " +
                    " prévue du " + dateDebut + " au " + dateFin + " est annulée.";

            Notification oldTechNotification = notificationService.createNotification(oldTechnicienEmail, oldTechNotificationMessage);
            messagingTemplate.convertAndSend("/topic/notifications/" + oldTechnicienEmail, oldTechNotification);
        }

        return ResponseEntity.ok("Technicien modifié avec succès !");
    }


    @PutMapping("/{reservationId}/affecter-technicien-par-email/{emailTechnicien}")
    public ResponseEntity<String> affecterTechnicienParEmail(
            @PathVariable Long reservationId,
            @PathVariable String emailTechnicien) {

        Optional<TechnicienEmploi> technicienOpt = technicienEmploiRepository.findByEmail(emailTechnicien);
        Optional<ReservationEntity> reservationOpt = reservationRepository.findById(reservationId);

        if (technicienOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Technicien non trouvé !");
        }

        if (reservationOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Réservation non trouvée !");
        }

        TechnicienEmploi technicien = technicienOpt.get();
        ReservationEntity reservation = reservationOpt.get();

        // Affectation du technicien à la réservation
        reservationService.affecterTechnicienAReservation(reservationId, technicien.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String dateDebut = reservation.getDateReservation().format(formatter);

     // Extraction des nombres et des unités (h pour heures, min pour minutes)
        String dureeStr = reservation.getDuree().replaceAll("[^0-9hmin]", ""); 

        long dureeHeures = 0;
        long dureeMinutes = 0;

        // Vérification si la durée contient "h" (heures) ou "min" (minutes)
        if (dureeStr.contains("h")) {
            dureeHeures = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les heures
        } else if (dureeStr.contains("min")) {
            dureeMinutes = Long.parseLong(dureeStr.replaceAll("[^0-9]", "")); // Extraire les minutes
        }

        // Calcul de la date de fin
        String dateFin = reservation.getDateReservation()
                .plusHours(dureeHeures)
                .plusMinutes(dureeMinutes)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        // Message de notification avec date de début et de fin
        String notificationMessage = "Vous avez été affecté à la réservation "  +
                "planifiée de " + reservation.getDateReservation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                " à " + dateFin + ".";


        // Enregistrement de la notification en base de données
        Notification notification = notificationService.createNotification(emailTechnicien, notificationMessage);

        // Envoi de la notification en temps réel via WebSocket
        messagingTemplate.convertAndSend("/topic/notifications/" + emailTechnicien, notification);

        return ResponseEntity.ok("Technicien affecté avec succès !");
    }
    @GetMapping("/affectations")
    public Map<Long, Map<String, Object>> getAllAffectations() {
        List<AffectationTechnicien> affectations = affectationTechnicienRepository.findAll();

        // ✅ Tri décroissant par ID
        affectations.sort((a1, a2) -> Long.compare(a2.getId(), a1.getId()));

        // ✅ Utiliser LinkedHashMap pour garder l’ordre
        Map<Long, Map<String, Object>> affectationsMap = new LinkedHashMap<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (AffectationTechnicien affectation : affectations) {
            Map<String, Object> affectationDetails = new HashMap<>();

            String dateDebut = affectation.getDateDebut().format(formatter);
            String dateFin = affectation.getDateFin().format(formatter);

            affectationDetails.put("dateDebutReservation", dateDebut);
            affectationDetails.put("dateFinReservation", dateFin);
            affectationDetails.put("Email Technicien", affectation.getUsername());

            affectationsMap.put(affectation.getId(), affectationDetails);
        }

        return affectationsMap;
    }



  
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countReservations() {
        long count = reservationRepository.count(); 
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/dates")
    public ResponseEntity<List<Map<String, Object>>> getAllReservationsWithDates() {
        return ResponseEntity.ok(reservationService.getAllReservationsWithDates());
    }

    @GetMapping("/indisponibles")
    public ResponseEntity<List<LocalDateTime>> getDatesIndisponibles() {
        return ResponseEntity.ok(reservationService.getDatesIndisponibles());
    }
    @GetMapping("/creneaux-indisponibles")
    public ResponseEntity<List<Map<String, LocalDateTime>>> getCreneauxIndisponibles() {
        return ResponseEntity.ok(reservationService.getCreneauxIndisponiblesComplets());
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/client/historique")
    public ResponseEntity<List<Map<String, Object>>> getHistoriqueClient(Authentication authentication) {
        String email = authentication.getName();
        List<ReservationEntity> reservations = reservationService.getReservationsByClientEmail(email);
        reservations.sort(Comparator.comparing(ReservationEntity::getId).reversed());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Map<String, Object>> response = reservations.stream()
            .filter(reservation -> reservation.getStatus() == ReservationStatus.TERMINEE) // ✅ Filtrage par enum
            .map(reservation -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", reservation.getId());
                map.put("titreService", reservation.getTitreService());
                map.put("dateReservation", reservation.getDateReservation().format(formatter));
              

                double prix = reservation.getPrix();
                double prixArrondi = BigDecimal.valueOf(prix)
                        .setScale(3, RoundingMode.HALF_UP)
                        .doubleValue();
                map.put("prix", prixArrondi);

                return map;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private String getTechnicienName(Long technicienId) {
        if (technicienId == null) return "Non affecté";
        return technicienEmploiRepository.findById(technicienId)
            .map(TechnicienEmploi::getUsername)
            .orElse("Technicien inconnu");
    }
    


    @PreAuthorize("hasRole('USER')")
    @GetMapping("/client/{reservationId}")
    public ResponseEntity<?> getReservationDetails(
        @PathVariable Long reservationId,
        Authentication authentication) {
        
        try {
            String email = authentication.getName();
            ReservationEntity reservation = reservationService.getReservationByIdAndEmail(reservationId, email);
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            Map<String, Object> response = new HashMap<>();
            response.put("id", reservation.getId());
            response.put("titreService", reservation.getTitreService());
            response.put("dateReservation", reservation.getDateReservation().format(formatter)); // OK
           
            double prix= reservation.getPrix();
            double prixArrondi = BigDecimal.valueOf(prix)
                    .setScale(3, RoundingMode.HALF_UP)
                    .doubleValue();// Round to 3 decimal places
            response.put("prix", prixArrondi);
            response.put("duree", reservation.getDuree());
            response.put("localisation", reservation.getLocalisation());
            response.put("longitude", reservation.getLongitude());
            response.put("lattitude", reservation.getLatitude());
            response.put("modePaiement", reservation.getModePaiement().name());
            response.put("dateCreation", reservation.getDateCreation().format(formatter));
            
       
            if(reservation.getTechnicienId() != null) {
                Optional<TechnicienEmploi> technicien = technicienEmploiRepository.findById(reservation.getTechnicienId());
                if(technicien.isPresent()) {
                    Map<String, String> techDetails = new HashMap<>();
                    techDetails.put("usernameTechnicien", technicien.get().getUsername());
                    techDetails.put("emailTechnicien", technicien.get().getEmail());
                    techDetails.put("telephone", technicien.get().getPhone());
                    response.put("technicien", techDetails);
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de la récupération des détails");
        }
    }
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/client/reservations")
    public ResponseEntity<List<Map<String, Object>>> getReservationsActivesClient(Authentication authentication) {
        String email = authentication.getName();
        List<ReservationEntity> reservations = reservationService.getReservationsByClientEmail(email);
        reservations.sort(Comparator.comparing(ReservationEntity::getId).reversed());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Map<String, Object>> response = reservations.stream()
            .filter(reservation -> reservation.getStatus() != ReservationStatus.TERMINEE)
            .map(reservation -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", reservation.getId());
                map.put("titreService", reservation.getTitreService());
                map.put("dateReservation", reservation.getDateReservation().format(formatter));
                map.put("duree", reservation.getDuree());

                double prix = reservation.getPrix();
                double prixArrondi = BigDecimal.valueOf(prix)
                        .setScale(3, RoundingMode.HALF_UP)
                        .doubleValue();
                map.put("prix", prixArrondi);
                map.put("status", reservation.getStatus());
                Optional<PaymentEntity> paymentOpt = paymentRepository.findTopByReservationIdOrderByCreatedAtDesc(reservation.getId());

                String paymentStatus = paymentOpt.map(p -> p.getStatus().name()).orElse("NON_PAYE");
                map.put("paymentStatus", paymentStatus);

                return map;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @PreAuthorize("hasRole('USER')")
    @PutMapping("/client/{reservationId}/modifier")
    public ResponseEntity<?> modifierReservationClient(
        @PathVariable Long reservationId,
        @RequestBody ReservationUpdateRequest updateRequest,
        Authentication authentication
    ) {
        try {
            String email = authentication.getName();
            ReservationEntity existing = reservationService.getReservationByIdAndEmail(reservationId, email);
            
            // 🔧 Correction : passer aussi l'objet authentication à la méthode
            ReservationEntity updatedReservation = reservationService.mettreAJourReservationClient(existing, updateRequest, authentication);

            return ResponseEntity.ok(Collections.singletonMap(
                "message", 
                "Date mise à jour. " + 
                (updatedReservation.getStatus() == ReservationStatus.EN_ATTENTE 
                    ? "Réaffectation nécessaire." 
                    : "")
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }
    @GetMapping("/technicien/{emailTechnicien}/stats")
    public ResponseEntity<Map<String, Object>> getReservationStatsByTechnicien(
            @PathVariable String emailTechnicien) {

        Optional<TechnicienEmploi> technicienOpt = technicienEmploiRepository.findByEmail(emailTechnicien);

        if (technicienOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TechnicienEmploi technicien = technicienOpt.get();
        List<ReservationEntity> reservations = reservationRepository.findByTechnicienId(technicien.getId());

        // Date d’aujourd’hui
        LocalDate today = LocalDate.now();

        // Statistiques du jour en cours
        long enCoursToday = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.EN_COURS)
                .filter(r -> r.getDateReservation() != null && r.getDateReservation().toLocalDate().equals(today))
                .count();

        long termineesToday = reservations.stream()
        	    .filter(r -> r.getStatus() == ReservationStatus.TERMINEE) // Vérifie que le statut est "TERMINEE"
        	    .filter(r -> r.getDateFinReelle() != null && 
        	                r.getDateFinReelle().toLocalDate().equals(today)) // Comparer uniquement la partie date de dateFinReelle
        	    .count();


        Map<String, Object> response = new HashMap<>();
        response.put("technicienEmail", emailTechnicien);
        response.put("technicienNom", technicien.getUsername());
        response.put("enCoursAujourd'hui", enCoursToday);
        response.put("termineesAujourd'hui", termineesToday);
        response.put("totalAujourd'hui", enCoursToday + termineesToday);

        return ResponseEntity.ok(response);
    }
    @PreAuthorize("hasRole('TECHNICIEN')")
    @PutMapping("/{reservationId}/marquer-payee")
    public ResponseEntity<?> marquerReservationPayee(
            @PathVariable Long reservationId,
            Authentication authentication) {
        
        try {
            // 1. Vérification de la réservation
            ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation non trouvée"));

            // 2. Vérification des autorisations
            String currentTechnicienEmail = authentication.getName();
            if (reservation.getTechnicienId() == null || 
                !technicienEmploiRepository.findById(reservation.getTechnicienId())
                    .map(TechnicienEmploi::getEmail)
                    .filter(currentTechnicienEmail::equals)
                    .isPresent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");
            }

            // 3. Trouver ou créer le paiement avec statut PAYE explicite
            PaymentEntity payment = paymentRepository.findByReservationId(reservationId)
                .orElseGet(() -> {
                    PaymentEntity newPayment = new PaymentEntity();
                    newPayment.setReservation(reservation);
                    newPayment.setAmount(reservation.getPrix());
                    newPayment.setStatus(PaymentStatus.PAYE); // Force PAYE dès la création
                    return newPayment;
                });

            // 4. Forcer le statut PAYE (même si existant)
            payment.setStatus(PaymentStatus.PAYE);
            payment.setDeveloperTrackingId("MANUEL_" + currentTechnicienEmail + "_" + LocalDateTime.now());
            
            // 5. Sauvegarde
            PaymentEntity savedPayment = paymentRepository.save(payment);

            // 6. Mise à jour réservation si nécessaire
            if (reservation.getStatus() == ReservationStatus.EN_ATTENTE) {
                reservation.setStatus(ReservationStatus.EN_COURS);
                reservationRepository.save(reservation);
            }
            String technicienName = currentTechnicienEmail;
            Optional<TechnicienEmploi> techOpt = technicienEmploiRepository.findById(reservation.getTechnicienId());
            if (techOpt.isPresent()) {
                TechnicienEmploi tech = techOpt.get();
                technicienName = tech.getEmail();
            }
            
            // Message de notification pour les administrateurs avec mention d'affectation au technicien
            String adminMessage = String.format(
                    "La Réservation: %d du Service: %s Prévu le: %s a été marquée comme payée par le  technicien %s.",
                    reservation.getId(),
                    reservation.getTitreService(),
                    reservation.getDateReservation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    technicienName
            );
            notificationService.sendNotificationToAdmins(adminMessage);

            return ResponseEntity.ok(Map.of(
                "message", "Réservation marquée comme payée avec succès",
                "reservationId", reservationId,
                "paymentStatus", savedPayment.getStatus().toString(),
                "paymentId", savedPayment.getId()
            ));

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur lors de la mise à jour du paiement: " + e.getMessage());
        }
    } 


    @GetMapping()
    public List<Map<String, Object>> getAllReservations() {
        List<ReservationEntity> reservations = reservationService.getAllReservations();
        List<Map<String, Object>> reservationsList = new ArrayList<>();
        reservations.sort((r1, r2) -> Long.compare(r2.getId(), r1.getId()));

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME; // Format ISO 8601

        for (ReservationEntity reservation : reservations) {
            Map<String, Object> reservationDetails = new HashMap<>();

            reservationDetails.put("id", reservation.getId());
            reservationDetails.put("titreService", reservation.getTitreService());
            reservationDetails.put("prix", reservation.getPrix());
            reservationDetails.put("localisation", reservation.getLocalisation());
            reservationDetails.put("latitude", reservation.getLatitude());
            reservationDetails.put("longitude", reservation.getLongitude());
            reservationDetails.put("email", reservation.getEmail());
            reservationDetails.put("phone", reservation.getPhone());
            reservationDetails.put("duree", reservation.getDuree());
            reservationDetails.put("modePaiement", reservation.getModePaiement().name());
            reservationDetails.put("status", reservation.getStatus().name());

            // Dates au format ISO
            reservationDetails.put("dateReservation", reservation.getDateReservation().format(formatter));
            reservationDetails.put("dateCreation", reservation.getDateCreation().format(formatter));

            // Nom du client si présent
            if (reservation.getUser() != null && reservation.getUser().getUsername() != null) {
                reservationDetails.put("nomClient", reservation.getUser().getUsernameFieldDirectly());
            }

            // Informations technicien si présent
            Long technicienId = reservation.getTechnicienId();
            if (technicienId != null) {
                technicienEmploiRepository.findById(technicienId).ifPresent(technicien -> {
                    if (technicien.getUsername() != null) {
                        reservationDetails.put("usernameTechnicien", technicien.getUsername());
                    }
                    if (technicien.getEmail() != null) {
                        reservationDetails.put("emailTechnicien", technicien.getEmail());
                    }
                });
            }

            // ✅ Dernier statut de paiement (le plus récent)
            Optional<PaymentEntity> paymentOpt = paymentRepository.findTopByReservationIdOrderByCreatedAtDesc(reservation.getId());

            String paymentStatus = paymentOpt.map(p -> p.getStatus().name()).orElse("NON_PAYE");
            reservationDetails.put("paymentStatus", paymentStatus);

            reservationsList.add(reservationDetails);
        }

        return reservationsList;
    }

    @GetMapping("/technicien/{emailTechnicien}")
    public List<Map<String, Object>> getReservationsByTechnicien(@PathVariable String emailTechnicien) {
        Optional<TechnicienEmploi> technicienOpt = technicienEmploiRepository.findByEmail(emailTechnicien);

        if (technicienOpt.isEmpty()) {
            return Collections.emptyList(); 
        }

        TechnicienEmploi technicien = technicienOpt.get();
        List<ReservationEntity> reservations = reservationRepository.findByTechnicienId(technicien.getId());

        // Trier les réservations par id dans l'ordre décroissant
        reservations.sort((r1, r2) -> Long.compare(r2.getId(), r1.getId()));

        List<Map<String, Object>> reservationsList = new ArrayList<>();

        for (ReservationEntity reservation : reservations) {
            Map<String, Object> reservationDetails = new HashMap<>();

            reservationDetails.put("id", reservation.getId());
            reservationDetails.put("titreService", reservation.getTitreService());
            reservationDetails.put("prix", reservation.getPrix());
            reservationDetails.put("localisation", reservation.getLocalisation());
            reservationDetails.put("latitude", reservation.getLatitude());
            reservationDetails.put("longitude", reservation.getLongitude());
            reservationDetails.put("dateReservation", reservation.getDateReservation());
            reservationDetails.put("dateCreation", reservation.getDateCreation());
            reservationDetails.put("email", reservation.getEmail());
            if (reservation.getUser() != null && reservation.getUser().getUsername() != null) {
                reservationDetails.put("nomClient", reservation.getUser().getUsernameFieldDirectly());
            }
            reservationDetails.put("phone", reservation.getPhone());
            reservationDetails.put("duree", reservation.getDuree());
            reservationDetails.put("modePaiement", reservation.getModePaiement());
            reservationDetails.put("status", reservation.getStatus());
            // ✅ Dernier statut de paiement (le plus récent)
            Optional<PaymentEntity> paymentOpt = paymentRepository.findTopByReservationIdOrderByCreatedAtDesc(reservation.getId());

            String paymentStatus = paymentOpt.map(p -> p.getStatus().name()).orElse("NON_PAYE");
            reservationDetails.put("paymentStatus", paymentStatus);
            

            reservationsList.add(reservationDetails);
        }

        return reservationsList;
    }

}
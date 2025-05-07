package com.rawend.demo.services;


import com.rawend.demo.entity.TechnicienEmploi;
import com.rawend.demo.entity.User;
import com.rawend.demo.entity.JourRepos;
import com.rawend.demo.entity.Role;
import com.rawend.demo.Repository.UserRepository;
import com.rawend.demo.dto.EmploiRequest;
import com.rawend.demo.dto.LocationTrackingRequest;
import com.rawend.demo.dto.LocationUpdateRequest;
import com.rawend.demo.Repository.TechnicienEmploiRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmploiService {

    private final UserRepository userRepository;
    private final TechnicienEmploiRepository technicienEmploiRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, ScheduledExecutorService> trackingSessions = new ConcurrentHashMap<>();
    public EmploiService(UserRepository userRepository, TechnicienEmploiRepository technicienEmploiRepository,SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.technicienEmploiRepository = technicienEmploiRepository;
		this.messagingTemplate = messagingTemplate;
    }
    public void supprimerEmploiTechnicien(Long emploiId) {
        TechnicienEmploi emploi = technicienEmploiRepository.findById(emploiId)
                .orElseThrow(() -> new RuntimeException("Emploi non trouvé"));
        technicienEmploiRepository.delete(emploi);
    }


    public void ajouterEmploiTechnicienParEmail(EmploiRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new RuntimeException("Utilisateur avec cet email introuvable"));

        if (!user.getRole().equals(Role.TECHNICIEN)) {
            throw new RuntimeException("Cet utilisateur n'est pas un technicien !");
        }

        TechnicienEmploi emploi = new TechnicienEmploi(); // heureDebut = 08:00 et heureFin = 22:00 par défaut
        emploi.setUser(user);
        emploi.setEmail(user.getEmail());
        emploi.setPhone(user.getPhone());
        emploi.setUsername(user.getUsernameValue());
        emploi.setJourRepos(JourRepos.valueOf(request.getJourRepos().toUpperCase()));

       

        technicienEmploiRepository.save(emploi);
    }

    public List<String> getEmailsTechniciens() {
        List<User> techniciens = userRepository.findByRole(Role.TECHNICIEN);

        if (techniciens.isEmpty()) {
            throw new RuntimeException("Aucun technicien trouvé !");
        }

        return techniciens.stream().map(User::getEmail).collect(Collectors.toList());
    }
    public void updateEmploiTechnicien(Long id, EmploiRequest request) {
        TechnicienEmploi emploi = technicienEmploiRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Technicien avec cet ID introuvable"));

        if (request.getJourRepos() != null) {
            emploi.setJourRepos(JourRepos.valueOf(request.getJourRepos().toUpperCase()));
        }
        if (request.getHeureDebut() != null) {
            emploi.setHeureDebut(request.getHeureDebut());
        }
        if (request.getHeureFin() != null) {
            emploi.setHeureFin(request.getHeureFin());
        }

        technicienEmploiRepository.save(emploi);
    }
    public List<Map<String, Object>> getAllTechniciensAsMap() {
        List<TechnicienEmploi> techniciens = technicienEmploiRepository.findAll();

        // Mettre à jour les techniciens si nécessaire
        techniciens.forEach(technicien -> {
            User user = technicien.getUser();
            
            // Vérifiez si les informations de l'utilisateur ont changé
            if (user != null) {
                // Comparer et mettre à jour avec la méthode getUsernameFieldDirectly()
                if (!user.getUsernameFieldDirectly().equals(technicien.getUsername())) {
                    technicien.setUsername(user.getUsernameFieldDirectly());
                }
                if (!user.getEmail().equals(technicien.getEmail())) {
                    technicien.setEmail(user.getEmail());
                }
                if (!user.getPhone().equals(technicien.getPhone())) {
                    technicien.setPhone(user.getPhone());
                }

                // Sauvegarder les modifications apportées au technicien dans la base de données
                technicienEmploiRepository.save(technicien);
            }
        });

        // Convertir la liste des techniciens en Map
        return techniciens.stream()
                .map(technicien -> {
                    // Créer une nouvelle instance de HashMap pour chaque technicien
                    Map<String, Object> technicienMap = new HashMap<>();
                    
                    // Utiliser le username du User avec la méthode getUsernameFieldDirectly()
                    User user = technicien.getUser(); // Assurez-vous que user n'est pas null avant
                    if (user != null) {
                        technicienMap.put("username", user.getUsernameFieldDirectly()); // Utilisation de la méthode personnalisée
                    }
                    technicienMap.put("email", technicien.getEmail());
                    technicienMap.put("phone", technicien.getPhone());
                    technicienMap.put("heureDebut", technicien.getHeureDebut());
                    technicienMap.put("heureFin", technicien.getHeureFin());
                    technicienMap.put("id", technicien.getId());
                    technicienMap.put("jourRepos", technicien.getJourRepos());

                    return technicienMap;
                })
                .collect(Collectors.toList());  // Collecte la liste des maps
    }


    public boolean emailTechnicienExiste(String email) {
        return technicienEmploiRepository.existsByUserEmail(email);
    }
    public Map<String, Object> getTechnicienByEmail(String email) {
        TechnicienEmploi technicien = technicienEmploiRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("Technicien avec cet email introuvable"));

        // Créer un HashMap pour stocker les informations du technicien
        Map<String, Object> technicienMap = new HashMap<>();
        technicienMap.put("email", technicien.getEmail());
        technicienMap.put("phone", technicien.getPhone());
        technicienMap.put("username", technicien.getUsername());
        technicienMap.put("heureDebut", technicien.getHeureDebut());
        technicienMap.put("heureFin", technicien.getHeureFin());
        technicienMap.put("id", technicien.getId());
        technicienMap.put("jourRepos", technicien.getJourRepos());

        return technicienMap;
    }

    public void enableLocationTracking(LocationTrackingRequest request) {
        TechnicienEmploi emploi = technicienEmploiRepository.findByUserEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Technicien non trouvé"));
        
        emploi.setLocationTrackingEnabled(request.getEnable());
        technicienEmploiRepository.save(emploi);
        
        if (request.getEnable()) {
            startPeriodicLocationUpdate(request.getEmail());
        } else {
            stopPeriodicLocationUpdate(request.getEmail());
        }
    }

    private void startPeriodicLocationUpdate(String email) {
        stopPeriodicLocationUpdate(email);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        trackingSessions.put(email, scheduler);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                TechnicienEmploi emploi = technicienEmploiRepository.findByUserEmail(email)
                        .orElseThrow(() -> new RuntimeException("Technicien non trouvé"));

                Map<String, Object> location = new HashMap<>();
                location.put("email", email);
                location.put("latitude", emploi.getLatitude());
                location.put("longitude", emploi.getLongitude());
                location.put("timestamp", LocalDateTime.now().toString());

                messagingTemplate.convertAndSend("/topic/locations/" + email, location);
                messagingTemplate.convertAndSend("/topic/locations/all", location);
                
            } catch (Exception e) {
                // Envoyer l'erreur via WebSocket au lieu de logger
                Map<String, String> error = new HashMap<>();
                error.put("error", "Erreur de mise à jour pour " + email);
                error.put("message", e.getMessage());
                messagingTemplate.convertAndSend("/topic/errors/" + email, error);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void stopPeriodicLocationUpdate(String email) {
        ScheduledExecutorService scheduler = trackingSessions.remove(email);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Transactional
    public void updateTechnicienLocation(LocationUpdateRequest request) {
        TechnicienEmploi emploi = technicienEmploiRepository.findByUserEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Technicien non trouvé"));
        
        // Supprimer la vérification du changement pour tester
        emploi.setLatitude(request.getLatitude());
        emploi.setLongitude(request.getLongitude());
        emploi.setLocationName(request.getLocationName());
        emploi.setLastLocationUpdate(LocalDateTime.now());
        
        technicienEmploiRepository.saveAndFlush(emploi); // Force l'écriture
        
        // Debug
        System.out.println("DEBUG - Position mise à jour : " + 
            technicienEmploiRepository.findById(emploi.getId()));
        
        // Notifier
        messagingTemplate.convertAndSend("/topic/locations/" + request.getEmail(), 
            createLocationMap(emploi));
    }
    private boolean hasPositionChanged(TechnicienEmploi emploi, double newLat, double newLon) {
        if (emploi.getLatitude() == null || emploi.getLongitude() == null) return true;
        
        // Seuil de changement (environ 50 mètres)
        return distance(emploi.getLatitude(), emploi.getLongitude(), newLat, newLon) > 0.0005;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        // Formule haversine simplifiée pour les petites distances
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private Map<String, Object> createLocationMap(TechnicienEmploi emploi) {
        Map<String, Object> map = new HashMap<>();
        map.put("email", emploi.getEmail());
        map.put("username", emploi.getUsername());
        map.put("latitude", emploi.getLatitude());
        map.put("longitude", emploi.getLongitude());
        map.put("locationName", emploi.getLocationName());
        map.put("timestamp", LocalDateTime.now().toString());
        return map;
    }
    public List<Map<String, Object>> getAllTechniciensLocations() {
        return technicienEmploiRepository.findByLocationTrackingEnabledTrue()
                .stream()
                .map(this::createLocationMap)
                .collect(Collectors.toList());
    }
}
    


package com.rawend.demo.Controller;


import com.rawend.demo.entity.TechnicienEmploi;
import com.rawend.demo.entity.User;
import com.rawend.demo.services.EmploiService;
import com.rawend.demo.entity.Role;
import com.rawend.demo.Repository.UserRepository;
import com.rawend.demo.dto.EmploiRequest;
import com.rawend.demo.dto.LocationTrackingRequest;
import com.rawend.demo.dto.LocationUpdateRequest;
import com.rawend.demo.Repository.TechnicienEmploiRepository;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/emplois")
public class EmploiController {

	private final EmploiService emploiService;
    private final SimpMessagingTemplate messagingTemplate;

    public EmploiController(EmploiService emploiService, SimpMessagingTemplate messagingTemplate) {
        this.emploiService = emploiService;
        this.messagingTemplate = messagingTemplate;
    }
    @PostMapping("/technicien/ajout")
    public ResponseEntity<?> ajouterEmploiTechnicien(@RequestBody EmploiRequest request) {
        try {
            emploiService.ajouterEmploiTechnicienParEmail(request);
            return ResponseEntity.ok("Emploi ajouté pour le technicien !");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/techniciens/emails")
    public ResponseEntity<?> getEmailsTechniciens() {
        try {
            return ResponseEntity.ok(emploiService.getEmailsTechniciens());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PutMapping("/technicien/update/{id}")
    public ResponseEntity<?> updateEmploiTechnicien(
            @PathVariable Long id, // L'ID récupéré depuis l'URL
            @RequestBody EmploiRequest request) { // Le corps contient les informations à mettre à jour
        try {
            emploiService.updateEmploiTechnicien(id, request); // Appel de la méthode avec l'ID et l'objet request
            return ResponseEntity.ok("Emploi du technicien mis à jour !");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/techniciens")
    public ResponseEntity<?> getAllTechniciens() {
        try {
            // Appel de la méthode qui récupère tous les techniciens sous forme de Map
            return ResponseEntity.ok(emploiService.getAllTechniciensAsMap());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/techniciens/{email}")
    public ResponseEntity<?> getTechnicienByEmail(@PathVariable String email) {
        try {
            // Appel à la méthode pour récupérer le technicien par email
            return ResponseEntity.ok(emploiService.getTechnicienByEmail(email));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/technicien/supprimer/{id}")
    public ResponseEntity<?> supprimerEmploiTechnicien(@PathVariable Long id) {
        try {
            emploiService.supprimerEmploiTechnicien(id);
            return ResponseEntity.ok("Emploi du technicien supprimé avec succès !");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @GetMapping("/technicien/existe")
    public ResponseEntity<?> emailPresentDansTable(@RequestParam String email) {
        try {
            boolean existe = emploiService.emailTechnicienExiste(email);
            return ResponseEntity.ok(existe);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/technicien/tracking")
    public ResponseEntity<?> toggleLocationTracking(@RequestBody LocationTrackingRequest request) {
        try {
            emploiService.enableLocationTracking(request);
            return ResponseEntity.ok("Suivi de localisation " + (request.getEnable() ? "activé" : "désactivé"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Erreur: " + e.getMessage());
        }
    }

    @PostMapping("/technicien/update-location")
    public ResponseEntity<?> updateTechnicienLocation(@RequestBody LocationUpdateRequest request) {
        try {
            emploiService.updateTechnicienLocation(request);
            return ResponseEntity.ok("Position mise à jour avec succès");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Erreur de mise à jour: " + e.getMessage());
        }
    }

    @GetMapping("/techniciens/locations")
    public ResponseEntity<?> getAllTechniciensLocations() {
        List<Map<String, Object>> locations = emploiService.getAllTechniciensLocations();
        
        if (locations.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Aucun technicien avec suivi de localisation activé");
        }
        
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/techniciens/location/{email}")
    public ResponseEntity<?> getTechnicienLocation(@PathVariable String email) {
        try {
            Map<String, Object> location = emploiService.getTechnicienByEmail(email);
            
            if (location.get("latitude") == null || location.get("longitude") == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Localisation non disponible pour ce technicien");
            }
            
            return ResponseEntity.ok(location);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Technicien introuvable: " + e.getMessage());
        }
    }

   


}
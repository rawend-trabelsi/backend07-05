package com.rawend.demo.Controller;

import com.rawend.demo.entity.PaymentEntity;
import com.rawend.demo.entity.PaymentStatus;
import com.rawend.demo.services.PaymentService;

import io.jsonwebtoken.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    @PostMapping("/create/{reservationId}")
    public ResponseEntity<Map<String, Object>> createPayment(@PathVariable Long reservationId) {
        try {
            PaymentEntity payment = paymentService.createPaymentForReservation(reservationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("reservationId", reservationId); // Ajout de l'ID de réservation
            response.put("paymentLink", payment.getPaymentLink());
            response.put("paymentId", payment.getFlouciPaymentId());
            response.put("amount", payment.getAmount());
            response.put("status", payment.getStatus().toString());
            response.put("createdAt", payment.getCreatedAt().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("reservationId", reservationId); // Même en cas d'erreur
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                               .body(errorResponse);
        }
    }


    @GetMapping("/verify/{paymentId}")
    public boolean verifyPayment(@PathVariable String paymentId) {
        try {
            return paymentService.verifyPayment(paymentId);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la vérification", e);
        }
    }

    @GetMapping("/success")
    public ResponseEntity<String> paymentSuccess(
            @RequestParam(required = false) String payment_id,
            @RequestParam(required = false) String status,
            HttpServletResponse httpResponse) throws IOException, java.io.IOException {

        // Traitement métier
        if (payment_id != null) {
            PaymentEntity payment = paymentService.findByFlouciPaymentId(payment_id);
            if (payment != null) {
                payment.setStatus(PaymentStatus.PAYE);
                paymentService.save(payment);
            }
        }

        // URL d'une image de succès de paiement (exemple)
        String imageUrl = "https://raw.githubusercontent.com/rawend-trabelsi/image/refs/heads/main/sucess.png";
        
        // Redirection vers l'image
        httpResponse.sendRedirect(imageUrl);
        return null;
    }
    @GetMapping("/fail")
    public ResponseEntity<?> paymentFailed(
            @RequestParam(required = false) String payment_id,
            @RequestParam(required = false) String status) {

        if (payment_id != null) {
            PaymentEntity payment = paymentService.findByFlouciPaymentId(payment_id);
            if (payment != null) {
                payment.setStatus(PaymentStatus.NON_PAYE);
                paymentService.save(payment);

                // Recréer un nouveau paiement lié à la même réservation
                Long reservationId = payment.getReservation().getId(); // suppose que chaque payment a une réservation liée
                PaymentEntity newPayment = paymentService.createPaymentForReservation(reservationId);

                // Rediriger automatiquement vers la nouvelle interface Flouci
                URI redirectUri = URI.create(newPayment.getPaymentLink());
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(redirectUri)
                        .build();
            }
        }

        return ResponseEntity.ok("Échec de paiement. Impossible de relancer le paiement automatiquement.");
    }

    @PostMapping("/retry/{reservationId}")
    public ResponseEntity<?> retryPayment(@PathVariable Long reservationId) {
        try {
            PaymentEntity lastPayment = paymentService.findLastPaymentByReservationId(reservationId);

            if (lastPayment == null || lastPayment.getStatus() == PaymentStatus.NON_PAYE) {
                PaymentEntity newPayment = paymentService.createPaymentForReservation(reservationId);

                Map<String, Object> response = new HashMap<>();
                response.put("reservationId", reservationId);
                response.put("paymentLink", newPayment.getPaymentLink());
                response.put("paymentId", newPayment.getFlouciPaymentId());
                response.put("amount", newPayment.getAmount());
                response.put("status", newPayment.getStatus().toString());
                response.put("createdAt", newPayment.getCreatedAt().toString());

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Le paiement est déjà effectué ou en cours, pas besoin de le refaire.");
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recréation du paiement : " + e.getMessage());
        }
    }

}

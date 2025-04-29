package com.rawend.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rawend.demo.Repository.PaymentRepository;
import com.rawend.demo.Repository.ReservationRepository;
import com.rawend.demo.entity.PaymentEntity;
import com.rawend.demo.entity.PaymentStatus;
import com.rawend.demo.entity.ReservationEntity;
import com.rawend.demo.exceptions.PaymentException;

import jakarta.persistence.EntityNotFoundException;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;

@Service
public class PaymentService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    @Value("${flouci.app_token}")
    private String appToken;

    @Value("${flouci.app_secret}")
    private String appSecret;

    @Value("${flouci.developer_tracking_id}")
    private String developerTrackingId;

    public PaymentService(PaymentRepository paymentRepository, 
                        ReservationRepository reservationRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
    }

    public PaymentEntity createPaymentForReservation(Long reservationId) throws PaymentException {
        try {
            ReservationEntity reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new PaymentException("Réservation non trouvée"));

          
            int amountInCents = (int) (reservation.getPrix() * 1000);

           
            HashMap<String, Object> requestBody = new HashMap<>();
            requestBody.put("app_token", appToken);
            requestBody.put("app_secret", appSecret);
            requestBody.put("accept_card", "true");
            requestBody.put("amount", String.valueOf(amountInCents));
            requestBody.put("success_link", "http://10.0.2.2:8085/api/payments/success");
            requestBody.put("fail_link", "http://10.0.2.2:8085/api/payments/fail");
            requestBody.put("session_timeout_secs", 1200);
            requestBody.put("developer_tracking_id", developerTrackingId);

            // Créer la requête HTTP
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                JSON
            );

            Request request = new Request.Builder()
                .url("https://developers.flouci.com/api/generate_payment")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

            // Exécuter la requête
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new PaymentException("Erreur Flouci - Code: " + response.code());
                }

                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                
                
                PaymentEntity payment = new PaymentEntity();
                payment.setFlouciPaymentId(jsonNode.path("result").path("payment_id").asText());
                payment.setPaymentLink(jsonNode.path("result").path("link").asText());
                payment.setAmount(reservation.getPrix());
                payment.setDeveloperTrackingId(developerTrackingId);
                payment.setReservation(reservation);
                
                return paymentRepository.save(payment);
            }
        } catch (IOException e) {
            throw new PaymentException("Erreur de communication avec Flouci", e);
        }
    }


    public boolean verifyPayment(String paymentId) throws PaymentException {
        try {
            Request request = new Request.Builder()
                .url("https://developers.flouci.com/api/verify_payment/" + paymentId)
                .addHeader("apppublic", appToken)
                .addHeader("appsecret", appSecret)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new PaymentException("Erreur de vérification - Code: " + response.code());
                }

                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                String statusFromFlouci = jsonNode.path("result").path("status").asText();

                Optional<PaymentEntity> paymentOpt = paymentRepository.findByFlouciPaymentId(paymentId);
                if (paymentOpt.isPresent()) {
                    PaymentEntity payment = paymentOpt.get();

                    // 💡 Mapping explicite
                    if ("SUCCESS".equalsIgnoreCase(statusFromFlouci)) {
                        payment.setStatus(PaymentStatus.PAYE);
                    } else {
                        payment.setStatus(PaymentStatus.NON_PAYE);
                    }

                    paymentRepository.save(payment);
                }

                return "SUCCESS".equalsIgnoreCase(statusFromFlouci);
            }
        } catch (IOException e) {
            throw new PaymentException("Erreur de vérification", e);
        }
    }

    public PaymentEntity findByFlouciPaymentId(String paymentId) {
        return paymentRepository.findByFlouciPaymentId(paymentId).orElse(null);
    }

    public void save(PaymentEntity payment) {
        paymentRepository.save(payment);
    }
    public PaymentEntity findLastPaymentByReservationId(Long reservationId) {
        return paymentRepository.findTopByReservationIdOrderByCreatedAtDesc(reservationId).orElse(null);
    }
    public PaymentEntity findByReservationId(Long reservationId) {
        return paymentRepository.findByReservationIdWithReservation(reservationId)
                .orElseThrow(() -> new EntityNotFoundException(
                    String.format("Aucun paiement trouvé pour la réservation ID %d", reservationId)
                ));
    }

    // Version optimisée pour seulement vérifier l'état
    public boolean hasSuccessfulPayment(Long reservationId) {
        return paymentRepository.findLatestByReservationId(reservationId)
                .map(p -> p.getStatus() == PaymentStatus.PAYE)
                .orElse(false);
    }
}
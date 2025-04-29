package com.rawend.demo.Controller;

import com.rawend.demo.entity.PaymentEntity;
import com.rawend.demo.entity.PaymentStatus;
import com.rawend.demo.entity.ReservationEntity;
import com.rawend.demo.services.PaymentService;
import com.rawend.demo.services.ReservationService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final PaymentService paymentService;
    private final ReservationService reservationService;

    public ReceiptController(PaymentService paymentService, ReservationService reservationService) {
        this.paymentService = paymentService;
        this.reservationService = reservationService;
    }

    @GetMapping("/generate/{reservationId}")
    public ResponseEntity<byte[]> generateReceipt(@PathVariable Long reservationId) throws IOException, DocumentException {
        ReservationEntity reservation = reservationService.findById(reservationId);
        
        // Verify payment exists and is paid
        PaymentEntity payment = paymentService.findByReservationId(reservationId);
        if (payment == null || payment.getStatus() != PaymentStatus.PAYE) {
            throw new IllegalStateException("Aucun paiement valide trouvé pour cette réservation");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, outputStream);
        
        document.open();
        addReceiptContent(document, reservation, payment);
        document.close();
        
        byte[] pdfBytes = outputStream.toByteArray();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("filename", "receipt_" + reservationId + ".pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    private void addReceiptContent(Document document, ReservationEntity reservation, PaymentEntity payment) 
            throws DocumentException, IOException {
        
       
    	Image logo = Image.getInstance(new URL("https://raw.githubusercontent.com/rawend-trabelsi/image/refs/heads/main/482829995_122182583936294963_8953054004184709794_n.jpg"));
        logo.scaleToFit(150, 150); // Adjust size as needed
        logo.setAlignment(Element.ALIGN_CENTER);
        document.add(logo);
        
        // Add spacing after logo
        document.add(new Paragraph("\n"));

        // Header
        Paragraph header = new Paragraph("REÇU DE PAIEMENT", new Font(Font.HELVETICA, 18, Font.BOLD));
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);

        // Company info
        Paragraph companyInfo = new Paragraph();
        companyInfo.add(new Chunk("Aghsalni Services\n", new Font(Font.HELVETICA, 12, Font.BOLD)));
        companyInfo.add(new Chunk("123 Rue des Services\nTél: +216 20 361 369\nEmail: aghsalniinfo@gmail.com"));
        companyInfo.setAlignment(Element.ALIGN_CENTER);
        companyInfo.setSpacingAfter(20);
        document.add(companyInfo);

        // Separator
        document.add(new Chunk("\n"));

        // Client info
        Paragraph clientInfo = new Paragraph("CLIENT:", new Font(Font.HELVETICA, 12, Font.BOLD));
        clientInfo.add("\nNom: " + reservation.getUser().getUsernameFieldDirectly());
        clientInfo.add("\nEmail: " + reservation.getEmail());
        clientInfo.add("\nTéléphone: " + reservation.getPhone());
        clientInfo.setSpacingAfter(15);
        document.add(clientInfo);

        // Reservation info
        Paragraph reservationInfo = new Paragraph("RÉSERVATION:", new Font(Font.HELVETICA, 12, Font.BOLD));
        reservationInfo.add("\nID: " + reservation.getId());
        reservationInfo.add("\nService: " + reservation.getTitreService());
        reservationInfo.add("\nDate: " + reservation.getDateReservation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        reservationInfo.add("\nDurée: " + reservation.getDuree());
        reservationInfo.add("\nLocalisation: " + reservation.getLocalisation());
        reservationInfo.setSpacingAfter(15);
        document.add(reservationInfo);

        // Payment info
        Paragraph paymentInfo = new Paragraph("PAIEMENT:", new Font(Font.HELVETICA, 12, Font.BOLD));
        paymentInfo.add("\nID Paiement: " + payment.getFlouciPaymentId());
        paymentInfo.add("\nDate Paiement: " + payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        paymentInfo.add("\nMontant: " + String.format("%.2f DT", reservation.getPrix()));
        paymentInfo.add("\nMode de paiement: " + reservation.getModePaiement());
        paymentInfo.add("\nStatut: PAYÉ");
        paymentInfo.setSpacingAfter(20);
        document.add(paymentInfo);

        // Total
        Paragraph total = new Paragraph("TOTAL: " + String.format("%.2f DT", reservation.getPrix()), 
                                      new Font(Font.HELVETICA, 14, Font.BOLD));
        total.setAlignment(Element.ALIGN_RIGHT);
        document.add(total);

        // Footer
        Paragraph footer = new Paragraph("\n\nMerci pour votre confiance!", 
                                       new Font(Font.HELVETICA, 10, Font.ITALIC));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }
}
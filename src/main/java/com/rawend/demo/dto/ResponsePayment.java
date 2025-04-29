package com.rawend.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponsePayment {
    private String link;
    private String payment_id;
    private String developer_tracking_id;
    private Boolean success;
}
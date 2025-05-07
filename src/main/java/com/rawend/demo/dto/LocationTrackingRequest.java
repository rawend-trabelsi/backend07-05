package com.rawend.demo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationTrackingRequest {
    private String email;
    private Boolean enable;
}
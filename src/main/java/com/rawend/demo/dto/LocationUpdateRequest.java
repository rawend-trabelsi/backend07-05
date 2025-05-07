package com.rawend.demo.dto;



import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationUpdateRequest {
    private String email;
    private Double latitude;
    private Double longitude;
    private String locationName; // Ajout du nom du lieu
}
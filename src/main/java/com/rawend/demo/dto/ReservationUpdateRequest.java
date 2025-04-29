package com.rawend.demo.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ReservationUpdateRequest(
		   @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		    LocalDateTime dateReservation
   
) {}

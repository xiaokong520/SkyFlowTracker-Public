package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.DeleteFlightsDto;
import com.example.skyflowtracker.dto.FlightsEndDto;
import com.example.skyflowtracker.dto.FlightsStartDto;
import com.example.skyflowtracker.dto.FlightsUpdatePathDto;
import jakarta.validation.Valid;

import java.util.Map;

public interface FlightsServiceInte {
    Map<String, Object> start(String token, FlightsStartDto flightsStartDto) throws Exception;

    String end(String token, FlightsEndDto flightsEndDto) throws Exception;

    String updatePath(String token, FlightsUpdatePathDto flightsUpdatePathDto) throws Exception;

    Map<String, Object> getFlightsList(String token, Integer page, Integer pageSize, String keyword) throws Exception;

    Map<String, Object> getFlightDetail(String token, Long flightId) throws Exception;

    String deleteFlights(String token, DeleteFlightsDto deleteFlightsDto) throws Exception;
}

package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket){
        if( (ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime())) ){
            throw new IllegalArgumentException("Out time provided is incorrect:"+ticket.getOutTime().toString());
        }

        long inTimeMillis = ticket.getInTime().getTime();
        long outTimeMillis = ticket.getOutTime().getTime();
        double duration = (outTimeMillis - inTimeMillis) / (1000.0 * 60 * 60);

        //calcul le prix du stationnement (type vehicule + durée)
        switch (ticket.getParkingSpot().getParkingType()){  //verification type vehicule
            case CAR: {
                ticket.setPrice(duration * Fare.CAR_RATE_PER_HOUR); //multiplication durée par tarif horaire voiture
                break;
            }
            case BIKE: {
                ticket.setPrice(duration * Fare.BIKE_RATE_PER_HOUR); //multiplication durée par tarif horaire moto
                break;
            }
            default: throw new IllegalArgumentException("Unkown Parking Type");
        }
    }
}
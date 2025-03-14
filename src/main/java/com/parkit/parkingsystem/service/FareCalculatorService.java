package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket, boolean discount) {
        if ((ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime()))) {
            throw new IllegalArgumentException("Out time provided is incorrect: " + ticket.getOutTime());
        }

        long inTimeMillis = ticket.getInTime().getTime();
        long outTimeMillis = ticket.getOutTime().getTime();
        double duration = (outTimeMillis - inTimeMillis) / (1000.0 * 60 * 60); // Converti en heures

        if (duration <= 0.5) { // 30 minutes gratuites
            ticket.setPrice(0);
            return;
        }

        double rate;
        switch (ticket.getParkingSpot().getParkingType()) {
            case CAR:
                rate = Fare.CAR_RATE_PER_HOUR;
                break;
            case BIKE:
                rate = Fare.BIKE_RATE_PER_HOUR;
                break;
            default:
                throw new IllegalArgumentException("Unknown Parking Type");
        }
        double price = duration * rate;
        if (discount) { // Appliquer 5% de réduction
            price *= 0.95;
        }

        ticket.setPrice(price);
    }

    public void calculateFare(Ticket ticket) {
        calculateFare(ticket, false); // Appelle la méthode avec discount = false par défaut
    }

}
package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private static ParkingService parkingService;

    @Mock
    private static InputReaderUtil inputReaderUtil;
    @Mock
    private static ParkingSpotDAO parkingSpotDAO;
    @Mock
    private static TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() {
        try {
            lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

            ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
            Ticket ticket = new Ticket();
            ticket.setInTime(new Date(System.currentTimeMillis() - (60*60*1000)));
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber("ABCDEF");
            lenient().when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
            lenient().when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);

            lenient().when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        } catch (Exception e) {
            e.printStackTrace();
            throw  new RuntimeException("Failed to set up test mock objects");
        }
    }

    @Test
    public void processExitingVehicleTest(){
        // Arrange : simuler que le véhicule est déjà venu (plus d'un ticket)
        when(ticketDAO.getNbTicket(anyString())).thenReturn(2);

        // Act : traiter la sortie du véhicule
        parkingService.processExitingVehicle();

        // Assert : vérifier que getNbTicket a été appelé avec le numéro de plaque "ABCDEF"
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABCDEF");
        // Et vérifier que la mise à jour de la disponibilité de la place a bien été effectuée
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
    }
    @Test
    public void testProcessIncomingVehicle() {
        // Arrange
        // Simule la saisie de la sélection du type de véhicule (1 = CAR)
        when(inputReaderUtil.readSelection()).thenReturn(1);
        // Simule la récupération d'une place de parking disponible pour une voiture
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);
        // Simule l'enregistrement du ticket avec succès
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        // Simule qu'il s'agit du premier ticket pour ce véhicule (pas de réduction)
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);

        // Act
        parkingService.processIncomingVehicle();

        // Assert
        // Vérifie que la place de parking a bien été mise à jour (passage en indisponible)
        verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));
        // Vérifie que le ticket a bien été sauvegardé
        verify(ticketDAO, times(1)).saveTicket(any(Ticket.class));
        // Vérifie que le comptage du nombre de tickets a été effectué pour le véhicule "ABCDEF"
        verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
    }
    @Test
    public void processExitingVehicleTestUnableUpdate() throws Exception {
        // Arrange
        // Simuler la saisie du véhicule
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        // Simuler le retour d'un ticket pour le véhicule
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        ticket.setVehicleRegNumber("ABCDEF");
        ticket.setInTime(new Date());
        when(ticketDAO.getTicket("ABCDEF")).thenReturn(ticket);
        // Simuler un échec lors de la mise à jour du ticket
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false);

        // Act
        parkingService.processExitingVehicle();

        // Assert
        // Vérifier que la méthode updateTicket a bien été appelée
        verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));
        // Vérifier que, suite à l'échec de la mise à jour du ticket,
        // on ne remet PAS la place de parking en disponibilité
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class));
    }
    @Test
    public void testGetNextParkingNumberIfAvailable() throws Exception {
        // Arrange
        // Simule la saisie de l'utilisateur pour un véhicule de type CAR (1)
        when(inputReaderUtil.readSelection()).thenReturn(1);
        // Simule un emplacement de parking disponible (slot ID = 1)
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);

        // Act
        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        // Assert
        assertNotNull(result, "La place de parking ne devrait pas être nulle");
        assertEquals(1, result.getId(), "L'ID du parking spot devrait être 1");
        assertEquals(ParkingType.CAR, result.getParkingType(),
                "Le type de parking devrait être CAR");
        assertTrue(result.isAvailable(), "La place de parking devrait être disponible");

        // Vérification supplémentaire (optionnelle) :
        // s'assurer que la méthode getNextAvailableSlot a bien été appelée
        verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);
    }
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() throws Exception {
        // Arrange
        // Simuler que l'utilisateur choisit un véhicule de type CAR
        when(inputReaderUtil.readSelection()).thenReturn(1);
        // Simuler qu'aucune place n'est disponible (la DAO renvoie 0)
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(0);

        // Act
        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        // Assert
        // Puisque parkingNumber <= 0, la méthode lance une Exception et la catch,
        // puis renvoie null.
        assertNull(result, "Si aucun spot n'est disponible, la méthode doit retourner null");

        // Vérifier que la méthode DAO a bien été appelée une fois avec le type CAR
        verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);
    }
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() throws Exception {
        // Arrange : simuler une saisie invalide pour le type de véhicule (par exemple, 3)
        when(inputReaderUtil.readSelection()).thenReturn(3);

        // Act : appeler la méthode testée
        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        // Assert : le résultat doit être null car une IllegalArgumentException est lancée dans getVehichleType()
        assertNull(result, "La méthode doit renvoyer null si l'entrée utilisateur est invalide");

        // Vérifier que la méthode du DAO n'est jamais appelée
        verify(parkingSpotDAO, never()).getNextAvailableSlot(any(ParkingType.class));
    }
}
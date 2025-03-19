package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    public static void setUp() throws Exception{
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    public static void tearDown(){
    }

    @Test
    public void testParkingACar(){
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        // Vérifier qu'un ticket est bien enregistré en base pour le véhicule "ABCDEF"
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket, "Le ticket devrait être enregistré dans la base de données.");
        assertNotNull(ticket.getInTime(), "La date d'entrée du ticket devrait être renseignée.");
        assertNull(ticket.getOutTime(), "La date de sortie devrait être nulle pour une entrée de véhicule.");

        // Vérifier que la place de parking a bien été mise à jour dans la base
        // Ici, nous exécutons une requête SQL pour vérifier que la colonne AVAILABLE est à false pour le parking spot utilisé.
        int parkingNumber = ticket.getParkingSpot().getId();
        Connection con = null;
        try {
            con = dataBaseTestConfig.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT AVAILABLE FROM parking WHERE PARKING_NUMBER = ?");
            ps.setInt(1, parkingNumber);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                boolean available = rs.getBoolean(1);
                assertFalse(available, "La place de parking devrait être marquée comme non disponible.");
            } else {
                fail("Aucune donnée trouvée pour le parking spot numéro " + parkingNumber);
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception lors de la vérification de la disponibilité du parking: " + e.getMessage());
        } finally {
            dataBaseTestConfig.closeConnection(con);
        }
    }

    @Test
    public void testParkingLotExit(){
        // 1) Simuler l’entrée
        testParkingACar();

        // 2) Récupérer le ticket et le forcer à être entré il y a 1 heure
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        updateTicketInTime(ticket.getId(), new Date(System.currentTimeMillis() - 3600000));

        // 3) Simuler la sortie
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processExitingVehicle();

        // 4) Vérifier que le prix est bien > 0
        ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket.getOutTime(), "La date de sortie devrait être renseignée après la sortie du véhicule.");
        assertTrue(ticket.getPrice() > 0, "Le tarif généré devrait être supérieur à 0.");

        // Vérifier que la place de parking est redevenue disponible dans la base
        int parkingNumber = ticket.getParkingSpot().getId();
        Connection con = null;
        try {
            con = dataBaseTestConfig.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT AVAILABLE FROM parking WHERE PARKING_NUMBER = ?");
            ps.setInt(1, parkingNumber);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                boolean available = rs.getBoolean(1);
                assertTrue(available, "La place de parking devrait être redevenue disponible après la sortie.");
            } else {
                fail("Aucune donnée trouvée pour le parking spot numéro " + parkingNumber);
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception lors de la vérification de la disponibilité du parking: " + e.getMessage());
        } finally {
            dataBaseTestConfig.closeConnection(con);
        }
    }
    @Test
    public void testParkingLotExitRecurringUser() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Simuler l'entrée du véhicule
        parkingService.processIncomingVehicle();
        // Mettre à jour la date d'entrée du ticket pour simuler 1 heure de stationnement
        Ticket firstTicket = getLatestTicket("ABCDEF");
        updateTicketInTime(firstTicket.getId(), new Date(System.currentTimeMillis() - 3600000));
        // Simuler la sortie du véhicule
        parkingService.processExitingVehicle();
        Ticket ticketAfterFirstExit = getLatestTicket("ABCDEF");
        double fareFirstVisit = ticketAfterFirstExit.getPrice();
        // Le tarif attendu pour 1 heure sans remise est supposé être 1,5€
        assertEquals(1.5, fareFirstVisit, 0.001, "Le tarif de la première visite doit être 1,5€ sans remise.");

        // Remise de 5%
        // Simuler une nouvelle entrée pour le même véhicule
        parkingService.processIncomingVehicle();
        // Mettre à jour la date d'entrée du ticket de la deuxième visite pour simuler 1 heure de stationnement
        Ticket secondTicket = getLatestTicket("ABCDEF");
        updateTicketInTime(secondTicket.getId(), new Date(System.currentTimeMillis() - 3600000));
        // Simuler la sortie du véhicule
        parkingService.processExitingVehicle();
        Ticket ticketAfterSecondExit = getLatestTicket("ABCDEF");
        double fareSecondVisit = ticketAfterSecondExit.getPrice();
        // Avec remise de 5%, le tarif attendu est 1,5 * 0.95 = 1,425€
        assertEquals(1.425, fareSecondVisit, 0.01, "Le tarif de la seconde visite doit être 1,425€ (remise 5%).");
    }

    /**
     * Met à jour la date d'entrée (IN_TIME) du ticket identifié par ticketId.
     */
    private void updateTicketInTime(int ticketId, Date newInTime) {
        Connection con = null;
        try {
            con = dataBaseTestConfig.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE ticket SET IN_TIME = ? WHERE ID = ?");
            ps.setTimestamp(1, new java.sql.Timestamp(newInTime.getTime()));
            ps.setInt(2, ticketId);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            fail("Erreur lors de la mise à jour de l'heure d'entrée du ticket: " + e.getMessage());
        } finally {
            dataBaseTestConfig.closeConnection(con);
        }
    }

    /**
     * Récupère le ticket le plus récent pour un véhicule donné, en se basant sur l'ID décroissant.
     */
    private Ticket getLatestTicket(String vehicleRegNumber) {
        Ticket ticket = null;
        Connection con = null;
        try {
            con = dataBaseTestConfig.getConnection();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM ticket WHERE VEHICLE_REG_NUMBER = ? ORDER BY ID DESC LIMIT 1");
            ps.setString(1, vehicleRegNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ticket = new Ticket();
                int parkingNumber = rs.getInt("PARKING_NUMBER");
                // On suppose ici que le type de véhicule est CAR (adaptable selon votre contexte)
                ticket.setParkingSpot(new com.parkit.parkingsystem.model.ParkingSpot(parkingNumber, com.parkit.parkingsystem.constants.ParkingType.CAR, true));
                ticket.setId(rs.getInt("ID"));
                ticket.setVehicleRegNumber(rs.getString("VEHICLE_REG_NUMBER"));
                ticket.setPrice(rs.getDouble("PRICE"));
                ticket.setInTime(rs.getTimestamp("IN_TIME"));
                ticket.setOutTime(rs.getTimestamp("OUT_TIME"));
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            fail("Erreur lors de la récupération du ticket le plus récent: " + e.getMessage());
        } finally {
            dataBaseTestConfig.closeConnection(con);
        }
        return ticket;
    }
}
package ro.unibuc.hello.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.dto.ride.RideResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.exceptions.ride.RideConflictException;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.model.Vehicle;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.VehicleRepository;
import ro.unibuc.hello.repository.RideBookingRepository;

@Service
public class RideService {
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RideBookingRepository rideBookingRepository;
    private final RideBookingService rideBookingService;

    public RideService(RideRepository rideRepository, 
                        UserRepository userRepository,
                        VehicleRepository vehicleRepository,
                        RideBookingRepository rideBookingRepository,
                        RideBookingService rideBookingService
                        ) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.rideBookingRepository = rideBookingRepository;
        this.rideBookingService = rideBookingService;
        
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public RideResponseDTO createRide(RideRequestDTO rideRequestDTO) {
        // Check if driver exists in users table
        if (!userRepository.existsById(rideRequestDTO.getDriverId())) {
            throw new InvalidRideException("Driver does not exist as user.");
        } 
        
        // Check if departure time is in the future
        if (rideRequestDTO.getDepartureTime().isBefore(Instant.now())) {
            throw new InvalidRideException("Departure time must be in the future.");
        }

        // Check if start location differs from end location
        if (rideRequestDTO.getStartLocation().equals(rideRequestDTO.getEndLocation())) {
            throw new InvalidRideException("Start location has to be different from end location");
        }

        // Check if the number of available seats is > 0
        if (rideRequestDTO.getSeatsAvailable() < 1) {
            throw new InvalidRideException("Number of seats has to be greater than 0.");
        }

        // Check if the price is >= 0
        if (rideRequestDTO.getSeatPrice() < 0) {
            throw new InvalidRideException("Price has to be greater or equal to 0.");
        }

        // Check if vehicle is stored in vehicles table
        if (!vehicleRepository.existsByLicensePlate(rideRequestDTO.getCarLicensePlate())) {
            throw new InvalidRideException("Vehicle does not exist in the system.");
        } 
        
        // Check if driver it's involved in other ride as driver that's overlapping current
        if(!rideRepository.findByDriverIdAndTimeOverlap(
                    rideRequestDTO.getDriverId(), 
                    rideRequestDTO.getDepartureTime(),
                    rideRequestDTO.getArrivalTime()
                ).isEmpty()) {
            throw new RideConflictException("Driver involved in another ride.");
        }

        // Check if driver it's involved in other ride as passenger that's overlapping current
        List<RideBooking> bookingsInvolvedAsPassenger = rideBookingRepository
                        .findByPassengerId(rideRequestDTO.getDriverId());

        for (RideBooking booking : bookingsInvolvedAsPassenger) {
            if (!rideRepository.findByIdAndTimeOverlap(
                booking.getRideId(),
                rideRequestDTO.getDepartureTime(),
                rideRequestDTO.getArrivalTime()
            ).isEmpty()) {
                throw new InvalidRideBookingException("Driver involved in another ride at the same time as passenger.");
            }
        }

        Ride newRide = rideRequestDTO.toEntity();

        rideRepository.save(newRide);

        return RideResponseDTO.toDTO(newRide);

    }

    public List<Ride> getRidesByDate(Instant date) {
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
    
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return rideRepository.findAllByDepartureDate(startOfDay, endOfDay);
    }

    public RideResponseDTO updateRideStatusToInProgress(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new InvalidRideException("Ride not found."));
        
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new InvalidRideException("Ride status must be SCHEDULED to start the ride.");
        }
        
        if (Instant.now().isBefore(ride.getDepartureTime())) {
            throw new InvalidRideException("Ride cannot be started before the departure time.");
        }
        
        ride.setStatus(RideStatus.IN_PROGRESS);
        return RideResponseDTO.toDTO(rideRepository.save(ride));
    }

    public RideResponseDTO updateRideStatusToCompleted(String rideId, String currentLocation) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new InvalidRideException("Ride not found."));
        
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideException("Ride must be IN_PROGRESS to be completed.");
        }
        
        if (!currentLocation.equals(ride.getEndLocation())) {
            throw new InvalidRideException("Ride cannot be completed unless the location matches the destination.");
        }
        
        ride.setStatus(RideStatus.COMPLETED);
        return RideResponseDTO.toDTO(rideRepository.save(ride));
    }

    public RideResponseDTO updateRideStatusToCancelled(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new InvalidRideException("Ride not found."));
        
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new InvalidRideException("Only SCHEDULED rides can be canceled.");
        }
        
        if (Instant.now().isAfter(ride.getDepartureTime())) {
            throw new InvalidRideException("Ride cannot be canceled after departure time.");
        }
        
        List<RideBooking> bookings = rideBookingRepository.findByRideId(rideId);
        for (RideBooking booking : bookings) {
            rideBookingService.updateRideBookingStatusToCancelled(rideId, booking.getPassengerId());
        }

        ride.setStatus(RideStatus.CANCELLED);
        rideBookingRepository.saveAll(bookings);

        return RideResponseDTO.toDTO(rideRepository.save(ride));
    }
}

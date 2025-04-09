package ro.unibuc.hello.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.unibuc.hello.dto.rideBooking.RideBookingRequestDTO;
import ro.unibuc.hello.dto.rideBooking.RideBookingResponseDTO;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.dto.ride.RideResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.service.UserService;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.exception.EntityNotFoundException;
import ro.unibuc.hello.enums.RideBookingStatus;

import java.time.Clock;
import java.time.Instant;

@Service
public class RideBookingService {
    private final RideBookingRepository rideBookingRepository;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final UserService userService;
    private final Clock clock;

    public RideBookingService(RideBookingRepository rideBookingRepository, UserRepository userRepository, RideRepository rideRepository, UserService userService, Clock clock)
    {
        this.rideBookingRepository = rideBookingRepository;
        this.userRepository = userRepository;
        this.rideRepository = rideRepository;
        this.userService = userService;
        this.clock = clock;
    }

    public List<RideBookingResponseDTO> getPassengersByRideId(String rideId) {

        List<RideBooking> bookings = rideBookingRepository.findByRideId(rideId);
        
        return bookings.stream()
            .map(booking -> {
                // Get passenger information
                User passenger = userRepository.findById(booking.getPassengerId())
                .orElseThrow(() -> new EntityNotFoundException("User"));
                
                // Create response DTO
                RideBookingResponseDTO responseDTO = RideBookingResponseDTO.toDTO(booking);
                
                // Set passenger full name
                responseDTO.setPassengerFullName(passenger.getFirstName() + " " + passenger.getLastName());
                
                return responseDTO;
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public RideBookingResponseDTO createRideBooking (RideBookingRequestDTO rideBookingRequestDTO)
    {
        //check if passenger id is in users collection
        if(!userRepository.existsById(rideBookingRequestDTO.getPassengerId())){
            throw new InvalidRideBookingException("Passenger's id doesnt exist");
        }

        //ride id has to exist
        Ride ride = rideRepository.findById(rideBookingRequestDTO.getRideId())
            .orElseThrow(() -> new InvalidRideException("Ride ID does not exist."));

        //passenger shouldnt have already booked
        RideBooking existingBooking = rideBookingRepository.findByRideIdAndPassengerId(
            ride.getId(), rideBookingRequestDTO.getPassengerId()
            ).orElse(null);

        if (existingBooking != null) {
            throw new InvalidRideBookingException("Passenger already booked for this ride.");
        }
            

        //check if the passenger has a conflicting ride
        List<RideBooking> bookingsInvolvedAsPassenger = rideBookingRepository
                        .findByPassengerId(rideBookingRequestDTO.getPassengerId());

        for (RideBooking booking : bookingsInvolvedAsPassenger) {
            if (!rideRepository.findByIdAndTimeOverlap(
                rideBookingRequestDTO.getRideId(),
                ride.getDepartureTime(),
                ride.getArrivalTime()
            ).isEmpty()) {
                throw new InvalidRideBookingException("User involved in another ride at the same time.");
            }
        }

        //available seats >0
        if(ride.getSeatsAvailable() < 1) {
            throw new InvalidRideBookingException("No more seats available");
        }

        //ride has to be scheduled

        if(ride.getStatus() != RideStatus.SCHEDULED)
        {
            throw new InvalidRideBookingException("Ride is not scheduled");
        }

       RideBooking newRideBooking = rideBookingRequestDTO.toEntity();

       rideBookingRepository.save(newRideBooking);

       ride.setSeatsAvailable(ride.getSeatsAvailable() - 1);
       rideRepository.save(ride);

       return RideBookingResponseDTO.toDTO(newRideBooking);
    }

    public RideBookingResponseDTO updateRideBookingStatusToCancelled(String rideId, String passengerId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new InvalidRideException("Ride not found."));

        RideBooking rideBooking = rideBookingRepository.findByRideIdAndPassengerId(rideId, passengerId)
                        .orElse(null);

        if (rideBooking == null) {
            throw new InvalidRideBookingException("Booking not found.");
        }

        // check if the rideBooking status is BOOKED
        if (rideBooking.getRideBookingStatus() != RideBookingStatus.BOOKED) {
            throw new InvalidRideBookingException("Ride already cancelled.");
        }
    
        // Check if instant.now < departure time
        if (!clock.instant().isBefore(ride.getDepartureTime())) {
            throw new InvalidRideBookingException("Ride cannot be cancelled after it started.");
        }
    
        rideBooking.setRideBookingStatus(RideBookingStatus.CANCELLED);

        ride.setSeatsAvailable(ride.getSeatsAvailable() + 1);
        rideRepository.save(ride);
        
        return RideBookingResponseDTO.toDTO(rideBookingRepository.save(rideBooking));
    }
    
    

}

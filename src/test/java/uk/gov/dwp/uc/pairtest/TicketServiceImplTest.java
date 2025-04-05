package uk.gov.dwp.uc.pairtest;

// Standard JUnit 5 imports for testing features
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Mockito imports for mocking and verification
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Static imports to make assertion and verification code cleaner
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// My project's classes that I need for testing
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;


/**
 * This is my unit test class for TicketServiceImpl.
 * My goal here is to verify that my implementation behaves correctly under various
 * conditions, both valid and invalid, according to the business rules.
 * I chose JUnit 5 as the testing framework and Mockito for mocking the external
 * dependencies (TicketPaymentService, SeatReservationService). This allows me to test
 * my service's logic in isolation without relying on the actual third-party code.
 */
@ExtendWith(MockitoExtension.class) // This integrates Mockito with JUnit 5 for annotations like @Mock, @InjectMocks
class TicketServiceImplTest {

    // Here I declare the mocks for the dependencies my service needs.
    // Mockito will create dummy implementations of these interfaces.
    @Mock
    private TicketPaymentService paymentServiceMock;
    @Mock
    private SeatReservationService reservationServiceMock;

    // This tells Mockito to create an instance of my TicketServiceImpl
    // and automatically inject the mocks declared above into its fields.
    // This is the object I'll be testing.
    @InjectMocks
    private TicketServiceImpl ticketService;

    // ArgumentCaptors are useful tools from Mockito. I'll use these to capture
    // the actual arguments that my service passes when it calls the mock methods,
    // so I can assert that the correct values (like cost, seats, account ID) were passed.
    @Captor
    private ArgumentCaptor<Long> accountIdCaptor;
    @Captor
    private ArgumentCaptor<Integer> amountCaptor;
    @Captor
    private ArgumentCaptor<Integer> seatsCaptor;

    // Defining constants here makes my test values consistent and readable.
    private static final Long VALID_ACCOUNT_ID = 123L;
    private static final int ADULT_PRICE = 2500; // Pence
    private static final int CHILD_PRICE = 1500; // Pence
    private static final int MAX_TICKETS = 25;

    // --- VALID PURCHASE SCENARIOS ---
    // In this section, I test various combinations of tickets that should be processed successfully.
    // My main checks are: 1) No exception is thrown. 2) The payment and reservation services
    // are called exactly once with the correctly calculated amount/seats.

    @Test
    @DisplayName("Test Case: Purchase Adults Only - Should succeed and call services")
    void purchaseTickets_ValidAdultsOnly_CallsServices() {
        // Arrange: Set up the input for this specific test case.
        TicketTypeRequest request = new TicketTypeRequest(Type.ADULT, 3);
        int expectedCost = 3 * ADULT_PRICE;
        int expectedSeats = 3;

        // Act: Call the method I'm testing. I use assertDoesNotThrow for valid cases.
        assertDoesNotThrow(() -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, request));

        // Assert: Verify the interactions with the mocks. Did my service call makePayment?
        // Did it call reserveSeat? Were the arguments correct?
        verify(paymentServiceMock).makePayment(VALID_ACCOUNT_ID, expectedCost);
        verify(reservationServiceMock).reserveSeat(VALID_ACCOUNT_ID, expectedSeats);
        // I also verify that no *other* unexpected calls were made to the mocks.
        verifyNoMoreInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Purchase Adults and Children - Should succeed and call services")
    void purchaseTickets_ValidAdultsAndChildren_CallsServices() {
        // Arrange: Two adults, four children.
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 2);
        TicketTypeRequest childReq = new TicketTypeRequest(Type.CHILD, 4);
        int expectedCost = (2 * ADULT_PRICE) + (4 * CHILD_PRICE); // Calculate expected total cost
        int expectedSeats = 2 + 4;                             // Adults + Children need seats

        // Act: Call the service method.
        assertDoesNotThrow(() -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq, childReq));

        // Assert: Verify calls to mocks with calculated values.
        verify(paymentServiceMock).makePayment(VALID_ACCOUNT_ID, expectedCost);
        verify(reservationServiceMock).reserveSeat(VALID_ACCOUNT_ID, expectedSeats);
        verifyNoMoreInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Purchase Adults and Infants - Should succeed and call services")
    void purchaseTickets_ValidAdultsAndInfants_CallsServices() {
        // Arrange: Infants are free and don't need seats.
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 1);
        TicketTypeRequest infantReq = new TicketTypeRequest(Type.INFANT, 1);
        int expectedCost = ADULT_PRICE; // Cost is only for the adult
        int expectedSeats = 1;       // Seats are only for the adult

        // Act
        assertDoesNotThrow(() -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq, infantReq));

        // Assert
        verify(paymentServiceMock).makePayment(VALID_ACCOUNT_ID, expectedCost);
        verify(reservationServiceMock).reserveSeat(VALID_ACCOUNT_ID, expectedSeats);
        verifyNoMoreInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Purchase Mixed (A, C, I) - Should succeed and call services")
    void purchaseTickets_ValidMixedBatch_CallsServices() {
        // Arrange: A mix of all types.
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 5);
        TicketTypeRequest childReq = new TicketTypeRequest(Type.CHILD, 3);
        TicketTypeRequest infantReq = new TicketTypeRequest(Type.INFANT, 2);
        int expectedCost = (5 * ADULT_PRICE) + (3 * CHILD_PRICE); // Infants cost 0
        int expectedSeats = 5 + 3;                             // Infants need no seats

        // Act
        assertDoesNotThrow(() -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq, childReq, infantReq));

        // Assert: Using ArgumentCaptors here to be extra sure about the values passed.
        verify(paymentServiceMock).makePayment(accountIdCaptor.capture(), amountCaptor.capture());
        verify(reservationServiceMock).reserveSeat(accountIdCaptor.capture(), seatsCaptor.capture());

        // Now I check the captured values.
        assertEquals(VALID_ACCOUNT_ID, accountIdCaptor.getAllValues().get(0)); // 1st call (payment) acc ID
        assertEquals(VALID_ACCOUNT_ID, accountIdCaptor.getAllValues().get(1)); // 2nd call (seat) acc ID
        assertEquals(expectedCost, amountCaptor.getValue());
        assertEquals(expectedSeats, seatsCaptor.getValue());

        verifyNoMoreInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Purchase Exactly Max Tickets (25) - Should be allowed")
    void purchaseTickets_ValidExactlyMaxTickets_CallsServices() {
        // Arrange: Testing the boundary condition.
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, MAX_TICKETS);
        int expectedCost = MAX_TICKETS * ADULT_PRICE;
        int expectedSeats = MAX_TICKETS;

        // Act
        assertDoesNotThrow(() -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq));

        // Assert: Verify services are called as expected.
        verify(paymentServiceMock).makePayment(VALID_ACCOUNT_ID, expectedCost);
        verify(reservationServiceMock).reserveSeat(VALID_ACCOUNT_ID, expectedSeats);
        verifyNoMoreInteractions(paymentServiceMock, reservationServiceMock);
    }

    // --- INVALID INPUT SCENARIOS ---
    // Testing cases where the input data itself is fundamentally wrong.

    @Test
    @DisplayName("Test Case: Null Account ID - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidNullAccountId_ThrowsException() {
        // Arrange
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 1);

        // Act & Assert: I expect my InvalidPurchaseException here.
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(null, adultReq));
        // Checking the message helps confirm *why* it failed.
        assertTrue(ex.getMessage().contains("Account ID is invalid"));
        // Crucially, no external services should have been called.
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    // Using a ParameterizedTest to check multiple invalid IDs without repeating code.
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L}) // Input values for the test.
    @DisplayName("Test Case: Zero or Negative Account ID - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidZeroOrNegativeAccountId_ThrowsException(long invalidAccountId) {
        // Arrange
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 1);

        // Act & Assert: Expecting the same exception for all these invalid IDs.
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(invalidAccountId, adultReq));
        assertTrue(ex.getMessage().contains("Account ID is invalid"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Null Ticket Requests Array - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidNullRequestsArray_ThrowsException() {
        // Act & Assert: Calling with null for the varargs array.
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, (TicketTypeRequest[]) null));
        assertTrue(ex.getMessage().contains("At least one ticket type must be requested"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Empty Ticket Requests Array - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidEmptyRequestsArray_ThrowsException() {
        // Arrange: An empty array.
        TicketTypeRequest[] emptyRequests = {};

        // Act & Assert: Calling with the empty array.
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, emptyRequests));
        assertTrue(ex.getMessage().contains("At least one ticket type must be requested"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    // --- BUSINESS RULE VIOLATION SCENARIOS ---
    // Testing specific rules defined in the requirements.

    @Test
    @DisplayName("Test Case: Child Only (No Adult) - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidChildOnly_ThrowsException() {
        // Arrange: Violates the "must have adult" rule.
        TicketTypeRequest childReq = new TicketTypeRequest(Type.CHILD, 1);

        // Act & Assert
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, childReq));
        assertTrue(ex.getMessage().contains("without purchasing at least one Adult ticket"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Infant Only (No Adult) - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidInfantOnly_ThrowsException() {
        // Arrange: Also violates the "must have adult" rule.
        TicketTypeRequest infantReq = new TicketTypeRequest(Type.INFANT, 1);

        // Act & Assert
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, infantReq));
        assertTrue(ex.getMessage().contains("without purchasing at least one Adult ticket"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Child and Infant Only (No Adult) - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidChildAndInfantOnly_ThrowsException() {
        // Arrange: Still no adult present.
        TicketTypeRequest childReq = new TicketTypeRequest(Type.CHILD, 1);
        TicketTypeRequest infantReq = new TicketTypeRequest(Type.INFANT, 1);

        // Act & Assert
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, childReq, infantReq));
        assertTrue(ex.getMessage().contains("without purchasing at least one Adult ticket"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: More Than Max Tickets (26) - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidTooManyTickets_ThrowsException() {
        // Arrange: Violates the max tickets rule (25).
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, MAX_TICKETS + 1);

        // Act & Assert
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq));
        assertTrue(ex.getMessage().contains("Cannot purchase more than " + MAX_TICKETS + " tickets"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }

    @Test
    @DisplayName("Test Case: Mixed Tickets Exceeding Max (26) - Should throw InvalidPurchaseException")
    void purchaseTickets_InvalidMixedTooManyTickets_ThrowsException() {
        // Arrange: Combined total exceeds the limit.
        TicketTypeRequest adultReq = new TicketTypeRequest(Type.ADULT, 15);
        TicketTypeRequest childReq = new TicketTypeRequest(Type.CHILD, 11); // 15 + 11 = 26

        // Act & Assert
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(VALID_ACCOUNT_ID, adultReq, childReq));
        assertTrue(ex.getMessage().contains("Cannot purchase more than " + MAX_TICKETS + " tickets"));
        verifyNoInteractions(paymentServiceMock, reservationServiceMock);
    }


}
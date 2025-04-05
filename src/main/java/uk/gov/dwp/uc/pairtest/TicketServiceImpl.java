package uk.gov.dwp.uc.pairtest;


import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

/**
 * This is my implementation of the TicketService interface.
 * My main goal here is to correctly process ticket purchase requests according
 * to all the business rules provided, calculate the right payment amount and
 * number of seats, and then interact with the third-party services for payment
 * and seat reservation. I also need to handle invalid requests by throwing
 * the custom InvalidPurchaseException.
 * The requirement also mentioned only having private methods other than the public
 * interface method, which I've followed (apart from the necessary constructor).
 */
public class TicketServiceImpl implements TicketService {

    /**
     * Should only have private methods other than the one below.
     */

    // --- Constants ---
    // I decided to define constants for the business rules to make the code clearer
    // and easier to maintain if prices or limits change.
    // I'm using pence for money calculations because the payment service's
    // method takes an 'int', which usually implies the smallest currency unit.
    private static final int ADULT_TICKET_PRICE_PENCE = 2500; // £25
    private static final int CHILD_TICKET_PRICE_PENCE = 1500; // £15
    private static final int INFANT_TICKET_PRICE_PENCE = 0;    // £0 (Rule: Infants do not pay)
    // Rule: Max 25 tickets per purchase.
    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    // Rule: Valid accounts have ID > 0.
    private static final long MIN_VALID_ACCOUNT_ID = 1L;

    // --- Dependencies ---
    // These services are provided externally, so I need fields to hold references to them.
    // Making them final means they must be set in the constructor and cannot be changed later.
    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    /**
     * I'm using constructor injection here to receive the external service dependencies.
     * This is standard practice for decoupling and makes the class much easier to test,
     * as I can inject mock services during testing (as seen in my test class).
     * I added null checks to ensure the service starts in a valid state - it cannot
     * function without these dependencies.
     *
     * @param ticketPaymentService Provided payment service instance.
     * @param seatReservationService Provided seat reservation service instance.
     * @throws IllegalArgumentException If either dependency is null on construction.
     */
    public TicketServiceImpl(TicketPaymentService ticketPaymentService, SeatReservationService seatReservationService) {
        if (ticketPaymentService == null) {
            throw new IllegalArgumentException("TicketPaymentService cannot be null.");
        }
        if (seatReservationService == null) {
            throw new IllegalArgumentException("SeatReservationService cannot be null.");
        }
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    /**
     * This is the main method required by the TicketService interface.
     * My approach here follows a clear sequence:
     * 1. Validate the basic inputs (account ID, request array presence).
     * 2. Calculate the totals (tickets, cost, seats) by iterating through the requests.
     * 3. Validate these calculated totals against the core business rules (max tickets, adult presence).
     * 4. If all validations pass, proceed to call the payment and reservation services.
     * Any validation failure results in an InvalidPurchaseException being thrown immediately.
     *
     * @param accountId The account making the purchase. Checked > 0.
     * @param ticketTypeRequests The list of tickets requested. Checked not null/empty.
     * @throws InvalidPurchaseException If any validation or business rule check fails.
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {

        // Step 1: Input Validation (Fail fast)
        validateAccountId(accountId);
        validateTicketRequestsArrayIsNotEmpty(ticketTypeRequests);

        // Step 2: Calculation
        // I decided to encapsulate the results of calculation into a small record
        // to make passing the data between my private methods cleaner.
        CalculationResult totals = calculateTotalsAndCounts(ticketTypeRequests);

        // Step 3: Business Rule Validation (using calculated totals)
        validateBusinessRules(totals);

        // Step 4: External Service Interaction (only if all previous steps passed)
        // The exercise assumes these calls always succeed, so no try/catch needed here.
        makePaymentRequest(accountId, totals.getTotalAmountToPayPence());
        reserveSeatsRequest(accountId, totals.getTotalSeatsToAllocate());

        // If we get here without an exception, the purchase is considered successful.
    }

    //region Private Helper Methods (as required by the prompt)
    // I created these private methods to break down the logic from purchaseTickets,
    // improving readability and maintainability. Each helper has a single responsibility.

    /**
     * My private helper to validate the account ID rule (must be > 0).
     * @param accountId The ID to check.
     * @throws InvalidPurchaseException If rule is violated.
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId < MIN_VALID_ACCOUNT_ID) {
            // Throwing my custom exception with a clear message.
            throw new InvalidPurchaseException("Account ID is invalid. Must be greater than zero.");
        }
    }

    /**
     * My private helper to check the basic validity of the requests array (not null or empty).
     * The validity of individual requests (count>0, type!=null) is already handled
     * by the TicketTypeRequest constructor, which I rely on here.
     * @param ticketTypeRequests The input array.
     * @throws InvalidPurchaseException If array is null or empty.
     */
    private void validateTicketRequestsArrayIsNotEmpty(TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket type must be requested.");
        }
    }

    /**
     * This helper method iterates through all the individual ticket requests.
     * My goal here is to sum up the total number of tickets, the total cost (in pence),
     * the total number of seats required (remembering infants don't need one),
     * and also keep track of the counts of each ticket type (Adult, Child, Infant)
     * as I need those counts for the business rule validation later.
     * @param ticketTypeRequests The validated, non-empty array of requests.
     * @return A CalculationResult object holding all the aggregated totals.
     */
    private CalculationResult calculateTotalsAndCounts(TicketTypeRequest... ticketTypeRequests) {
        int totalTickets = 0;
        int totalSeatsToAllocate = 0;
        int totalAmountToPayPence = 0;
        int adultTickets = 0;
        int childTickets = 0;
        int infantTickets = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            int numberOfTickets = request.getNoOfTickets(); // Assumed > 0 due to constructor validation
            totalTickets += numberOfTickets;

            switch (request.getTicketType()) {
                case ADULT:
                    adultTickets += numberOfTickets;
                    totalAmountToPayPence += numberOfTickets * ADULT_TICKET_PRICE_PENCE;
                    totalSeatsToAllocate += numberOfTickets; // Rule: Adults need seats
                    break;
                case CHILD:
                    childTickets += numberOfTickets;
                    totalAmountToPayPence += numberOfTickets * CHILD_TICKET_PRICE_PENCE;
                    totalSeatsToAllocate += numberOfTickets; // Rule: Children need seats
                    break;
                case INFANT:
                    infantTickets += numberOfTickets;
                    // totalAmountToPayPence += numberOfTickets * INFANT_TICKET_PRICE_PENCE; // This is always 0, can omit
                    // Rule: Infants do not pay and are not allocated a seat.
                    break;
            }
        }
        // Return the results bundled in my private record.
        return new CalculationResult(totalTickets, totalSeatsToAllocate, totalAmountToPayPence, adultTickets, childTickets, infantTickets);
    }

    /**
     * This is where I enforce the core business rules based on the overall request totals.
     * I check the maximum ticket limit and the rule about needing an adult
     * if children or infants are being purchased. I also added a belt-and-braces
     * check for totalTickets > 0, though previous validation should catch that.
     * Finally, some sanity checks on calculated amounts.
     * @param totals The aggregated results from the calculation step.
     * @throws InvalidPurchaseException If any rule is violated.
     */
    private void validateBusinessRules(CalculationResult totals) {
        // Rule: Maximum 25 tickets per purchase
        if (totals.getTotalTickets() > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException(
                    String.format("Cannot purchase more than %d tickets at a time. Requested: %d",
                            MAX_TICKETS_PER_PURCHASE, totals.getTotalTickets())
            );
        }

        // Rule: Child and Infant tickets require at least one Adult ticket
        if ((totals.getChildTickets() > 0 || totals.getInfantTickets() > 0) && totals.getAdultTickets() == 0) {
            throw new InvalidPurchaseException("Child or Infant tickets cannot be purchased without purchasing at least one Adult ticket.");
        }

        // Defensive check: Ensure at least one ticket is requested overall.
        if (totals.getTotalTickets() <= 0) {
            throw new InvalidPurchaseException("Cannot make a purchase request for zero tickets.");
        }

        // Sanity checks (shouldn't happen with current logic, but good practice)
        if (totals.getTotalAmountToPayPence() < 0) {
            throw new InvalidPurchaseException("Internal Error: Calculated payment amount is negative.");
        }
        if (totals.getTotalSeatsToAllocate() < 0) {
            throw new InvalidPurchaseException("Internal Error: Calculated seat allocation is negative.");
        }

        if (totals.getInfantTickets() > totals.getAdultTickets()) {
            // Throw the specific exception if there are more infants than adults
            throw new InvalidPurchaseException(
                    String.format("Number of infants (%d) cannot exceed the number of adults (%d). Please ensure there is an adult lap for each infant.",
                            totals.getInfantTickets(), totals.getAdultTickets())
            );
        }
    }

    /**
     * My private helper to interact with the payment service.
     * I only make the call if the calculated amount is actually greater than zero.
     * Based on the exercise assumptions, I don't need to handle errors from this call.
     * @param accountId The valid account ID.
     * @param totalAmountToPayPence The calculated amount in pence.
     */
    private void makePaymentRequest(Long accountId, int totalAmountToPayPence) {
        if (totalAmountToPayPence > 0) {
            // The payment service interface takes a 'long', so my Long accountId gets auto-unboxed.
            ticketPaymentService.makePayment(accountId, totalAmountToPayPence);
        }
    }

    /**
     * My private helper to interact with the seat reservation service.
     * Similarly, I only make the call if the number of seats required is greater than zero.
     * Again, based on assumptions, I don't need to handle errors from this call.
     * @param accountId The valid account ID.
     * @param totalSeatsToAllocate The calculated number of seats (Adults + Children).
     */
    private void reserveSeatsRequest(Long accountId, int totalSeatsToAllocate) {
        if (totalSeatsToAllocate > 0) {
            // The reservation service interface takes a 'long', so my Long accountId gets auto-unboxed.
            seatReservationService.reserveSeat(accountId, totalSeatsToAllocate);
        }
    }


    /**
     * I created this private record `CalculationResult` as a simple data holder.
     * Its purpose is just to bundle together all the values I calculate in
     * `calculateTotalsAndCounts` so I can easily pass them as a single object
     * to the `validateBusinessRules` method. Using a record makes it immutable
     * by default and saves me writing boilerplate getter code. It's private because
     * only this service class needs to know about it.
     */
    private record CalculationResult(
            int totalTickets,
            int totalSeatsToAllocate,
            int totalAmountToPayPence,
            int adultTickets,
            int childTickets,
            int infantTickets
    ) {

        public int getTotalTickets() { return totalTickets; }
        public int getTotalSeatsToAllocate() { return totalSeatsToAllocate; }
        public int getTotalAmountToPayPence() { return totalAmountToPayPence; }
        public int getAdultTickets() { return adultTickets; }
        public int getChildTickets() { return childTickets; }
        public int getInfantTickets() { return infantTickets; }
    }

}
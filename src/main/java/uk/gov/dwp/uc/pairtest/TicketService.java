package uk.gov.dwp.uc.pairtest;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public interface TicketService {

    /**
     * Purchases tickets based on the provided requests.
     *
     * @param accountId The ID of the account making the purchase.
     * @param ticketTypeRequests Variable number of ticket purchase requests.
     * @throws InvalidPurchaseException if the purchase request is invalid
     *         (e.g., invalid account ID, too many tickets, invalid ticket mix).
     */
    void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException;

}
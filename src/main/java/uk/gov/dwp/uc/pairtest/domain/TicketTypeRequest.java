package uk.gov.dwp.uc.pairtest.domain;

/**
 * This class represents the request for a certain number of tickets of a specific type.
 * The requirement stated it MUST be immutable, so my first step was to make the
 * class and its fields `final`. This ensures once an instance is created, it cannot be changed.
 */
public final class TicketTypeRequest { // Made class final for immutability

    // Declared fields as final, they'll be set once in the constructor.
    private final int noOfTickets;
    private final Type type;

    /**
     * I decided an Enum was the clearest and safest way to represent the fixed
     * set of ticket types (ADULT, CHILD, INFANT) defined in the business rules.
     */
    public enum Type {
        ADULT, CHILD , INFANT
    }

    /**
     * This is the constructor where I initialize the final fields.
     * Crucially, I added validation here based on the requirement that the purchaser
     * declares *how many* tickets. To me, this implies a request must be for a positive
     * number of tickets (at least 1) and must have a valid type. I'm throwing
     * IllegalArgumentException here because this validation relates to constructing
     * a valid object state, distinct from the business rule validation in the service.
     *
     * @param type The type of ticket - I check it's not null.
     * @param noOfTickets The number requested - I check it's greater than 0.
     * @throws IllegalArgumentException If the preconditions (type != null, noOfTickets > 0) aren't met.
     */
    public TicketTypeRequest(Type type, int noOfTickets) {
        if (type == null) {
            // Fail fast if the type is missing.
            throw new IllegalArgumentException("Ticket type cannot be null.");
        }
        if (noOfTickets <= 0) {
            // A request for zero or negative tickets doesn't make sense in this context.
            throw new IllegalArgumentException("Number of tickets must be greater than zero. Value provided: " + noOfTickets);
        }
        // If validation passes, assign to the final fields.
        this.type = type;
        this.noOfTickets = noOfTickets;
    }

    // Standard getters for the final fields.
    public int getNoOfTickets() {
        return noOfTickets;
    }

    public Type getTicketType() {
        return type;
    }

    // I also added equals, hashCode, and toString. While not strictly required by the brief,
    // they are generally good practice for value objects like this, especially if they
    // might be used in collections or logs later.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketTypeRequest that = (TicketTypeRequest) o;
        return noOfTickets == that.noOfTickets && type == that.type;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + noOfTickets;
        return result;
    }

    @Override
    public String toString() {
        // Helpful for debugging.
        return "TicketTypeRequest{" +
                "type=" + type +
                ", noOfTickets=" + noOfTickets +
                '}';
    }
}
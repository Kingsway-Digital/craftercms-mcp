package foo

import org.craftercms.ai.DeclareTool


public class BookingService {
 
    @DeclareTool(toolName="bookFlight", toolDescription="Book a specific seat on a given flight" )
    public String bookFlight(String flight, String seat) {
        return "Booked"
    }
}   
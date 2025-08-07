package foo

import org.craftercms.ai.mcp.server.tools.DeclareTool
import org.craftercms.ai.mcp.server.tools.DeclareToolParam
 
 
public class BookingService {
 
    @DeclareTool(toolName="bookFlight", returnType="string", toolDescription="Book a specific seat on a given flight" )
    @DeclareToolParam (name="flight", type="string", description="The flight the user wants")
    @DeclareToolParam (name="seat", type="string", description="The seat the user wants")
    public String bookFlight(String flight, String seat) {
        return "Booked"
    }
}   
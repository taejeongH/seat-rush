package com.seatrush.ticketservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "outbox.relay.enabled=false",
        "outbox.cleanup.enabled=false",
        "outbox.monitor.enabled=false"
})
class TicketServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}

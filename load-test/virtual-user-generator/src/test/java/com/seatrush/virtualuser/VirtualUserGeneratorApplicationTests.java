package com.seatrush.virtualuser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties =
        "virtual-user.gateway-base-url=https://seat-rush.example.com")
class VirtualUserGeneratorApplicationTests {

    @Test
    void contextLoads() {
    }
}

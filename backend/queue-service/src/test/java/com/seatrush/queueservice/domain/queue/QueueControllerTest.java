package com.seatrush.queueservice.domain.queue;

import com.seatrush.queueservice.domain.queue.controller.QueueController;
import com.seatrush.queueservice.domain.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    /**
     * 사용자 헤더가 없으면 인증 오류를 반환하는지 검증합니다.
     */
    @Test
    void missingUserHeaderReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/schedules/1/queues/join"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH003"));
    }
}

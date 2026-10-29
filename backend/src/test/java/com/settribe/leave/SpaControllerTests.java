package com.settribe.leave;

import com.settribe.leave.controller.SpaController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaControllerTests {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SpaController()).build();

    @Test
    void forwardsClientRoutesToSpaEntryPoint() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}

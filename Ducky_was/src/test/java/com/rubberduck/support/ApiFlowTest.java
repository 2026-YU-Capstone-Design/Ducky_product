package com.rubberduck.support;

import java.security.SecureRandom;
import java.util.Base64;

import com.rubberduck.domain.auth.service.AuthTokenService;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceRepository;
import com.rubberduck.domain.device.repository.DeviceUserRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public abstract class ApiFlowTest {

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected DeviceRepository devices;
    @Autowired
    protected DeviceUserRepository links;

    protected MockMvc mockMvc;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    protected record TestDevice(Device device, String authorization) {}

    protected TestDevice provision(String serial, User user) {
        Device device = devices.save(Device.create(serial, "test"));
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        DeviceUser link = DeviceUser.link(device, user, "OWNER");
        link.setTokenHash(AuthTokenService.hashDeviceKey(key));
        links.save(link);
        return new TestDevice(device, "Bearer device." + key);
    }

    protected User newUser(String name) {
        String id = name + System.nanoTime();
        return users.save(User.create(name, id + "@example.com", id, "unused"));
    }
}

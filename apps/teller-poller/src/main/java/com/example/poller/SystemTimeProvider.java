package com.example.poller;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class SystemTimeProvider implements TimeProvider {
    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}

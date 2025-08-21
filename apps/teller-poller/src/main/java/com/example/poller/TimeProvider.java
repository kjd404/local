package com.example.poller;

import java.time.OffsetDateTime;

public interface TimeProvider {
    OffsetDateTime now();
}

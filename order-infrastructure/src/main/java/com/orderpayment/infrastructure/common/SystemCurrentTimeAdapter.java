package com.orderpayment.infrastructure.common;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SystemCurrentTimeAdapter implements CurrentTimePort {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}

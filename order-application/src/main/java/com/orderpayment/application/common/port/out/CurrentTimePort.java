package com.orderpayment.application.common.port.out;

import java.time.LocalDateTime;

public interface CurrentTimePort {

    LocalDateTime now();
}

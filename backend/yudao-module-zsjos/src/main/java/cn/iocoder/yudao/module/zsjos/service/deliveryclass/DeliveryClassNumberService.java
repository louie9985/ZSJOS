package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DeliveryClassNumberService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong second = new AtomicLong();
    private final AtomicInteger sequence = new AtomicInteger(ThreadLocalRandom.current().nextInt(10_000));

    public String next() {
        long currentSecond = System.currentTimeMillis() / 1000;
        long previous = second.getAndSet(currentSecond);
        if (previous != currentSecond) sequence.set(ThreadLocalRandom.current().nextInt(10_000));
        return "BJ" + FORMAT.format(LocalDateTime.now()) + String.format("%04d", Math.floorMod(sequence.getAndIncrement(), 10_000));
    }
}

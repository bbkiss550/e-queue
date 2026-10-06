package th.co.equeue.domain;

import java.time.*;
public record Booking(long id, String bookingCode, String customerName, String customerPhone,
    LocalDate bookingDate, LocalTime bookingTime, String status, String cancellationReason,
    String createBy, Instant createDate, Instant updateDate) {}

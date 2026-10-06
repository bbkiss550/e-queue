package th.co.equeue.domain;
import java.time.LocalTime;
public record BusinessHour(int dayOfWeek, boolean open, LocalTime openTime, LocalTime closeTime) {}

package th.co.equeue.web;

import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class ApiModels {
    public record CreateBooking(@NotNull LocalDate date,@NotNull LocalTime time,
        @NotBlank @Size(max=100) String name,@NotBlank @Size(max=30) String phone,
        @NotNull UUID requestKey,boolean duplicateConfirmed) {}
    public record DateRequest(@NotNull LocalDate date) {}
    public record PhoneRequest(@NotBlank @Size(max=30) String phone) {}
    public record NotificationRead(@Positive Long id) {}
    public record ChangeRequest(@NotNull Long id,LocalTime time,@Size(max=30) String phone) {}
    public record ListRequest(@NotNull LocalDate from,@NotNull LocalDate to,@Size(max=100) String search) {}
    public record HourRequest(@Min(1) @Max(7) int dayOfWeek,boolean open,@NotNull LocalTime openTime,@NotNull LocalTime closeTime) {}
    public record SettingsRequest(@NotBlank @Size(max=100) String shopName,@Min(1) @Max(999) int capacityPerHour,
        @Min(0) @Max(60) int leadMinutes,@NotNull @Size(min=7,max=7) List<@Valid HourRequest> hours) {}
    public record HolidayRequest(@NotNull LocalDate date,@Size(max=200) String description) {}
}

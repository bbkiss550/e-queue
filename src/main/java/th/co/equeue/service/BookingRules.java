package th.co.equeue.service;

import java.time.*;
import th.co.equeue.domain.*;
import th.co.equeue.web.ApiException;

public final class BookingRules {
    private BookingRules() {}
    public static String phone(String value) {
        String normalized=value==null ? "" : value.replaceAll("[\\s-]", "");
        if (!normalized.matches("0[0-9]{8,9}")) throw new ApiException("INVALID_PHONE","กรุณากรอกเบอร์โทรศัพท์ไทย 9–10 หลัก เริ่มต้นด้วย 0");
        return normalized;
    }
    public static boolean availableTime(LocalDate date,LocalTime time,BusinessHour hours,boolean holiday,int lead,LocalDateTime now) {
        return !holiday && hours.open() && time.getMinute()==0 && time.getSecond()==0 && time.getNano()==0
            && !time.isBefore(hours.openTime()) && time.isBefore(hours.closeTime())
            && date.atTime(time).isAfter(now) && !date.atTime(time).isBefore(now.plusMinutes(lead));
    }
    public static void horizon(LocalDate date,LocalDate today) {
        if (date.isBefore(today) || date.isAfter(today.plusDays(2))) throw new ApiException("INVALID_DATE","จองได้ตั้งแต่วันนี้ถึงอีก 2 วันเท่านั้น");
    }
}

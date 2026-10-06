package th.co.equeue.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=QueueApiController.class)
public class ApiErrorHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiErrorHandler.class);
    @ExceptionHandler(ApiException.class) public ResponseEntity<?> rule(ApiException e) {return ResponseEntity.status(e.code().equals("NOT_FOUND")?404:409).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(Exception e) {return ResponseEntity.badRequest().body(Map.of("code","VALIDATION","message","กรุณาตรวจสอบข้อมูลที่กรอกให้ครบและถูกต้อง"));}
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception e) {log.error("Queue request failed",e);return ResponseEntity.internalServerError().body(Map.of("code","SERVER_ERROR","message","ไม่สามารถทำรายการได้ในขณะนี้ กรุณาลองใหม่"));}
}

package th.co.equeue.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class NotificationStream {
    private final CopyOnWriteArrayList<SseEmitter> clients=new CopyOnWriteArrayList<>();
    public SseEmitter connect() {
        var emitter=new SseEmitter(1_800_000L); clients.add(emitter);
        emitter.onCompletion(()->clients.remove(emitter)); emitter.onTimeout(()->{clients.remove(emitter);emitter.complete();}); emitter.onError(e->clients.remove(emitter));
        send(emitter,"ready",Map.of("connected",true)); return emitter;
    }
    @TransactionalEventListener
    public void changed(QueueService.QueueChanged event) {
        for(var client:clients) send(client,event.type(),Map.of("bookingId",event.bookingId()==null?0:event.bookingId()));
    }
    @Scheduled(fixedDelay=25_000)
    public void heartbeat() { for(var client:clients) send(client,"heartbeat",Map.of("alive",true)); }
    private void send(SseEmitter emitter,String event,Object data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch(IOException|IllegalStateException ex) { clients.remove(emitter);emitter.complete(); }
    }
}

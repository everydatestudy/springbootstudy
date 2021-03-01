package event;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class EventConfig {
    @EventListener
    public void listen(TestEvent event) {
        System.out.println("接收到事件：" + event);
    }
}
package event;

import org.springframework.context.ApplicationEvent;

public class TestEvent extends ApplicationEvent {
    private String name;
    public TestEvent(Object source, String name) {
        super(source);
        this.name = name;
    }
    public TestEvent(Object source) {
        super(source);
    }
    @Override
    public String toString() {
        return "TestEvent{name='" + name + '\'' + '}';
    }
}
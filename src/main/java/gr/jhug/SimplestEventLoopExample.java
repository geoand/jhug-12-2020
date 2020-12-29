package gr.jhug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class SimplestEventLoopExample {

    private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();

    public SimplestEventLoopExample on(String key, Consumer<Object> handler) {
        handlers.put(key, handler);
        return this;
    }

    public void dispatch(Event event) {
        events.add(event);
    }

    public void run() {
        while (!(events.isEmpty() && Thread.interrupted())) {
            if (!events.isEmpty()) {
                Event event = events.pop();
                if (handlers.containsKey(event.key)) {
                    handlers.get(event.key).accept(event.data);
                } else {
                    System.err.println("No handler for key " + event.key);
                }
            }
        }
    }

    public void stop() {
        Thread.currentThread().interrupt();
    }

    public static void main(String[] args) {
        SimplestEventLoopExample eventLoop = new SimplestEventLoopExample();

        new Thread(() -> {
            delay(5_000);
            eventLoop.dispatch(new Event("stop", null));
        }).start();

        new Thread(() -> {
            delay(2500);
            eventLoop.dispatch(new Event("hello", "beautiful world"));
            delay(800);
            eventLoop.dispatch(new Event("hello", "beautiful universe"));
        }).start();

        eventLoop.dispatch(new Event("hello", "world!"));

        eventLoop
                .on("hello", s -> System.out.printf("Thread: %s, hello %s%n", Thread.currentThread().getName(), s))
                .on("stop", v -> eventLoop.stop())
                .run();

        System.out.println("Bye!");
    }

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

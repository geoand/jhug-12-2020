package gr.jhug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SimplestExecutorServiceExample {

    private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private volatile Thread mainThread;

    public SimplestExecutorServiceExample on(String key, Consumer<Object> handler) {
        handlers.put(key, handler);
        return this;
    }

    public void dispatch(Event event) {
        events.add(event);
    }

    public void run() {
        mainThread = Thread.currentThread();
        while (!(events.isEmpty() && Thread.interrupted())) {
            if (!events.isEmpty()) {
                Event event = events.pop();
                if (handlers.containsKey(event.key)) {
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            handlers.get(event.key).accept(event.data);
                        }
                    });
                } else {
                    System.err.println("No handler for key " + event.key);
                }
            }
        }
    }

    public void stop() {
        mainThread.interrupt();
        executorService.shutdown();
    }


    public static void main(String[] args) {
        SimplestExecutorServiceExample svc = new SimplestExecutorServiceExample();

        Thread t1 = new Thread(() -> {
            delay(5_000);
            svc.dispatch(new Event("stop", null));
        });
        t1.setDaemon(true);
        t1.start();


        Thread t2 = new Thread(() -> {
            delay(2_500);
            svc.dispatch(new Event("hello", "beautiful world"));
            delay(800);
            svc.dispatch(new Event("hello", "beautiful universe"));
        });
        t2.setDaemon(true);
        t2.start();

        svc.dispatch(new Event("hello", "world!"));

        svc.on("hello", s -> System.out.printf("Thread: %s, hello %s%n", Thread.currentThread().getName(), s))
                .on("stop", s -> svc.stop())
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

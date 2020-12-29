package gr.jhug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SimpleServerExecutorService {


    private final ConcurrentHashMap<String, Consumer<Context>> handlers = new ConcurrentHashMap<>();

    public void run() throws IOException {
        ExecutorService executorService = Executors.newFixedThreadPool(150);
        ServerSocket ss = new ServerSocket(1234);
        while (true) {
            Socket socket = ss.accept();
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try (socket;
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                         OutputStream out = socket.getOutputStream()) {
                        String line;
                        while (!((line = in.readLine()).toLowerCase().startsWith("quit"))) {
                            String command;
                            String data;
                            int index = line.indexOf(' ');
                            if (index == -1) {
                                command = line;
                                data = null;
                            } else {
                                command = line.substring(0, index);
                                data = line.substring(index + 1) + '\n';
                            }


                            if (handlers.containsKey(command)) {
                                handlers.get(command).accept(new SimpleContext(data, out));
                            } else {
                                System.err.println("No handler for key " + command);
                                out.write("Unknown command\n".getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        try {
                            ss.close();
                        } catch (Exception ignored){

                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    public SimpleServerExecutorService on(String key, Consumer<Context> handler) {
        handlers.put(key, handler);
        return this;
    }

    private static class SimpleContext implements Context {

        private final String data;
        private final OutputStream os;

        public SimpleContext(String data, OutputStream os) {
            this.data = data;
            this.os = os;
        }


        @Override
        public String getData() {
            return data;
        }

        @Override
        public void write(byte[] out) throws IOException {
            os.write(out);
        }
    }

    public static void main(String[] args) throws IOException {
        new SimpleServerExecutorService().on("echo", ctx -> {
            try {
                ctx.write(ctx.getData().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).run();
    }

}

package gr.jhug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class EchoClient {

    public static void main(String[] args) throws IOException, InterruptedException {
        Socket[] sockets = new Socket[200];
        for (int i = 0; i < sockets.length; i++) {
            sockets[i] = new Socket("localhost", 1234);
        }
        for (Socket socket : sockets) {
            new Thread(() -> {
                try {
                    for (int i=0; i < 10; i++) {
                        socket.getOutputStream().write("echo hello world".getBytes(StandardCharsets.UTF_8));
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException ignored) {

                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).start();
        }
        Thread.sleep(100_000);
    }
}

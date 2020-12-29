package gr.jhug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class SimpleServerSingleThreaded {

    private final ConcurrentHashMap<String, Consumer<Context>> handlers = new ConcurrentHashMap<>();
    private final Map<SocketChannel, Queue<ByteBuffer>> pendingData = new HashMap<>();


    public void run() throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(1234));
        ssc.configureBlocking(false);
        Selector selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            }
        }
    }

    public SimpleServerSingleThreaded on(String key, Consumer<Context> handler) {
        handlers.put(key, handler);
        return this;
    }

    public void accept(SelectionKey selectionKey) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) selectionKey.channel();
        SocketChannel sc = ssc.accept();
        pendingData.put(sc, new ConcurrentLinkedQueue<>());
        sc.configureBlocking(false);
        sc.register(selectionKey.selector(), SelectionKey.OP_READ);
    }

    //TODO: this is waaaaay to simplified and needs to be fixed
    public void read(SelectionKey selectionKey) throws IOException {
        SocketChannel sc = (SocketChannel) selectionKey.channel();
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int read = sc.read(buffer);
        if (read == -1) {
            pendingData.remove(sc);
            sc.close();
            return;
        }
        if (read > 0) {
            buffer.flip();
            String line = "" + Charset.defaultCharset().decode(buffer);
            String command;
            String data;
            int index = line.indexOf(' ');
            if (index == -1) {
                command = line;
                data = null;
            } else {
                command = line.substring(0, index);
                data = line.substring(index + 1);
            }
            if (command.toLowerCase().startsWith("quit")) {
                pendingData.remove(sc);
                sc.close();
                return;
            }

            Queue<ByteBuffer> queue = pendingData.get(sc);
            if (handlers.containsKey(command)) {
                handlers.get(command).accept(new SimpleContext(data, queue));
            } else {
                System.err.println("No handler for key " + command);
                queue.add(ByteBuffer.wrap("Unknown command\n".getBytes(StandardCharsets.UTF_8)));
            }

            selectionKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    public void write(SelectionKey selectionKey) throws IOException {
        SocketChannel sc = (SocketChannel) selectionKey.channel();
        Queue<ByteBuffer> queue = pendingData.get(sc);
        while (!queue.isEmpty()) {
            ByteBuffer buffer = queue.peek();
            int written = sc.write(buffer);
            if (written == -1) {
                sc.close();
                pendingData.remove(sc);
            }
            if (buffer.hasRemaining()) {
                return;
            }
            queue.remove();
        }
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    private static class SimpleContext implements Context {

        private final String data;
        private final Queue<ByteBuffer> queue;

        public SimpleContext(String data, Queue<ByteBuffer> queue) {
            this.data = data;
            this.queue = queue;
        }


        @Override
        public String getData() {
            return data;
        }

        @Override
        public void write(byte[] out) {
            queue.add(ByteBuffer.wrap(out));
        }
    }

    public static void main(String[] args) throws IOException {
        new SimpleServerSingleThreaded().on("echo", ctx -> {
            try {
                ctx.write(ctx.getData().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).run();
    }
}

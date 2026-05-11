package netpro2320603;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ChatServer {

    public static final String USER_LIST_PREFIX = "##USERLIST##:";
    public static final String MSG_PREFIX       = "##MSG##:";
    public static final String SYSTEM_SENDER    = "System";

    private final int port;
    private final ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private final AtomicLong msgCount  = new AtomicLong(0);
    private long lastStatTime = System.currentTimeMillis();

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("[Server] Listening on port " + port);

        ScheduledExecutorService stats = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Stats-Thread");
            t.setDaemon(true);
            return t;
        });
        stats.scheduleAtFixedRate(() -> {
            long now     = System.currentTimeMillis();
            double elapsed = (now - lastStatTime) / 1000.0;
            long count   = msgCount.getAndSet(0);
            lastStatTime = now;
            if (count > 0) {
                System.out.printf("[Server] Throughput: %.1f msg/s  (clients=%d)%n",
                        count / elapsed, clients.size());
            }
        }, 5, 5, TimeUnit.SECONDS);

        while (true) {
            Socket socket = serverSocket.accept();
            pool.submit(new ClientHandler(socket));
        }
    }

    private synchronized void broadcast(String line) {
        for (PrintWriter pw : clients.values()) {
            pw.println(line);
            pw.flush();
        }
        msgCount.incrementAndGet();
    }

    private synchronized void broadcastUserList() {
        String list = USER_LIST_PREFIX + String.join(",", clients.keySet());
        for (PrintWriter pw : clients.values()) {
            pw.println(list);
            pw.flush();
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private String username = "Unknown";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream(),  "UTF-8"));
                PrintWriter    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
            ) {
                String firstLine = in.readLine();
                if (firstLine == null) return;

                if (firstLine.startsWith("JOIN:")) {
                    username = firstLine.substring(5).trim();
                    if (username.isEmpty()) username = "Guest_" + (int)(Math.random() * 999);
                }

                clients.put(username, out);
                System.out.println("[Server] + " + username + "  (total=" + clients.size() + ")");
                broadcast(MSG_PREFIX + SYSTEM_SENDER + ":" + username + " joined the room.");
                broadcastUserList();

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(MSG_PREFIX)) {
                        broadcast(line);
                    }
                }

            } catch (IOException e) {
            } finally {
                clients.remove(username);
                System.out.println("[Server] - " + username + "  (total=" + clients.size() + ")");
                broadcast(MSG_PREFIX + SYSTEM_SENDER + ":" + username + " left the room.");
                broadcastUserList();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
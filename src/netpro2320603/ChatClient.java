package netpro2320603;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class ChatClient {

    private final String host;
    private final int    port;
    private final String username;

    private final Consumer<String> onMessage;
    private final Consumer<String> onUserList;
    private final Consumer<String> onStatus;

    private volatile Socket      socket;
    private volatile PrintWriter out;
    private volatile boolean     running = false;

    private static final int MAX_RETRIES    = 20;
    private static final int RETRY_DELAY_MS = 2000;

    public ChatClient(String host, int port, String username,
                      Consumer<String> onMessage,
                      Consumer<String> onUserList,
                      Consumer<String> onStatus) {
        this.host      = host;
        this.port      = port;
        this.username  = username;
        this.onMessage = onMessage;
        this.onUserList = onUserList;
        this.onStatus  = onStatus;
    }

    public void connect() {
        running = true;
        Thread t = new Thread(this::connectionLoop, "Client-" + username);
        t.setDaemon(true);
        t.start();
    }

    public void send(String text) {
        PrintWriter pw = out;
        if (pw != null) {
            pw.println(ChatServer.MSG_PREFIX + username + ":" + text);
            pw.flush();
        }
    }

    public void disconnect() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public String getUsername() { return username; }

    private void connectionLoop() {
        int retries = 0;
        while (running && retries < MAX_RETRIES) {
            try {
                onStatus.accept("Connecting...");
                socket = new Socket(host, port);
                socket.setKeepAlive(true);

                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                out.println("JOIN:" + username);
                out.flush();

                retries = 0;
                onStatus.accept("Connected");

                String line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith(ChatServer.USER_LIST_PREFIX)) {
                        onUserList.accept(line.substring(ChatServer.USER_LIST_PREFIX.length()));
                    } else if (line.startsWith(ChatServer.MSG_PREFIX)) {
                        onMessage.accept(line.substring(ChatServer.MSG_PREFIX.length()));
                    }
                }

            } catch (IOException e) {
                if (!running) break;
                retries++;
                onStatus.accept("Reconnecting... (" + retries + "/" + MAX_RETRIES + ")");
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { break; }
            } finally {
                out = null;
                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            }
        }
        if (running) onStatus.accept("Connection failed");
    }
}
package netpro2320603;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class BotManager {

    private static final String[] BOT_PHRASES = {
        "Interesting point!",
        "Could you clarify that?",
        "I completely agree.",
        "I did not quite follow that.",
        "That is great!",
        "I think there is another perspective here.",
        "Thanks for sharing that.",
        "That reminds me of something I read before.",
        "What does everyone else think?",
        "I agree with you on that.",
        "Let us continue the discussion.",
        "Very good point!"
    };

    public static final String[] BOT_NAMES = { "AITP1", "AITP2", "AITP3", "AITP4" };

    private final String host;
    private final int    port;
    private final List<ChatClient> bots = new ArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BotScheduler");
                t.setDaemon(true);
                return t;
            });

    private final Random rng = new Random();
    private final AtomicLong lastHumanMsg = new AtomicLong(0);
    private volatile ScheduledFuture<?> pendingReply = null;

    private boolean started = false;

    public BotManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void onHumanMessage() {
        lastHumanMsg.set(System.currentTimeMillis());
        ScheduledFuture<?> f = pendingReply;
        if (f != null && !f.isDone()) f.cancel(false);

        if (bots.isEmpty()) return;

        long delayMs = 1000 + rng.nextInt(1000);
        pendingReply = scheduler.schedule(this::sendBotMessage, delayMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void startBots(int count) {
        if (started) return;
        started = true;
        count = Math.max(1, Math.min(4, count));
        for (int i = 0; i < count; i++) {
            final String name = BOT_NAMES[i];
            ChatClient bot = new ChatClient(host, port, name,
                    msg    -> {},
                    list   -> {},
                    status -> System.out.println("[Bot:" + name + "] " + status));
            bot.connect();
            bots.add(bot);
        }
        System.out.println("[BotManager] Started " + count + " bot(s).");
    }

    public synchronized void stopBots() {
        for (ChatClient b : bots) b.disconnect();
        bots.clear();
        started = false;
    }

    private void sendBotMessage() {
        if (bots.isEmpty()) return;
        ChatClient bot    = bots.get(rng.nextInt(bots.size()));
        String     phrase = BOT_PHRASES[rng.nextInt(BOT_PHRASES.length)];
        bot.send(phrase);
    }
}
# TCPChat603JFX

A self-contained, real-time desktop chat application built entirely in Java and JavaFX. The application hosts its own embedded TCP server on startup, requiring no external infrastructure. Two human users can chat simultaneously through a split-pane interface, with an optional bot system that injects automated responses during conversation.

The visual theme draws from 19th-century naturalist illustration: parchment tones, serif typography, and engraved-style borders throughout.

---

## Table of Contents

- [Architecture](#architecture)
- [Protocol](#protocol)
- [Features](#features)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Setup](#setup)
- [Running the Application](#running-the-application)
- [Bot Manager](#bot-manager)
- [Technical Notes](#technical-notes)
- [Network Configuration](#network-configuration)
- [Academic Information](#academic-information)

---

## Architecture

The application follows a client-server model where both the server and clients coexist in a single Java process. On launch, `Main` starts a daemon thread that binds `ChatServer` to port 20603, then waits 300 ms before the JavaFX application thread opens the graphical window. This self-hosting design means the user never manages a separate server process.

```
┌─────────────────────────────────────────────────────┐
│                  Java Process                       │
│                                                     │
│   ┌─────────────┐        ┌──────────────────────┐   │
│   │ ChatServer  │◄──────►│ ChatClient (Left)    │   │
│   │  port 20603 │◄──────►│ ChatClient (Right)   │   │
│   │             │◄──────►│ ChatClient (Bot x4)  │   │
│   └─────────────┘        └──────────────────────┘   │
│                                                     │
│   ┌─────────────────────────────────────────────┐   │
│   │          ClientController (JavaFX)          │   │
│   └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

| Class | Responsibility |
|---|---|
| `Main` | Application entry point. Starts the server daemon thread then launches JavaFX. |
| `ChatServer` | TCP server on port 20603. Uses a `CachedThreadPool` to handle each client on a dedicated thread. Manages broadcast and user-list updates. |
| `ChatClient` | Socket model used by both human panes and bots. Handles the JOIN handshake, message sending, and the reconnection loop. |
| `ClientController` | JavaFX FXML controller. Manages both chat panes, the online users sidebar, the startup dialog, and the bot control panel. |
| `BotManager` | Creates bot `ChatClient` instances and schedules a single reply 1 to 2 seconds after the last human message. |

---

## Protocol

All messages are plain UTF-8 text lines over TCP. Three prefixes govern every exchange.

| Direction | Format | Description |
|---|---|---|
| Client to Server | `JOIN:<username>` | First line sent after a connection is established. Registers the user on the server. |
| Client to Server | `##MSG##:<username>:<text>` | Sends a chat message to be broadcast to all connected clients. |
| Server to Client | `##MSG##:<username>:<text>` | Delivers a broadcast chat message. |
| Server to Client | `##USERLIST##:<user1>,<user2>,...` | Pushes the full participant list whenever a client joins or leaves. |

System events such as join and leave notifications use the reserved sender name `System` and travel through the standard `##MSG##` channel. If a `JOIN` line carries an empty username, the server substitutes a `Guest_` fallback with a random three-digit suffix.

---

## Features

- Embedded TCP server that starts automatically and requires no configuration
- Two simultaneous human chat panes in a horizontally resizable split view
- Live online users sidebar updated in real time on every join and leave event
- Message bubble rendering with distinct alignment and styling for own and peer messages
- System event messages rendered separately from conversation content
- Pre-chat dialog prompting both users to enter display names before connecting
- Bot manager supporting 1 to 4 automated participants
- Bots respond naturally with a randomised 1 to 2 second delay after human silence
- Auto-reconnect logic with up to 20 retry attempts and a 2-second interval
- Leave and Reconnect controls per pane, without requiring an application restart
- Server throughput stats printed to the console every 5 seconds when traffic is active
- Full UTF-8 support on all streams
- No external runtime dependencies beyond the JDK and JavaFX SDK

---

## Project Structure

```
netpro2320603/
├── src/
│   └── netpro2320603/
│       ├── Main.java               Entry point; starts server thread and launches JavaFX
│       ├── ChatServer.java         TCP server; thread-per-client model; broadcast logic
│       ├── ChatClient.java         Socket model; JOIN handshake; reconnection loop
│       ├── ClientController.java   JavaFX FXML controller; all UI logic
│       ├── BotManager.java         Bot creation and scheduled reply logic
│       ├── chat.fxml               FXML layout definition for the main window
│       └── style.css               Full JavaFX CSS theme
├── nbproject/
│   ├── project.xml
│   └── project.properties
├── build.xml
└── README.md
```

---

## Requirements

- Java 8 or higher
- JavaFX (bundled in JDK 8; separate SDK required for JDK 11 and above)
- NetBeans IDE (recommended) or any IDE that supports Ant builds

---

## Setup

### Opening in NetBeans

1. Go to **File > Open Project** and select the `netpro2320603` folder.
2. NetBeans detects the `nbproject/` directory and configures the project automatically.
3. Press **F6** to build and run.

### JDK 8

No additional configuration is required. JavaFX is included with the JDK.

### JDK 11 and Above

1. Download the JavaFX SDK from [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx) and extract it.
2. In NetBeans, open **Project Properties > Libraries > Modulepath** and add all `.jar` files from the `javafx-sdk/lib/` directory.
3. Open **Project Properties > Run > VM Options** and add the following, replacing the path with your actual JavaFX SDK location:

```
--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
```

4. Press **F6** to run.

---

## Running the Application

On launch, the following happens in sequence:

1. The embedded TCP server binds to port 20603 on a background daemon thread.
2. After a 300 ms startup delay, the JavaFX window opens and presents the name dialog.
3. Both users enter their display names and click **Start Chatting**.
4. The left pane connects immediately as the first user.
5. Clicking **Add User** opens the right pane and connects a second user on the same server.

Both panes receive all messages in real time. Either pane can leave and reconnect independently without affecting the other.

---

## Bot Manager

The bot manager injects automated participants that respond to human conversation.

1. In the left sidebar, select the number of bots (1 to 4) from the dropdown.
2. Click **Launch Bots**.
3. Send a message from either human pane.
4. One randomly selected bot replies 1 to 2 seconds after the last human message.

Bots connect to the server exactly as human clients do. They appear in the online users sidebar with an italic `[Bot]` prefix. Only one bot response is ever scheduled at a time. Any new human message cancels the pending reply and reschedules it, preventing flooding during rapid conversation.

---

## Technical Notes

**Thread safety.** The server maintains its client map in a `ConcurrentHashMap`. Both `broadcast` and `broadcastUserList` are `synchronized` on the server instance, ensuring that no partial user-list snapshot is delivered to any client during a concurrent membership change.

**JavaFX thread model.** JavaFX requires all scene-graph modifications to run on its application thread. Every socket callback that touches a UI element, including appending messages, updating status labels, and refreshing the users list, is dispatched through `Platform.runLater`.

**Reconnection.** `ChatClient` runs its connection logic inside a `connectionLoop` on a daemon background thread. On any `IOException`, the loop increments a retry counter, emits a status update, sleeps for 2 seconds, and retries. After 20 failed attempts it emits a terminal status and stops. The `disconnect` method closes the socket from outside the loop, causing any blocked `readLine` to unblock and the loop to exit cleanly.

**Bot scheduling.** `BotManager` holds a single `volatile ScheduledFuture` for the pending reply. Each call to `onHumanMessage` cancels the existing future before scheduling a new one. This guarantees at most one pending reply at any time, regardless of how quickly messages arrive.

**Daemon threads.** All background threads, including the server thread pool, client connection loops, bot threads, and the scheduler, are marked as daemon threads. The JVM terminates them automatically when the JavaFX window is closed, with no explicit shutdown hook required.

**Throughput logging.** A dedicated `ScheduledExecutorService` on the server samples an `AtomicLong` message counter every 5 seconds and prints the rate to the console, then resets the counter atomically. Output is suppressed during idle periods.

---

## Network Configuration

| Parameter | Value |
|---|---|
| Protocol | TCP |
| Server port | 20603 |
| Character encoding | UTF-8, explicit on all streams |
| Socket option | `SO_KEEPALIVE` enabled per client socket |
| Max reconnect attempts | 20 |
| Reconnect interval | 2000 ms |
| Bot reply delay | 1000 to 2000 ms, randomised per message |
| Bot count range | 1 to 4 |
| Throughput log interval | Every 5 seconds, server console only |
| Thread model | Daemon threads throughout |

---

## Academic Information

| Field | Detail |
|---|---|
| University | Borg El Arab Technological University |
| Faculty | Faculty of Industrial Energy Technology |
| Department | Information Technology |
| Specialization | Software Technology |
| Course | Network Programming |
| Term | Second Term, Academic Year 2025 / 2026 |
| Instructor | Dr. Aya Ibrahim |
| Student | Maryam Eid Abd Elsalam |
| Student ID | 2320603 |
| Assigned Port | 20603 |

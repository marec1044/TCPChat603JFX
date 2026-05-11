package netpro2320603;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class ClientController implements Initializable {

    private static final String HOST = "localhost";
    private static final int    PORT = 20603;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Label leftUsernameField;
    @FXML private ListView<ChatMessage> leftChatList;
    @FXML private TextField leftInputField;
    @FXML private Button    leftSendBtn;
    @FXML private Label     leftStatusLabel;
    @FXML private Label     leftAvatarLabel;

    @FXML private Label rightUsernameField;
    @FXML private ListView<ChatMessage> rightChatList;
    @FXML private TextField rightInputField;
    @FXML private Button    rightSendBtn;
    @FXML private Label     rightStatusLabel;
    @FXML private Label     rightAvatarLabel;
    @FXML private VBox      rightPane;

    @FXML private ListView<String>   onlineUsersList;
    @FXML private Label              onlineCountLabel;
    @FXML private ComboBox<Integer>  botCountCombo;
    @FXML private Button             addBotsBtn;
    @FXML private Label              botStatusLabel;
    @FXML private SplitPane          mainSplit;

    private ChatClient leftClient;
    private ChatClient rightClient;
    private BotManager botManager;

    // ── These hold the CURRENT active username for each pane.
    // They are updated whenever a reconnection happens, and used
    // by appendMsg to decide own vs peer bubble alignment.
    private volatile String leftActiveUsername  = "Me";
    private volatile String rightActiveUsername = "Peer";

    private final ObservableList<ChatMessage> leftMessages  = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> rightMessages = FXCollections.observableArrayList();
    private final ObservableList<String>      onlineUsers   = FXCollections.observableArrayList();

    private boolean rightPaneVisible = false;
    private final List<String> botNames = Arrays.asList(BotManager.BOT_NAMES);

    public enum MsgType { CHAT, SYSTEM }

    public static class ChatMessage {
        public final String  sender;
        public final String  body;
        public final String  time;
        public final MsgType type;
        public final boolean isOwn;

        ChatMessage(String sender, String body, String time, MsgType type, boolean isOwn) {
            this.sender = sender;
            this.body   = body;
            this.time   = time;
            this.type   = type;
            this.isOwn  = isOwn;
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        leftChatList.setItems(leftMessages);
        rightChatList.setItems(rightMessages);
        onlineUsersList.setItems(onlineUsers);

        leftChatList.setCellFactory(lv  -> new MessageCell());
        rightChatList.setCellFactory(lv -> new MessageCell());

        onlineUsersList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    getStyleClass().removeAll("user-cell-bot");
                    return;
                }
                boolean isBot = botNames.contains(name);
                setText(isBot ? "[Bot] " + name : name);
                if (isBot) {
                    if (!getStyleClass().contains("user-cell-bot"))
                        getStyleClass().add("user-cell-bot");
                } else {
                    getStyleClass().removeAll("user-cell-bot");
                }
            }
        });

        botCountCombo.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        botCountCombo.setValue(1);

        leftInputField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) onLeftSend();
        });
        rightInputField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) onRightSend();
        });

        Platform.runLater(this::showNameDialog);
    }

    // ── Startup dialog ─────────────────────────────────────────────────────

    private void showNameDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("TCPChat603JFX");
        dialog.setHeaderText("Enter user names to start chatting");

        DialogPane dp = dialog.getDialogPane();
        dp.getStyleClass().add("name-dialog");
        try { dp.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); }
        catch (Exception ignored) {}

        TextField myNameField   = new TextField("Me");
        TextField peerNameField = new TextField("Peer");
        myNameField.getStyleClass().add("dialog-field");
        peerNameField.getStyleClass().add("dialog-field");

        Label myLbl   = new Label("Your name:");
        Label peerLbl = new Label("Other person's name:");
        myLbl.getStyleClass().add("dialog-label");
        peerLbl.getStyleClass().add("dialog-label");

        VBox content = new VBox(10, myLbl, myNameField, peerLbl, peerNameField);
        content.setPadding(new Insets(20, 20, 10, 20));
        dp.setContent(content);
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.setText("Start Chatting");
        okBtn.getStyleClass().add("dialog-ok-btn");
        ((Button) dp.lookupButton(ButtonType.CANCEL)).setText("Cancel");

        dialog.showAndWait().ifPresent(result -> {
            // Read names from the DIALOG fields (not the header fields yet)
            String myName   = myNameField.getText().trim();
            String peerName = peerNameField.getText().trim();
            if (myName.isEmpty())   myName   = "Me";
            if (peerName.isEmpty()) peerName = "Peer";

            // Push to header fields (triggers avatar listener, not reconnect)
            leftUsernameField.setText(myName);
            rightUsernameField.setText(peerName);

            // Connect only if OK was pressed; also connect on CANCEL with defaults
            // Either way, connect left pane once with the resolved name
            connectLeft(myName);
        });
    }

    // ── Avatar helper ──────────────────────────────────────────────────────

    private void updateAvatar(Label label, String name) {
        String letter = (name == null || name.isEmpty()) ? "?"
                      : name.substring(0, 1).toUpperCase();
        Platform.runLater(() -> label.setText(letter));
    }

    // ── Connect helpers ────────────────────────────────────────────────────
    // These set leftActiveUsername / rightActiveUsername so that appendMsg
    // always compares against the CURRENT username, not a stale closure.

    private void connectLeft(String name) {
        if (name == null || name.isEmpty()) name = "Me";
        leftActiveUsername = name;
        updateAvatar(leftAvatarLabel, name);

        leftClient = new ChatClient(HOST, PORT, name,
            msg -> appendMsg(leftMessages,  leftChatList,  msg, true),
            csv -> updateUserList(csv),
            st  -> Platform.runLater(() -> leftStatusLabel.setText(st))
        );
        leftClient.connect();
    }

    private void connectRight(String name) {
        if (name == null || name.isEmpty()) name = "Peer";
        rightActiveUsername = name;
        updateAvatar(rightAvatarLabel, name);

        rightClient = new ChatClient(HOST, PORT, name,
            msg -> appendMsg(rightMessages, rightChatList, msg, false),
            csv -> updateUserList(csv),
            st  -> Platform.runLater(() -> rightStatusLabel.setText(st))
        );
        rightClient.connect();
    }

    // ── FXML actions ───────────────────────────────────────────────────────

    /**
     * Reconnect button for left pane.
     * Disconnects old client, reconnects with the name currently in the field.
     * This is the ONLY place that triggers a reconnection — typing alone does not.
     */
    @FXML private void onLeftConnect() {
        if (leftClient != null) { leftClient.disconnect(); leftClient = null; }
        leftMessages.clear();
        connectLeft(leftUsernameField.getText().trim());
    }

    /**
     * Reconnect button for right pane.
     */
    @FXML private void onRightConnect() {
        if (rightClient != null) { rightClient.disconnect(); rightClient = null; }
        rightMessages.clear();
        connectRight(rightUsernameField.getText().trim());
    }

    @FXML private void onLeftLeave() {
        if (leftClient != null) { leftClient.disconnect(); leftClient = null; }
        Platform.runLater(() -> {
            leftStatusLabel.setText("Disconnected");
            leftMessages.clear();
        });
    }

    @FXML private void onLeftSend() {
        String text = leftInputField.getText().trim();
        if (text.isEmpty() || leftClient == null) return;
        leftClient.send(text);
        leftInputField.clear();
        if (botManager != null) botManager.onHumanMessage();
    }

    @FXML private void onRightSend() {
        String text = rightInputField.getText().trim();
        if (text.isEmpty() || rightClient == null) return;
        rightClient.send(text);
        rightInputField.clear();
        if (botManager != null) botManager.onHumanMessage();
    }

    @FXML private void onAddUser() {
        if (rightPaneVisible) return;
        rightPane.setManaged(true);
        rightPane.setVisible(true);
        mainSplit.setDividerPositions(0.5);
        rightPaneVisible = true;
        addBotsBtn.setDisable(true);
        connectRight(rightUsernameField.getText().trim());
    }

    @FXML private void onRemoveUser() {
        if (!rightPaneVisible) return;
        if (rightClient != null) { rightClient.disconnect(); rightClient = null; }
        rightPane.setVisible(false);
        rightPane.setManaged(false);
        mainSplit.setDividerPositions(1.0);
        rightPaneVisible = false;
        addBotsBtn.setDisable(false);
        rightMessages.clear();
    }

    @FXML private void onAddBots() {
        if (botManager != null) botManager.stopBots();
        int count = botCountCombo.getValue() == null ? 1 : botCountCombo.getValue();
        botManager = new BotManager(HOST, PORT);
        botManager.startBots(count);
        Platform.runLater(() ->
            botStatusLabel.setText(count + " bot" + (count > 1 ? "s" : "") + " active"));
    }

    // ── Message rendering ──────────────────────────────────────────────────
    // isLeftPane: true = compare against leftActiveUsername, false = right

    private void appendMsg(ObservableList<ChatMessage> list,
                           ListView<ChatMessage> view,
                           String raw,
                           boolean isLeftPane) {
        Platform.runLater(() -> {
            String time = LocalTime.now().format(TIME_FMT);
            ChatMessage cm;

            int colon = raw.indexOf(':');
            if (colon >= 0) {
                String sender = raw.substring(0, colon);
                String body   = raw.substring(colon + 1);

                if (sender.equals("System") || sender.equals("النظام")) {
                    cm = new ChatMessage("", body, time, MsgType.SYSTEM, false);
                } else {
                    // Always read the current active username at render time
                    String myName = isLeftPane ? leftActiveUsername : rightActiveUsername;
                    boolean isOwn = sender.equalsIgnoreCase(myName);
                    cm = new ChatMessage(sender, body, time, MsgType.CHAT, isOwn);
                }
            } else {
                cm = new ChatMessage("", raw, time, MsgType.SYSTEM, false);
            }

            list.add(cm);
            view.scrollTo(list.size() - 1);
        });
    }

    private void updateUserList(String csv) {
        Platform.runLater(() -> {
            onlineUsers.clear();
            if (csv != null && !csv.trim().isEmpty()) {
                for (String name : csv.split(",")) {
                    String t = name.trim();
                    if (!t.isEmpty()) onlineUsers.add(t);
                }
            }
            onlineCountLabel.setText(String.valueOf(onlineUsers.size()));
        });
    }

    // ── Message cell ───────────────────────────────────────────────────────

    static class MessageCell extends ListCell<ChatMessage> {

        private final HBox  row       = new HBox();
        private final VBox  bubble    = new VBox(3);
        private final Label senderLbl = new Label();
        private final Label bodyLbl   = new Label();
        private final Label timeLbl   = new Label();
        private final Label sysLbl    = new Label();

        MessageCell() {
            super();
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");

            senderLbl.getStyleClass().add("msg-sender-name");
            bodyLbl.getStyleClass().add("msg-body");
            timeLbl.getStyleClass().add("msg-timestamp");
            sysLbl.getStyleClass().add("system-msg-label");

            bodyLbl.setWrapText(true);
            bodyLbl.setMinWidth(0);

            timeLbl.setMaxWidth(Double.MAX_VALUE);
            timeLbl.setAlignment(Pos.BOTTOM_RIGHT);

            bubble.getChildren().addAll(senderLbl, bodyLbl, timeLbl);
            bubble.setMaxWidth(380);
            bubble.setMinWidth(80);

            row.setPadding(new Insets(4, 14, 4, 14));
            row.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(ChatMessage item, boolean empty) {
            super.updateItem(item, empty);
            row.getChildren().clear();
            bubble.getStyleClass().removeAll("msg-bubble-own", "msg-bubble-peer");

            if (empty || item == null) { setGraphic(null); return; }

            if (item.type == MsgType.SYSTEM) {
                sysLbl.setText(item.body);
                HBox centred = new HBox(sysLbl);
                centred.setAlignment(Pos.CENTER);
                centred.setPadding(new Insets(5, 14, 5, 14));
                centred.setMaxWidth(Double.MAX_VALUE);
                setGraphic(centred);
                return;
            }

            senderLbl.setText(item.sender);
            bodyLbl.setText(item.body);
            timeLbl.setText(item.time);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            if (item.isOwn) {
                bubble.getStyleClass().add("msg-bubble-own");
                row.setAlignment(Pos.CENTER_RIGHT);
                row.getChildren().addAll(spacer, bubble);
            } else {
                bubble.getStyleClass().add("msg-bubble-peer");
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(bubble, spacer);
            }

            setGraphic(row);
        }
    }
}
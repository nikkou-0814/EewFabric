package nagoya.nikkou;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class eewfabric implements ModInitializer {

    private OkHttpClient client;
    private MinecraftServer server;
    private WebSocket eewWebSocket;
    private WebSocket p2pWebSocket;

    @Override
    public void onInitialize() {
        client = new OkHttpClient();
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;
        connectEEWWebSocket();
        connectP2PWebSocket();
    }

    private void onServerStopping(MinecraftServer server) {
        closeWebSocket(eewWebSocket, "EEW");
        closeWebSocket(p2pWebSocket, "P2P");
    }

    private void closeWebSocket(WebSocket webSocket, String type) {
        if (webSocket != null) {
            webSocket.close(1000, "Server stopping");
            System.out.println(type + " Socketがサーバー停止のためにクローズされました。");
            broadcastToChat(type + " Socketがサーバー停止のためにクローズされました。");
        }
    }

    private void connectEEWWebSocket() {
        Request request = new Request.Builder()
                .url("wss://ws-api.wolfx.jp/jma_eew")
                .build();
        eewWebSocket = client.newWebSocket(request, new EEWWebSocketListener());
    }

    private void connectP2PWebSocket() {
        Request request = new Request.Builder()
                .url("https://api.p2pquake.net/v2/ws")
                .build();
        p2pWebSocket = client.newWebSocket(request, new P2PWebSocketListener());
    }

    private class EEWWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("EEW Socketが接続されました！");
            broadcastToChat("EEW Socketが接続されました！");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            System.out.println("Received EEW message: " + text);
            JSONObject jsonObject = new JSONObject(text);
            if ("heartbeat".equals(jsonObject.optString("type"))) {
                webSocket.send("ping");
                return;
            }
            if ("pong".equals(jsonObject.optString("type"))) {
                return;
            }
            String message = createEEWMessage(jsonObject);
            broadcastToChat(message);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            handleWebSocketClosing(webSocket, code, reason, "EEW");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            handleWebSocketFailure(webSocket, t, "EEW");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            handleWebSocketClosed(webSocket, code, reason, "EEW");
        }
    }

    private class P2PWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("P2P Socketが接続されました！");
            broadcastToChat("P2P Socketが接続されました！");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            System.out.println("Received P2P message: " + text);
            JSONObject jsonObject = new JSONObject(text);
            String type = jsonObject.getJSONObject("issue").getString("type");
            String message = createP2PMessage(jsonObject, type);
            if (message != null) {
                broadcastToChat(message);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            handleWebSocketClosing(webSocket, code, reason, "P2P");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            handleWebSocketFailure(webSocket, t, "P2P");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            handleWebSocketClosed(webSocket, code, reason, "P2P");
        }
    }

    private void handleWebSocketClosing(WebSocket webSocket, int code, String reason, String type) {
        System.out.println(type + " WebSocket connection closing: " + reason);
        broadcastToChat(type + " WebSocket connection closing: " + reason);
    }

    private void handleWebSocketFailure(WebSocket webSocket, Throwable t, String type) {
        System.err.println(type + " Socketエラー: " + t.getMessage());
        broadcastToChat(type + " Socketエラー: " + t.getMessage());
        reconnectWebSocket(type + " Socketエラー: " + t.getMessage(), type);
    }

    private void handleWebSocketClosed(WebSocket webSocket, int code, String reason, String type) {
        System.out.println(type + " Socketがクローズされました: " + reason);
        broadcastToChat(type + " Socketがクローズされました: " + reason);
        reconnectWebSocket(type + " Socketがクローズされました: " + reason, type);
    }

    private String createEEWMessage(JSONObject jsonObject) {
        StringBuilder message = new StringBuilder();
        message.append(jsonObject.getString("Title")).append(" 第").append(jsonObject.getInt("Serial")).append("報\n\n");
        message.append("震源地: ").append(jsonObject.getString("Hypocenter")).append("\n");
        message.append("推定最大震度: ").append(jsonObject.getString("MaxIntensity")).append("\n");
        message.append("マグニチュード: ").append(jsonObject.getDouble("Magunitude")).append("\n");
        message.append("深さ: ").append(jsonObject.getInt("Depth")).append("km\n");
        message.append("発生時刻: ").append(jsonObject.getString("OriginTime")).append("\n");
        message.append("海域地震: ").append(jsonObject.getBoolean("isSea") ? "はい" : "いいえ").append("\n");
        message.append("仮定震源要素: ").append(jsonObject.getBoolean("isAssumption") ? "はい" : "いいえ").append("\n");
        message.append("キャンセル: ").append(jsonObject.getBoolean("isCancel") ? "はい" : "いいえ").append("\n");
        message.append("最終報: ").append(jsonObject.getBoolean("isFinal") ? "はい" : "いいえ").append("\n");
        message.append("警報: ").append(jsonObject.getBoolean("isWarn") ? "発表中" : "発表なし").append("\n");
        message.append("訓練報: ").append(jsonObject.getBoolean("isTraining") ? "はい" : "いいえ").append("\n");
        message.append("\n気象庁原電文: ").append(jsonObject.getString("OriginalText"));

        return message.toString();
    }

    private String createP2PMessage(JSONObject jsonObject, String type) {
        switch (type) {
            case "ScalePrompt":
                return createScalePromptMessage(jsonObject);
            case "DetailScale":
                return createDetailScaleMessage(jsonObject);
            case "Destination":
                return createDestinationMessage(jsonObject);
            default:
                System.out.println("Unhandled message type: " + type);
                return null;
        }
    }

    private String createScalePromptMessage(JSONObject jsonObject) {
        StringBuilder message = new StringBuilder();
        message.append("震度速報\n\n");

        String time = jsonObject.getJSONObject("earthquake").getString("time");
        LocalDateTime dateTime = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        int maxScale = jsonObject.getJSONObject("earthquake").getInt("maxScale");
        String domesticTsunami = jsonObject.getJSONObject("earthquake").getString("domesticTsunami");
        message.append(dateTime.format(DateTimeFormatter.ofPattern("dd日 HH時mm分"))).append("頃").append("\n").append("最大震度").append(convertScale(maxScale)).append("を観測する地震が発生しました").append("\n").append(convertTsunamiInfo(domesticTsunami)).append("\n\n");

        message.append("\n震度情報:\n");
        JSONArray points = jsonObject.getJSONArray("points");
        for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.getJSONObject(i);
            message.append(point.getString("pref")).append(" ")
                    .append(point.getString("addr")).append(": ")
                    .append(convertScale(point.getInt("scale"))).append("\n");
        }

        return message.toString();
    }

    private String createDetailScaleMessage(JSONObject jsonObject) {
        StringBuilder message = new StringBuilder();
        message.append("震源・震度に関する情報\n\n");

        String time = jsonObject.getJSONObject("earthquake").getString("time");
        LocalDateTime dateTime = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        int maxScale = jsonObject.getJSONObject("earthquake").getInt("maxScale");
        String domesticTsunami = jsonObject.getJSONObject("earthquake").getString("domesticTsunami");
        String hypocenterName = jsonObject.getJSONObject("earthquake").getJSONObject("hypocenter").getString("name");
        message.append(dateTime.format(DateTimeFormatter.ofPattern("dd日 HH時mm分"))).append("頃").append("\n").append(hypocenterName.isEmpty() ? "不明" : hypocenterName).append("で最大震度").append(convertScale(maxScale)).append("を観測する地震が発生しました").append("\n").append(convertTsunamiInfo(domesticTsunami)).append("\n\n");

        message.append("震源地: ").append(hypocenterName.isEmpty() ? "不明" : hypocenterName).append("\n");

        JSONObject hypocenter = jsonObject.getJSONObject("earthquake").getJSONObject("hypocenter");
        message.append("マグニチュード: ").append("M").append(hypocenter.getDouble("magnitude")).append("\n");
        message.append("深さ: ").append(hypocenter.getInt("depth")).append("km");

        return message.toString();
    }

    private String createDestinationMessage(JSONObject jsonObject) {
        StringBuilder message = new StringBuilder();
        message.append("震源に関する情報\n\n");

        String time = jsonObject.getJSONObject("earthquake").getString("time");
        LocalDateTime dateTime = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        String domesticTsunami = jsonObject.getJSONObject("earthquake").getString("domesticTsunami");
        JSONObject hypocenter = jsonObject.getJSONObject("earthquake").getJSONObject("hypocenter");
        String hypocenterName = hypocenter.getString("name");
        message.append(dateTime.format(DateTimeFormatter.ofPattern("dd日 HH時mm分"))).append("頃、").append(hypocenterName.isEmpty() ? "不明" : hypocenterName).append("で地震がありました。").append("\n").append(convertTsunamiInfo(domesticTsunami)).append("\n\n");

        message.append("震源地: ").append(hypocenterName.isEmpty() ? "不明" : hypocenterName).append("\n");

        message.append("マグニチュード: ").append("M").append(hypocenter.getDouble("magnitude")).append("\n");
        message.append("深さ: ").append(hypocenter.getInt("depth")).append("km");

        return message.toString();
    }

    private String convertScale(int scale) {
        switch (scale) {
            case 10: return "1";
            case 20: return "2";
            case 30: return "3";
            case 40: return "4";
            case 45: return "5弱";
            case 50: return "5強";
            case 55: return "6弱";
            case 60: return "6強";
            case 70: return "7";
            default: return "不明";
        }
    }

    private String convertTsunamiInfo(String tsunami) {
        switch (tsunami) {
            case "None": return "この地震による津波の心配はありません";
            case "Unknown": return "この地震による津波の有無は不明です";
            case "Checking": return "この地震による津波影響は現在調査中です";
            case "NonEffective": return "この地震により若干の海面変動が予想されますが、被害の心配はありません";
            case "Watch": return "この地震により津波注意報等を発表しています";
            case "Warning": return "この地震により津波警報等を発表しています";
            default: return tsunami;
        }
    }

    private void reconnectWebSocket(String reason, String type) {
        try {
            Thread.sleep(5000); // 5秒待つ
            System.out.println(type + " Socketが切断されました。5秒後に再接続します:" + reason);
            broadcastToChat(type + " Socketが切断されました。5秒後に再接続します:" + reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if ("EEW".equals(type)) {
            connectEEWWebSocket();
        } else if ("P2P".equals(type)) {
            connectP2PWebSocket();
        }
    }

    private void broadcastToChat(String message) {
        if (server != null && server.getPlayerManager() != null) {
            server.getPlayerManager().broadcast(Text.of(message), false);
        }
    }
}

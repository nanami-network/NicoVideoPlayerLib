package xyz.n7mn.dev.video;

import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.n7mn.dev.NicoVideoAudioSourceManager;
import xyz.n7mn.dev.video.aes.NicoVideoCryptTsM3uStreamAudioTrack;
import xyz.n7mn.dev.video.normal.NicoVideoTsM3uStreamAudioTrack;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class NicoVideoAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NicoAudioTrack.class);
    protected final NicoVideoAudioSourceManager sourceManager;

    private String data, sessionId;
    private boolean running;

    /**
     * @param trackInfo Track info
     */
    public NicoVideoAudioTrack(AudioTrackInfo trackInfo, NicoVideoAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            NicoCryptStatus cryptKey = loadUrl(httpInterface);
            //Encoded Urls
            //通常は一回URLにアクセスする必要がある 安全にしたい場合はそうしてほしい
            //一回のHTTPアクセスをなくすという利点も有るため一概にデメリットというわけでもない。
            String playbackUrl = cryptKey.url.replaceFirst("master\\.m3u8", "1/ts/playlist.m3u8");
            log.debug("Starting KeepAlive from SessionId: {}", sessionId);
            startKeepAlive();
            log.debug("Starting NicoNico track from URL: {}", playbackUrl);
            if (cryptKey.licenced) {
                processDelegate(new NicoVideoCryptTsM3uStreamAudioTrack(trackInfo, httpInterface, playbackUrl), executor);
            } else {
                processDelegate(new NicoVideoTsM3uStreamAudioTrack(trackInfo, httpInterface, playbackUrl), executor);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public void startKeepAlive() {
        running = true;
        new Thread(() -> {
            try {
                while (running) {
                    Thread.sleep(40000L);
                    //動画のリンクを維持するために実行します！
                    executeKeepAlive();
                }
            } catch (Exception e) {
                e.printStackTrace();
                stopKeepAlive();
            }
        }).start();
    }

    public void executeKeepAlive() throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            checkStatusCode(httpInterface.execute(new HttpOptions("https://api.dmc.nico/api/sessions/" + sessionId + "?_format=json&_method=PUT")));

            HttpPost post = new HttpPost("https://api.dmc.nico/api/sessions/" + sessionId + "?_format=json&_method=PUT");
            post.setEntity(new StringEntity(data));
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");
            post.setHeader("User-Agent", "NicoUtils/1.0 (https://github.com/KoutaChan/NicoUtils)");
            this.data = new JSONObject(EntityUtils.toString(checkStatusCode(httpInterface.execute(post)).getEntity(), StandardCharsets.UTF_8.name())).getJSONObject("data").toString();
        }
    }

    public void stopKeepAlive() {
        if (running) {
            running = false;
        }
    }

    public NicoCryptStatus loadUrl(HttpInterface httpInterface) throws IOException {
        CloseableHttpResponse response = checkStatusCode(httpInterface.execute(new HttpGet(trackInfo.uri)));

        Document document = Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
        JSONObject data = new JSONObject(document.getElementById("js-initial-watch-data").attr("data-api-data"));
        JSONObject delivery = data.getJSONObject("media").getJSONObject("delivery");
        final boolean licenced = delivery.optJSONObject("encryption") != null;
        // ここから、許可を得ても遅くないっぽいのでここで対応する！
        // これを実行しないと偽鍵が返されるので留意！
        if (licenced) {
            this.requestCryptKey(httpInterface, delivery.getString("trackingId"));
        }
        String session = createSession(delivery, licenced);
        HttpPost post = new HttpPost("https://api.dmc.nico/api/sessions?_format=json");
        post.setEntity(new StringEntity(session));
        post.setHeader("Accept", "application/json");
        post.setHeader("User-Agent", "NicoUtils/1.0 (https://github.com/KoutaChan/NicoUtils)");
        JSONObject callback = new JSONObject(EntityUtils.toString(checkStatusCode(httpInterface.execute(post)).getEntity(), StandardCharsets.UTF_8.name())).getJSONObject("data");
        JSONObject videoSessions = callback.getJSONObject("session");
        this.sessionId = videoSessions.getString("id");
        this.data = callback.toString();
        return new NicoCryptStatus(videoSessions.getString("content_uri"), licenced);
    }

    @SneakyThrows
    public void requestCryptKey(HttpInterface httpInterface, String trackingId) throws IOException {
        String encodedUrl = "https://nvapi.nicovideo.jp/v1/2ab0cbaa/watch?t=" + URLEncoder.encode(trackingId, StandardCharsets.UTF_8);
        HttpOptions options = new HttpOptions(encodedUrl);
        options.setHeader("Access-Control-Request-Headers", "x-frontend-id,x-frontend-version");
        options.setHeader("Access-Control-Request-Method", "GET");
        options.setHeader("Referer", this.trackInfo.uri);
        options.setHeader("Origin", "https://www.nicovideo.jp");
        checkStatusCode(httpInterface.execute(options));
        HttpGet get = new HttpGet(encodedUrl);
        get.setHeader("X-Frontend-Id", "6");
        get.setHeader("X-Frontend-Version", "0");
        get.setHeader("Referer", this.trackInfo.uri);
        get.setHeader("Origin", "https://www.nicovideo.jp");
        checkStatusCode(httpInterface.execute(get));
    }

    public String createSession(JSONObject delivery, boolean licensed) {
        final JSONObject session = delivery.getJSONObject("movie").getJSONObject("session");
        final JSONObject urls = session.getJSONArray("urls").getJSONObject(0);
        final JSONObject authTypes = session.getJSONObject("authTypes");
        final JSONObject sessions = new JSONObject()
                .put("recipe_id", session.getString("recipeId"))
                .put("content_id", session.getString("contentId"))
                .put("priority", session.getDouble("priority"))
                .put("content_type", "movie")
                .append("content_src_id_sets", new JSONObject().append("content_src_ids", new JSONObject().put("src_id_to_mux", new JSONObject()
                        .append("video_src_ids", session.getJSONArray("videos").getString(0))
                        .append("audio_src_ids", session.getJSONArray("audios").getString(0)))))
                .put("timing_constraint", "unlimited")
                .put("keep_method", new JSONObject().put("heartbeat", new JSONObject().put("lifetime", session.getInt("heartbeatLifetime"))))
                .put("client_info", new JSONObject().put("player_id", session.getString("playerId")))
                .put("content_uri", "")
                .put("session_operation_auth", new JSONObject().put("session_operation_auth_by_signature", new JSONObject()
                        .putOnce("token", session.getString("token"))
                        .put("signature", session.getString("signature"))))
                .put("protocol", createProtocol(urls, licensed ? delivery.getJSONObject("encryption") : null,licensed))
                .put("content_auth", new JSONObject()
                        .put("auth_type", authTypes.getString(authTypes.keys().next()))
                        .put("content_key_timeout", session.getInt("contentKeyTimeout"))
                        .put("service_id", "nicovideo")
                        .put("service_user_id", session.getString("serviceUserId")));
        return new JSONObject().put("session", sessions).toString();
    }

    public JSONObject createProtocol(JSONObject urls, JSONObject encryption, boolean licensed) {
        return new JSONObject()
                .put("name", "http")
                //hls_parameters=HLS
                //http_output_download_parameters=HTTP BUT NOT WORKING 6/16?
                .put("parameters", new JSONObject().put("http_parameters", new JSONObject().put("parameters",
                        licensed ? new JSONObject().put("hls_parameters", new JSONObject()
                                .put("encryption", new JSONObject()
                                        .put("hls_encryption_v1", new JSONObject()
                                                .put("encrypted_key", encryption.getString("encryptedKey"))
                                                .put("key_uri", encryption.getString("keyUri"))))
                                .put("use_well_known_port", urls.getBoolean("isWellKnownPort") ? "yes" : "no")
                                .put("use_ssl", urls.getBoolean("isSsl") ? "yes" : "no")
                                .put("transfer_preset", "")
                                .put("segment_duration", 6000))
                                : new JSONObject().put("hls_parameters", new JSONObject()
                                .put("use_well_known_port", urls.getBoolean("isWellKnownPort") ? "yes" : "no")
                                .put("use_ssl", urls.getBoolean("isSsl") ? "yes" : "no")
                                .put("transfer_preset", "")
                                .put("segment_duration", 6000)))));
    }

    @SneakyThrows
    public static CloseableHttpResponse checkStatusCode(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_PARTIAL_CONTENT ||
                statusCode == HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION || statusCode == HttpStatus.SC_CREATED) {
            return response;
        }
        throw new IOException("Unexpected response code from video info: " + response.getStatusLine().getStatusCode());
    }

    @Override
    public void stop() {
        super.stop();
        stopKeepAlive();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NicoVideoAudioTrack(trackInfo, sourceManager);
    }

    public static class NicoCryptStatus {
        public boolean licenced;
        public String url;

        public NicoCryptStatus(String url, boolean licenced) {
            this.url = url;
            this.licenced = licenced;
        }

        public String getUrl() {
            return url;
        }
    }
}

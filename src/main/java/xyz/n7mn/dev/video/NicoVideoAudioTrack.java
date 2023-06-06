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

import java.io.IOException;
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
            String playbackUrl = loadUrl(httpInterface);
            log.debug("Starting KeepAlive from SessionId: {}", sessionId);
            startKeepAlive();
            log.debug("Starting NicoNico track from URL: {}", playbackUrl);
            // Encoded Urls
            // 通常は一回URLにアクセスする必要がある 安全にしたい場合はそうしてほしい
            // 一回のHTTPアクセスをなくすという利点も有るため一概にデメリットというわけでもない。
            processDelegate(new NicoVideoTsM3uStreamAudioTrack(trackInfo, httpInterface, playbackUrl.replaceFirst("master\\.m3u8", "1/ts/playlist.m3u8")), executor);
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
        try (HttpInterface httpInterface = sourceManager.getHttpInterface())  {
            checkStatusCode(httpInterface.execute(new HttpOptions("https://api.dmc.nico/api/sessions/" + sessionId + "?_format=json&_method=PUT")));

            HttpPost post = new HttpPost("https://api.dmc.nico/api/sessions/" + sessionId + "?_format=json&_method=PUT");
            post.setEntity(new StringEntity(data));
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");
            post.setHeader("User-Agent", "NicoUtils/1.0 (https://github.com/KoutaChan/NicoUtils)");
            //lets go
            data = new JSONObject(EntityUtils.toString(checkStatusCode(httpInterface.execute(post)).getEntity(), StandardCharsets.UTF_8.name())).getJSONObject("data").toString();
        }
    }

    public void stopKeepAlive() {
        if (running) {
            running = false;
        }
    }

    public String loadUrl(HttpInterface httpInterface) throws IOException {
        CloseableHttpResponse response = checkStatusCode(httpInterface.execute(new HttpGet(trackInfo.uri)));

        String supporting = makeJson(Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser()));
        HttpPost post = new HttpPost("https://api.dmc.nico/api/sessions?_format=json");
        post.setEntity(new StringEntity(supporting));
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");
        post.setHeader("User-Agent", "NicoUtils/1.0 (https://github.com/KoutaChan/NicoUtils)");
        JSONObject dataJson = new JSONObject(EntityUtils.toString(checkStatusCode(httpInterface.execute(post)).getEntity(), StandardCharsets.UTF_8.name())).getJSONObject("data");
        //Send Data;s
        this.data = dataJson.toString();

        JSONObject session = dataJson.getJSONObject("session");
        this.sessionId = session.getString("id");
        return session.getString("content_uri");
    }

    public String makeJson(Document document) {
        JSONObject object = new JSONObject(document.getElementById("js-initial-watch-data").attr("data-api-data"));

        final JSONObject session = object.getJSONObject("media")
                .getJSONObject("delivery")
                .getJSONObject("movie")
                .getJSONObject("session");
        final JSONObject urls = session.getJSONArray("urls").getJSONObject(0);

        final JSONObject sessions = new JSONObject()
                .put("recipe_id", session.getString("recipeId"))
                .put("content_id", session.getString("contentId"))
                .put("priority", session.getInt("priority"))
                .put("content_type", "movie")
                .append("content_src_id_sets", new JSONObject().append("content_src_ids",  new JSONObject().put("src_id_to_mux", new JSONObject()
                        .append("video_src_ids", session.getJSONArray("videos").getString(0))
                        .append("audio_src_ids", session.getJSONArray("audios").getString(0)))))
                .put("timing_constraint", "unlimited")
                .put("keep_method", new JSONObject().put("heartbeat", new JSONObject().put("lifetime", session.getInt("heartbeatLifetime"))))
                .put("client_info", new JSONObject().put("player_id", session.getString("playerId")))
                .put("content_uri", "")
                .put("session_operation_auth", new JSONObject().put("session_operation_auth_by_signature", new JSONObject()
                        .putOnce("token", session.getString("token"))
                        .put("signature", session.getString("signature"))))
                .put("protocol", new JSONObject()
                        .put("name", "http")
                        //hls_parameters
                        //http_output_download_parameters
                        .put("parameters", new JSONObject().put("http_parameters", new JSONObject().put("parameters", new JSONObject().put("hls_parameters", new JSONObject()
                                .put("use_well_known_port", urls.getBoolean("isWellKnownPort") ? "yes" : "no")
                                .put("use_ssl", urls.getBoolean("isSsl") ? "yes" : "no")
                                .put("transfer_preset", "")
                                .put("segment_duration", 6000))))))
                .put("content_auth", new JSONObject()
                        .put("auth_type", session.getJSONObject("authTypes").getString("http"))
                        .put("content_key_timeout", session.getInt("contentKeyTimeout"))
                        .put("service_id", "nicovideo")
                        .put("service_user_id", session.getString("serviceUserId")));
        //Once
        return new JSONObject().put("session", sessions).toString();
    }

    @SneakyThrows
    public static CloseableHttpResponse checkStatusCode(CloseableHttpResponse response) {
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
}

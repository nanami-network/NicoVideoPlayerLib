package xyz.n7mn.dev.video;

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;
import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

public class NicoVideoStreamSegmentUrlProvider extends M3uStreamSegmentUrlProvider {
    private final String segmentPlaylistUrl;
    private int currentSegments = -1;
    private List<M3uStreamSegmentUrlProvider.SegmentInfo> segments;

    public NicoVideoStreamSegmentUrlProvider(String segmentPlaylistUrl) {
        this.segmentPlaylistUrl = segmentPlaylistUrl;
    }

    @Override
    protected String getQualityFromM3uDirective(ExtendedM3uParser.Line directiveLine) {
        return "default";
    }

    @Override
    protected String fetchSegmentPlaylistUrl(HttpInterface httpInterface) throws IOException {
        return segmentPlaylistUrl;
    }

    @Override
    protected String getNextSegmentUrl(HttpInterface httpInterface) {
        try {
            if (this.segments == null) {
                String streamSegmentPlaylistUrl = fetchSegmentPlaylistUrl(httpInterface);
                if (streamSegmentPlaylistUrl == null) {
                    return null;
                }
                this.segments = loadStreamSegmentsList(httpInterface, streamSegmentPlaylistUrl);
                this.currentSegments = -1;
            }
            if (this.shouldEndSegments()) {
                return null;
            }
            this.currentSegments += 1;
            this.lastSegment = this.segments.get(this.currentSegments);
            return createSegmentUrl(this.segmentPlaylistUrl, lastSegment.url);
        } catch (IOException e) {
            throw new FriendlyException("Failed to get next part of the stream.", SUSPICIOUS, e);
        }
    }

    private boolean shouldEndSegments() {
        return segments.size() <= this.currentSegments + 1;
    }

    @Override
    protected HttpUriRequest createSegmentGetRequest(String url) {
        return new HttpGet(url);
    }
}

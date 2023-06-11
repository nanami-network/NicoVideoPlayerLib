package xyz.n7mn.dev.video.aes;

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;
import java.io.InputStream;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class NicoVideoCryptStreamSegmentUrlProvider extends M3uStreamSegmentUrlProvider {
    private final String segmentPlaylistUrl;
    private int currentSegments = -1;
    private NicoVideoCryptSegmentReader.NicoVideoCryptSegmentsInfo segmentsInfo;

    public NicoVideoCryptStreamSegmentUrlProvider(String segmentPlaylistUrl, NicoVideoCryptSegmentReader.NicoVideoCryptSegmentsInfo segments) {
        this.segmentPlaylistUrl = segmentPlaylistUrl;
        this.segmentsInfo = segments;
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
            if (this.segmentsInfo == null) {
                String streamSegmentPlaylistUrl = fetchSegmentPlaylistUrl(httpInterface);
                if (streamSegmentPlaylistUrl == null) {
                    return null;
                }
                this.segmentsInfo = NicoVideoCryptSegmentReader.loadStreamSegmentsList(httpInterface, streamSegmentPlaylistUrl);
                this.currentSegments = -1;
            }
            if (this.shouldEndSegments()) {
                return null;
            }
            this.currentSegments += 1;
            NicoVideoCryptSegmentReader.Segment segmentInfo = this.segmentsInfo.segments.get(this.currentSegments);
            return createSegmentUrl(this.segmentPlaylistUrl, segmentInfo.url);
        } catch (IOException e) {
            throw new FriendlyException("Failed to get next part of the stream.", SUSPICIOUS, e);
        }
    }

    /**
     * Fetches the input stream for the next segment in the M3U stream.
     *
     * @param httpInterface HTTP interface to use for any requests required to perform to find the segment URL.
     * @return Input stream of the next segment.
     */
    public InputStream getNextSegmentStream(HttpInterface httpInterface) {
        String url = getNextSegmentUrl(httpInterface);
        if (url == null) {
            return null;
        }

        CloseableHttpResponse response = null;
        boolean success = false;

        try {
            response = httpInterface.execute(createSegmentGetRequest(url));
            HttpClientTools.assertSuccessWithContent(response, "segment data URL");

            success = true;
            return response.getEntity().getContent();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (response != null && !success) {
                ExceptionTools.closeWithWarnings(response);
            }
        }
    }

    private boolean shouldEndSegments() {
        return segmentsInfo.segments.size() <= this.currentSegments + 1;
    }

    @Override
    protected HttpUriRequest createSegmentGetRequest(String url) {
        return new HttpGet(url);
    }
}

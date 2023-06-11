package xyz.n7mn.dev.video.normal;

import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.source.stream.MpegTsM3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class NicoVideoTsM3uStreamAudioTrack extends MpegTsM3uStreamAudioTrack {
    private final HttpInterface httpInterface;
    private final NicoVideoStreamSegmentUrlProvider segmentUrlProvider;

    /**
     * @param trackInfo Track info
     * @param httpInterface HTTP interface to use for loading segments
     * @param signedUrl URI of the base stream with signature resolved
     */
    public NicoVideoTsM3uStreamAudioTrack(AudioTrackInfo trackInfo, HttpInterface httpInterface, String signedUrl) {
        super(trackInfo);

        this.httpInterface = httpInterface;
        this.segmentUrlProvider = new NicoVideoStreamSegmentUrlProvider(signedUrl);
    }

    @Override
    protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
        return this.segmentUrlProvider;
    }

    @Override
    protected HttpInterface getHttpInterface() {
        return httpInterface;
    }
}

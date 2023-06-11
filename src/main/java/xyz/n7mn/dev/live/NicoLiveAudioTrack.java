package xyz.n7mn.dev.live;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class NicoLiveAudioTrack extends DelegatedAudioTrack {
    private static boolean LOW_LATENCY = true;

    /**
     * @param trackInfo Track info
     */
    public NicoLiveAudioTrack(AudioTrackInfo trackInfo) {
        super(trackInfo);
    }

    public static boolean isLowLatency() {
        return LOW_LATENCY;
    }

    public static void setLowLatency(boolean lowLatency) {
        LOW_LATENCY = lowLatency;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {

    }
}
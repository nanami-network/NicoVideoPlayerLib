package xyz.n7mn.dev.video.aes;

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

public class NicoVideoCryptSegmentReader {
    public static NicoVideoCryptSegmentsInfo loadStreamSegmentsList(HttpInterface httpInterface, String streamSegmentPlaylistUrl) throws IOException {
        NicoVideoCryptSegmentsInfo segmentsInfo = new NicoVideoCryptSegmentsInfo();
        ExtendedM3uParser.Line segmentInfo = null;

        for (String lineText : fetchResponseLines(httpInterface, new HttpGet(streamSegmentPlaylistUrl), "stream segments list")) {
            ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);
            if (line.isDirective()) {
                if ("EXT-X-KEY".equals(line.directiveName)) {
                    String[] fields = line.extraData.split(",", 3);
                    segmentsInfo.cryptInfo = new CryptInfo(fields[0], fields[1], fields[2]);
                } else if ("EXTINF".equals(line.directiveName)) {
                    segmentInfo = line;
                }
            } else if (line.isData()) {
                if (segmentInfo != null && segmentInfo.extraData.contains(",")) {
                    String[] fields = segmentInfo.extraData.split(",", 2);
                    segmentsInfo.segments.add(new Segment(line.lineData, parseSecondDuration(fields[0]), fields[1]));
                } else {
                    segmentsInfo.segments.add(new Segment(line.lineData, null, null));
                }
            }
        }
        return segmentsInfo;
    }

    public static class NicoVideoCryptSegmentsInfo {
        public List<Segment> segments = new ArrayList<>();
        public CryptInfo cryptInfo;

    }

    public static class CryptInfo {
        public final String type;
        public final String url;
        public final String iv;

        public CryptInfo(String type, String url, String iv) {
            this.type = type.replaceFirst("METHOD=", "");
            this.url = url.replaceFirst("URI=", "").replaceFirst("\"$", "");
            this.iv = iv.replaceFirst("IV=", "");
        }
    }


    public static class Segment {
        /**
         * URL of the segment.
         */
        public final String url;
        /**
         * Duration of the segment in milliseconds. <code>null</code> if unknown.
         */
        public final Long duration;
        /**
         * Name of the segment. <code>null</code> if unknown.
         */
        public final String name;


        public Segment(String url, Long duration, String name) {
            this.url = url;
            this.duration = duration;
            this.name = name;
        }
    }

    private static Long parseSecondDuration(String value) {
        try {
            double asDouble = Double.parseDouble(value);
            return (long) (asDouble * 1000.0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

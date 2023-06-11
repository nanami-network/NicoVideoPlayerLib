package xyz.n7mn.dev.video.aes;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.source.stream.MpegTsM3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.HttpGet;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM;

public class NicoVideoCryptTsM3uStreamAudioTrack extends MpegTsM3uStreamAudioTrack {
    private final HttpInterface httpInterface;
    private final NicoVideoCryptSegmentReader.NicoVideoCryptSegmentsInfo segmentsInfo;
    private NicoVideoCryptStreamSegmentUrlProvider segmentUrlProvider;
    private Cipher cipher;

    /**
     * @param trackInfo Track info
     */
    public NicoVideoCryptTsM3uStreamAudioTrack(AudioTrackInfo trackInfo, HttpInterface httpInterface, String signedUrl) throws IOException {
        super(trackInfo);
        this.httpInterface = httpInterface;
        this.segmentsInfo = NicoVideoCryptSegmentReader.loadStreamSegmentsList(httpInterface, signedUrl);
        this.segmentUrlProvider = new NicoVideoCryptStreamSegmentUrlProvider(signedUrl, segmentsInfo);
        try {
            this.cipher = Cipher.getInstance("AES/CBC/NoPadding");
            this.cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(getDecryptKey().readAllBytes(), "AES"), new IvParameterSpec(getEncryptionIvByte(segmentsInfo.cryptInfo.iv)));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
        return segmentUrlProvider;
    }

    @Override
    protected void processJoinedStream(LocalAudioTrackExecutor localExecutor, InputStream stream) throws Exception {
        CipherInputStream inputStream = new CipherInputStream(stream, this.cipher);
        NicoVideoMpegTsElementaryInputStream elementaryInputStream = new NicoVideoMpegTsElementaryInputStream(inputStream, ADTS_ELEMENTARY_STREAM);
        PesPacketInputStream pesPacketInputStream = new PesPacketInputStream(elementaryInputStream);

        processDelegate(new AdtsAudioTrack(trackInfo, pesPacketInputStream), localExecutor);
    }

    @Override
    protected HttpInterface getHttpInterface() {
        return httpInterface;
    }

    public InputStream getDecryptKey() throws IOException {
        HttpGet get = new HttpGet(this.segmentsInfo.cryptInfo.url);
        return httpInterface.execute(get).getEntity().getContent();
    }

    private static byte[] getEncryptionIvByte(String iv) {
        String trimmedIv = iv.startsWith("0x") ? iv.substring(2) : iv;
        byte[] ivData = new BigInteger(trimmedIv).toByteArray();
        byte[] ivDataEncoded = new byte[16];
        int offset = ivData.length > 16 ? ivData.length - 16 : 0;
        System.arraycopy(
                ivData,
                offset,
                ivDataEncoded,
                ivDataEncoded.length - ivData.length + offset,
                ivData.length - offset);
        return ivDataEncoded;
    }
}

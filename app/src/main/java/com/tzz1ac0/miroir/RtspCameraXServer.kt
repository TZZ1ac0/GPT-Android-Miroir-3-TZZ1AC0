package com.tzz1ac0.miroir

import android.content.Context
import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.library.base.StreamBase
import com.pedro.library.util.streamclient.StreamBaseClient
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

/**
 * RTSP server that encodes the same CameraX/GL frames shown by the on-device preview.
 * It deliberately does not open its own camera capture session.
 */
internal class RtspCameraXServer(
    context: Context,
    connectChecker: ConnectChecker,
    port: Int,
    onCameraError: (String) -> Unit
) : StreamBase(
    context,
    FrontCameraXSource(context, onCameraError),
    NoAudioSource()
) {

    private val rtspServer = RtspServer(connectChecker, port)

    init {
        rtspServer.setOnlyVideo(true)
        setVideoCodec(VideoCodec.H264)
    }

    override fun getStreamClient(): StreamBaseClient = RtspServerStreamClient(rtspServer)

    override fun setVideoCodecImp(codec: VideoCodec) {
        rtspServer.setVideoCodec(codec)
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        rtspServer.setAudioCodec(codec)
    }

    override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) {
        rtspServer.setAudioInfo(sampleRate, isStereo)
    }

    override fun startStreamImp(endPoint: String) {
        rtspServer.startServer()
    }

    override fun stopStreamImp() {
        rtspServer.stopServer()
    }

    override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        rtspServer.setVideoInfo(sps, pps, vps)
    }

    override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtspServer.sendVideo(videoBuffer, info)
    }

    override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtspServer.sendAudio(audioBuffer, info)
    }
}

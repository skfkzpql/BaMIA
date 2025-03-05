package com.example.bamia.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * AudioStreamingService (최종 디버깅 버전)
 *
 * 각 기기는 인텐트를 통해 자신의 역할("CAMERA" 또는 "VIEWER")과
 * 상대방의 IP(REMOTE_IP)를 전달받습니다.
 *
 * - 송신: 자신의 마이크 음성을 REMOTE_IP로 UDP 전송하며,
 *         AudioRecord 초기화 상태 및 읽은 바이트 수, 전송 타임스탬프 등을 로그로 기록합니다.
 *
 * - 수신: 포트(50005)로 들어온 UDP 패킷 중, 패킷의 출발지 IP가 REMOTE_IP와 일치할 때만 오디오를 재생하며,
 *         패킷 수신 시 송신자 IP, 패킷 길이, 재생 여부 등을 상세 로그로 기록합니다.
 *
 * [주의]
 * - 인텐트로 전달되는 "ROLE"과 "REMOTE_IP"는 반드시 올바르게 설정되어야 합니다.
 * - 각 기기는 동일한 WiFi 네트워크에 있어야 하며, UDP 패킷이 차단되지 않아야 합니다.
 */
class AudioStreamingService : Service() {

    // 오디오 기본 파라미터
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    private val port = 50005

    // 송신 관련 변수
    @Volatile private var isSending = false
    private var audioRecord: AudioRecord? = null
    private var sendingSocket: DatagramSocket? = null

    // 수신 관련 변수
    @Volatile private var isReceiving = false
    private var audioTrack: AudioTrack? = null
    private var receivingSocket: DatagramSocket? = null

    // 인텐트로 전달받은 ROLE과 REMOTE_IP
    private var role: String? = null    // "CAMERA" 또는 "VIEWER"
    private var remoteIp: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AudioStreamingService", "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        role = intent?.getStringExtra("ROLE")
        remoteIp = intent?.getStringExtra("REMOTE_IP")
        val action = intent?.getStringExtra("ACTION")
        Log.d("AudioStreamingService", "onStartCommand: ACTION=$action, ROLE=$role, REMOTE_IP=$remoteIp")
        when (action) {
            "START_SENDING" -> startSending()
            "STOP_SENDING" -> stopSending()
            "START_RECEIVING" -> startReceiving()
            "STOP_RECEIVING" -> stopReceiving()
            "STOP_ALL" -> {
                stopSending()
                stopReceiving()
                stopSelf()
            }
            else -> Log.d("AudioStreamingService", "Unknown ACTION received")
        }
        return START_STICKY
    }

    /**
     * 송신 시작
     */
    private fun startSending() {
//        if (isSending) {
//            Log.d("AudioStreamingService", "[$role] Already sending audio")
//            return
//        }
        if (remoteIp.isNullOrEmpty()) {
            Log.e("AudioStreamingService", "[$role] REMOTE_IP not provided for sending")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioStreamingService", "[$role] RECORD_AUDIO permission not granted. Cannot start sending.")
            return
        }
        isSending = true
        Thread {
            try {
                sendingSocket = DatagramSocket()
                val destAddress = InetAddress.getByName(remoteIp)
                // AudioRecord 초기화 및 상태 확인
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioStreamingService", "[$role] AudioRecord initialization failed")
                    return@Thread
                }
                audioRecord?.startRecording()
                Log.d("AudioStreamingService", "[$role] Started sending audio to $remoteIp:$port at ${System.currentTimeMillis()}")
                val buffer = ByteArray(bufferSize)
                while (isSending) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Log.d("AudioStreamingService", "[$role] Read $read bytes from mic at ${System.currentTimeMillis()}")
                        val packet = DatagramPacket(buffer, read, destAddress, port)
                        sendingSocket?.send(packet)
                        // Log.d("AudioStreamingService", "[$role] Sent packet to $remoteIp at ${System.currentTimeMillis()}")
                    } else {
                        Log.d("AudioStreamingService", "[$role] Read 0 bytes (mic might be muted or error)")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreamingService", "[$role] Error during sending", e)
            } finally {
                cleanupAudioRecord()
                cleanupSendingSocket()
                Log.d("AudioStreamingService", "[$role] Stopped sending audio at ${System.currentTimeMillis()}")
            }
        }.start()
    }

    /**
     * 수신 시작
     */
    private fun startReceiving() {
//        if (isReceiving) {
//            Log.d("AudioStreamingService", "[$role] Already receiving audio")
//            return
//        }
        if (remoteIp.isNullOrEmpty()) {
            Log.e("AudioStreamingService", "[$role] REMOTE_IP not provided for receiving")
            return
        }
        isReceiving = true
        Thread {
            try {
                receivingSocket = DatagramSocket(port)
                audioTrack = AudioTrack(
                    AudioFormat.CHANNEL_OUT_MONO,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e("AudioStreamingService", "[$role] AudioTrack initialization failed")
                    return@Thread
                }
                audioTrack?.play()
                Log.d("AudioStreamingService", "[$role] Started receiving audio on port $port, expecting from $remoteIp at ${System.currentTimeMillis()}")
                val buffer = ByteArray(bufferSize)
                while (isReceiving) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    receivingSocket?.receive(packet)
                    val senderIp = packet.address.hostAddress
                    // Log.d("AudioStreamingService", "[$role] Received packet from $senderIp with ${packet.length} bytes at ${System.currentTimeMillis()}")
                    if (senderIp == remoteIp) {
                        audioTrack?.write(packet.data, 0, packet.length)
                        // Log.d("AudioStreamingService", "[$role] Played packet from $senderIp")
                    } else {
                        // Log.d("AudioStreamingService", "[$role] Ignored packet from $senderIp")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreamingService", "[$role] Error during receiving", e)
            } finally {
                cleanupAudioTrack()
                cleanupReceivingSocket()
                Log.d("AudioStreamingService", "[$role] Stopped receiving audio at ${System.currentTimeMillis()}")
            }
        }.start()
    }

    private fun stopSending() {
        isSending = false
        cleanupAudioRecord()
        cleanupSendingSocket()
    }

    private fun stopReceiving() {
        isReceiving = false
        cleanupAudioTrack()
        cleanupReceivingSocket()
    }

    private fun cleanupAudioRecord() {
        try { audioRecord?.stop() } catch (e: Exception) { }
        audioRecord?.release()
        audioRecord = null
    }

    private fun cleanupSendingSocket() {
        try { sendingSocket?.close() } catch (e: Exception) { }
        sendingSocket = null
    }

    private fun cleanupAudioTrack() {
        try { audioTrack?.stop() } catch (e: Exception) { }
        audioTrack?.release()
        audioTrack = null
    }

    private fun cleanupReceivingSocket() {
        try { receivingSocket?.close() } catch (e: Exception) { }
        receivingSocket = null
    }

    override fun onDestroy() {
        stopSending()
        stopReceiving()
        Log.d("AudioStreamingService", "Service destroyed at ${System.currentTimeMillis()}")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

import UIKit
import MobileVLCKit
import Shared

/// O player nativo do iOS não abre RTSP (formato das câmeras EZVIZ e iCSee); o VLC abre.
final class VlcVideoFactory: NSObject, VideoViewFactory {
    func create(url: String, muted: Bool, listener: VideoListener) -> UIView {
        VlcVideoView(url: url, muted: muted, listener: listener)
    }

    func setMuted(view: UIView, muted: Bool) {
        (view as? VlcVideoView)?.setMuted(muted)
    }

    func release(view: UIView) {
        (view as? VlcVideoView)?.stop()
    }
}

final class VlcVideoView: UIView, VLCMediaPlayerDelegate {
    private let player = VLCMediaPlayer()
    private let listener: VideoListener
    private var reportedPlaying = false

    init(url: String, muted: Bool, listener: VideoListener) {
        self.listener = listener
        super.init(frame: .zero)
        backgroundColor = .black
        isUserInteractionEnabled = false
        player.drawable = self
        player.delegate = self
        guard let link = URL(string: url) else {
            listener.onError()
            return
        }
        let media = VLCMedia(url: link)
        // Buffer curto para ficar perto do tempo real; RTSP sobre TCP atravessa melhor o Wi-Fi.
        media.addOptions(["network-caching": 300, "rtsp-tcp": true])
        player.media = media
        setMuted(muted)
        player.play()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) não é usado")
    }

    func setMuted(_ muted: Bool) {
        // Funciona se o VLCKit declarar "audio" como opcional ou não.
        let audio: VLCAudio? = player.audio
        audio?.volume = muted ? 0 : 100
    }

    func stop() {
        player.delegate = nil
        player.stop()
    }

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch player.state {
        case .playing:
            if !reportedPlaying {
                reportedPlaying = true
                listener.onPlaying()
            }
        case .error:
            listener.onError()
        default:
            break
        }
    }
}

import SwiftUI

@main
struct SesNotWatchApp: App {
    @StateObject private var recorder = AudioRecorder()
    @StateObject private var session = WatchSessionManager.shared

    var body: some Scene {
        WindowGroup {
            RecorderView()
                .environmentObject(recorder)
                .environmentObject(session)
                .onAppear {
                    session.activate()
                    session.flushQueue()   // önceki oturumdan kalan dosyaları gönder
                }
        }
    }
}

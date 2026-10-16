import SwiftUI

@main
struct SesNotApp: App {
    @StateObject private var store = NoteStore()
    @StateObject private var pipeline: ProcessingPipeline
    @StateObject private var phoneSession: PhoneSessionManager
    @StateObject private var phoneRecorder: PhoneRecorder

    init() {
        let store = NoteStore()
        let pipeline = ProcessingPipeline(store: store)
        _store = StateObject(wrappedValue: store)
        _pipeline = StateObject(wrappedValue: pipeline)
        _phoneSession = StateObject(wrappedValue: PhoneSessionManager(store: store, pipeline: pipeline))
        _phoneRecorder = StateObject(wrappedValue: PhoneRecorder(store: store, pipeline: pipeline))
    }

    var body: some Scene {
        WindowGroup {
            NotesListView()
                .environmentObject(store)
                .environmentObject(pipeline)
                .environmentObject(phoneSession)
                .environmentObject(phoneRecorder)
                .environmentObject(RecorderBridge.shared)
                .task {
                    phoneSession.activate()
                    await pipeline.resumeUnfinished()
                }
        }
    }
}

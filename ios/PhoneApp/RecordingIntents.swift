import AppIntents

/// Siri / Kısayollar: "SesNot kaydı başlat", "SesNot kaydı durdur".
/// Uygulamayı öne getirir ve PhoneRecorder'ı tetikler (arka planda mikrofon başlatmak için
/// uygulamanın ön planda olması gerekir; openAppWhenRun bunu sağlar).
struct StartRecordingIntent: AppIntent {
    static var title: LocalizedStringResource = "SesNot kaydı başlat"
    static var description = IntentDescription("Seçili şablonla telefondan (veya Bluetooth saat mikrofonundan) kayıt başlatır.")
    static var openAppWhenRun = true

    @Parameter(title: "Şablon", default: .note)
    var template: TemplateEntity

    @MainActor
    func perform() async throws -> some IntentResult {
        RecorderBridge.shared.requestStart(template: template.noteTemplate)
        return .result()
    }
}

struct StopRecordingIntent: AppIntent {
    static var title: LocalizedStringResource = "SesNot kaydı durdur"
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        RecorderBridge.shared.requestStop()
        return .result()
    }
}

enum TemplateEntity: String, AppEnum {
    case meeting, shopping, note
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Şablon")
    static var caseDisplayRepresentations: [TemplateEntity: DisplayRepresentation] = [
        .meeting: "Toplantı", .shopping: "Alışveriş", .note: "Sesli Not",
    ]
    var noteTemplate: NoteTemplate {
        switch self { case .meeting: return .meeting; case .shopping: return .shopping; case .note: return .note }
    }
}

struct SesNotShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: StartRecordingIntent(),
                    phrases: ["\(.applicationName) kaydı başlat", "\(.applicationName) ile kayıt al"],
                    shortTitle: "Kayıt başlat", systemImageName: "mic.fill")
        AppShortcut(intent: StopRecordingIntent(),
                    phrases: ["\(.applicationName) kaydı durdur"],
                    shortTitle: "Kaydı durdur", systemImageName: "stop.fill")
    }
}

/// Intent'ler ile uygulama içindeki PhoneRecorder arasında köprü: intent önce çalışabilir,
/// kaydedici sonra bağlanır; istek bekletilir.
@MainActor
final class RecorderBridge: ObservableObject {
    static let shared = RecorderBridge()
    @Published var pendingStart: NoteTemplate?
    @Published var pendingStop = false
    @Published var showRecordSheet = false

    func requestStart(template: NoteTemplate) { pendingStart = template; showRecordSheet = true }
    func requestStop() { pendingStop = true }
}

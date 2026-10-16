import Foundation

/// Şablon başına LLM istemi ve LLM çıktısı ayrıştırma.
/// Foundation Models yolu bu dosyayı kullanır; yedek özetleyici `ExtractiveSummarizer`dadır.
enum SummaryTemplates {

    /// Cihaz üstü LLM için istem. Çıktı JSON istenir; `Summary` ile aynı alanlar.
    static func prompt(for template: NoteTemplate, transcript: String) -> String {
        let common = """
        Aşağıdaki Türkçe konuşma transkriptini işle. YALNIZCA şu JSON'u döndür, başka hiçbir şey yazma:
        {"title": "...", "abstract": "...", "items": ["..."], "actions": ["..."], "dates": ["..."]}
        """
        let specific: String
        switch template {
        case .meeting:
            specific = """
            Bu bir toplantı kaydıdır. Satırlar "Kişi 1:", "Kişi 2:" gibi konuşmacı etiketleriyle başlayabilir;
            kim ne söylediyse ona göre değerlendir. title: 5-8 kelimelik toplantı başlığı. abstract: 2-3 cümlelik özet.
            items: alınan kararlar, kararı kim önerdiyse belirt ("Kişi 2: bütçe onaylandı").
            actions: yapılacak işler, sorumlu konuşmacıyla ("Kişi 1: raporu gönder").
            dates: konuşulan tarih ve saatler.
            """
        case .shopping:
            specific = """
            Bu bir alışveriş listesi kaydıdır. title: "Alışveriş Listesi" veya mağaza adı. abstract: boş bırak.
            items: her ürün ayrı, miktar ve birimle ("2 kg domates", "1 paket süt"). Tekrarları birleştir.
            actions ve dates: boş dizi.
            """
        case .note:
            specific = """
            Bu bir sesli nottur. title: 3-6 kelimelik başlık. abstract: notun 2-4 cümlelik özeti.
            items: en fazla 5 anahtar nokta. actions: varsa yapılacaklar. dates: geçen tarihler.
            """
        }
        return common + "\n" + specific + "\n\nTRANSKRİPT:\n" + transcript
    }

    /// LLM çıktısını ayrıştırır; bozuksa nil döner ve yedek özetleyici devreye girer.
    static func parse(llmOutput: String) -> Summary? {
        guard let start = llmOutput.firstIndex(of: "{"),
              let end = llmOutput.lastIndex(of: "}"), start < end else { return nil }
        let json = String(llmOutput[start...end])
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        func strings(_ key: String) -> [String] {
            (obj[key] as? [Any])?.compactMap { $0 as? String }.filter { !$0.isEmpty } ?? []
        }
        let summary = Summary(
            title: obj["title"] as? String ?? "",
            abstract: obj["abstract"] as? String ?? "",
            items: strings("items").map { SummaryItem(text: $0) },
            actions: strings("actions").map { SummaryItem(text: $0, owner: ownerPrefix($0)) },
            dates: strings("dates")
        )
        return summary.isEmpty ? nil : summary
    }

    /// "Ahmet: raporu gönder" → "Ahmet"
    static func ownerPrefix(_ text: String) -> String? {
        guard let colon = text.firstIndex(of: ":") else { return nil }
        let name = text[..<colon].trimmingCharacters(in: .whitespaces)
        return name.split(separator: " ").count <= 2 && !name.isEmpty ? name : nil
    }
}

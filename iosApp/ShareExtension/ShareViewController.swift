import UIKit
import UniformTypeIdentifiers

private enum ShareLimits {
    static let maxPayloadBytes = 32 * 1024
    static let maxProviderCount = 8
    static let maxURLBytes = 64 * 1024
    static let maxFilenameBytes = 255
    static let defaultFilename = "shared.ics"
}

private enum ShareFailure: String {
    case tooLarge = "too_large"
    case tooManyItems = "too_many_items"
    case invalidUTF8 = "invalid_utf8"
    case unreadable = "unreadable"
}

final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        collectPayload { [weak self] result in
            guard let self else { return }
            switch result {
            case .payload(let payload):
                guard payload.hasContent else {
                    self.extensionContext?.completeRequest(returningItems: nil)
                    return
                }
                self.openContainingApp(with: payload)
            case .failure(let failure):
                self.openContainingApp(with: failure)
            }
        }
    }

    private func collectPayload(completion: @escaping (PayloadCollectionResult) -> Void) {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            completion(.payload(SharedPayload()))
            return
        }

        let providers = items.flatMap { $0.attachments ?? [] }
        guard providers.count <= ShareLimits.maxProviderCount else {
            completion(.failure(.tooManyItems))
            return
        }

        let group = DispatchGroup()
        let resultQueue = DispatchQueue(label: "com.udnahc.opentasks.share.provider-results")
        var results = Array(repeating: ProviderResult.ignored, count: providers.count)

        for (index, provider) in providers.enumerated() {
            guard let typeIdentifier = Self.typeIdentifier(for: provider) else { continue }
            group.enter()
            provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { item, error in
                let result: ProviderResult
                if error != nil {
                    result = .failure(.unreadable)
                } else {
                    result = Self.providerResult(item, typeIdentifier: typeIdentifier)
                }
                resultQueue.async {
                    results[index] = result
                    group.leave()
                }
            }
        }

        group.notify(queue: resultQueue) {
            let result = Self.aggregate(results)
            DispatchQueue.main.async {
                completion(result)
            }
        }
    }

    private static func typeIdentifier(for provider: NSItemProvider) -> String? {
        if provider.hasItemConformingToTypeIdentifier(UTType.calendarEvent.identifier) ||
            provider.registeredTypeIdentifiers.contains("com.apple.ical.ics") {
            return provider.registeredTypeIdentifiers.first {
                $0 == "com.apple.ical.ics" || UTType($0)?.conforms(to: .calendarEvent) == true
            } ?? UTType.calendarEvent.identifier
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            return UTType.url.identifier
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
            return UTType.text.identifier
        }
        return nil
    }

    private static func providerResult(_ item: NSSecureCoding?, typeIdentifier: String) -> ProviderResult {
        if typeIdentifier == UTType.url.identifier {
            if let url = item as? URL {
                return boundedResult(.url(url.absoluteString))
            }
            if let url = item as? NSURL {
                return boundedResult(.url(url.absoluteString ?? ""))
            }
            if let value = item as? String {
                return boundedResult(.url(value))
            }
            return .ignored
        }

        if typeIdentifier == UTType.text.identifier {
            if let value = item as? String {
                return boundedResult(.text(value))
            }
            if let data = item as? Data {
                return dataResult(data, as: ProviderResult.text)
            }
            return .ignored
        }

        if let value = item as? String {
            return boundedResult(.calendar(value))
        }
        if let data = item as? Data {
            return dataResult(data, as: ProviderResult.calendar)
        }
        if let url = item as? URL {
            return boundedFileResult(url)
        }
        if let url = item as? NSURL, let fileURL = url as URL? {
            return boundedFileResult(fileURL)
        }
        return .ignored
    }

    private static func aggregate(_ results: [ProviderResult]) -> PayloadCollectionResult {
        var payload = SharedPayload()
        for result in results {
            switch result {
            case .ignored:
                continue
            case .failure(let failure):
                return .failure(failure)
            case .text(let value):
                if payload.description.isEmpty {
                    payload.description = value
                }
                if payload.url.isEmpty {
                    payload.url = firstURL(in: value) ?? ""
                }
            case .url(let value):
                if payload.url.isEmpty {
                    payload.url = value
                }
                if payload.description.isEmpty {
                    payload.description = value
                }
            case .calendar(let value):
                if payload.icsContent.isEmpty {
                    payload.icsContent = value
                } else {
                    payload.icsContent += "\n\(value)"
                }
            }
            if payload.failure != nil {
                return .failure(payload.failure ?? .tooLarge)
            }
        }
        return .payload(payload)
    }

    private static func boundedResult(_ result: ProviderResult) -> ProviderResult {
        switch result {
        case .text(let value), .url(let value), .calendar(let value):
            guard utf8CountUpTo(value, limit: ShareLimits.maxPayloadBytes) <= ShareLimits.maxPayloadBytes else {
                return .failure(.tooLarge)
            }
            return result
        case .ignored, .failure(_):
            return result
        }
    }

    private static func stringResult(_ data: Data) -> String? {
        return String(data: data, encoding: .utf8)
    }

    private static func dataResult(
        _ data: Data,
        as makeResult: (String) -> ProviderResult,
    ) -> ProviderResult {
        guard data.count <= ShareLimits.maxPayloadBytes else { return .failure(.tooLarge) }
        guard let value = stringResult(data) else { return .failure(.invalidUTF8) }
        return boundedResult(makeResult(value))
    }

    private static func boundedFileResult(_ url: URL) -> ProviderResult {
        do {
            let handle = try FileHandle(forReadingFrom: url)
            defer { try? handle.close() }
            var data = Data()
            while data.count <= ShareLimits.maxPayloadBytes {
                let requested = min(8 * 1024, ShareLimits.maxPayloadBytes + 1 - data.count)
                guard let chunk = try handle.read(upToCount: requested), !chunk.isEmpty else { break }
                data.append(chunk)
            }
            if data.count > ShareLimits.maxPayloadBytes {
                return .failure(.tooLarge)
            }
            guard let value = String(data: data, encoding: .utf8) else {
                return .failure(.invalidUTF8)
            }
            return boundedResult(.calendar(value))
        } catch {
            return .failure(.unreadable)
        }
    }

    private func openContainingApp(with payload: SharedPayload) {
        guard let url = makeShareURL(payload: payload, failure: nil) else {
            openContainingApp(with: .tooLarge)
            return
        }
        openContainingApp(url: url)
    }

    private func openContainingApp(with failure: ShareFailure) {
        guard let url = makeShareURL(payload: nil, failure: failure) else {
            showOpenFailure()
            return
        }
        openContainingApp(url: url)
    }

    private func makeShareURL(payload: SharedPayload?, failure: ShareFailure?) -> URL? {
        var components = URLComponents()
        components.scheme = "opentasks"
        components.host = "share"
        if let failure {
            components.queryItems = [URLQueryItem(name: "error", value: failure.rawValue)]
        } else if let payload {
            components.queryItems = [
                URLQueryItem(name: "description", value: payload.description),
                URLQueryItem(name: "url", value: payload.url),
                URLQueryItem(name: "ics", value: payload.icsContent),
                URLQueryItem(name: "icsFileName", value: payload.icsFileName),
            ]
        } else {
            return nil
        }

        guard let url = components.url,
              Self.utf8CountUpTo(url.absoluteString, limit: ShareLimits.maxURLBytes) <= ShareLimits.maxURLBytes else {
            return nil
        }
        return url
    }

    private func openContainingApp(url: URL) {
        extensionContext?.open(url) { [weak self] accepted in
            guard let self else { return }
            DispatchQueue.main.async {
                if accepted {
                    self.extensionContext?.completeRequest(returningItems: nil)
                } else {
                    self.showOpenFailure()
                }
            }
        }
    }

    private func showOpenFailure() {
        let alert = UIAlertController(
            title: "OpenTasks",
            message: "Unable to open OpenTasks from this share.",
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private static func firstURL(in text: String) -> String? {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return nil
        }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return detector.firstMatch(in: text, options: [], range: range)?
            .url?
            .absoluteString
    }

    fileprivate static func utf8CountUpTo(_ value: String, limit: Int) -> Int {
        value.utf8.prefix(limit + 1).count
    }
}

private enum PayloadCollectionResult {
    case payload(SharedPayload)
    case failure(ShareFailure)
}

private enum ProviderResult {
    case ignored
    case text(String)
    case url(String)
    case calendar(String)
    case failure(ShareFailure)
}

private struct SharedPayload {
    var description: String = ""
    var url: String = ""
    var icsContent: String = ""
    var icsFileName: String = ShareLimits.defaultFilename

    var hasContent: Bool {
        !description.isEmpty || !url.isEmpty || !icsContent.isEmpty
    }

    var failure: ShareFailure? {
        guard ShareLimits.maxFilenameBytes >= 0,
              ShareViewController.utf8CountUpTo(icsFileName, limit: ShareLimits.maxFilenameBytes) <= ShareLimits.maxFilenameBytes else {
            return .tooLarge
        }
        let values = [description, url, icsContent, icsFileName]
        var total = 0
        for value in values {
            total += ShareViewController.utf8CountUpTo(
                value,
                limit: ShareLimits.maxPayloadBytes - min(total, ShareLimits.maxPayloadBytes),
            )
            if total > ShareLimits.maxPayloadBytes {
                return .tooLarge
            }
        }
        return nil
    }
}

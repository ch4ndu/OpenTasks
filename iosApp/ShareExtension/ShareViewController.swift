import Darwin
import UIKit
import UniformTypeIdentifiers

private enum ShareExtensionLimits {
    static let maxProviderCount = 8
    static let readBufferBytes = 8 * 1024
}

private enum ProviderReadFailure: Error {
    case tooLarge
    case unreadable
}

final class ShareViewController: UIViewController {
    private let handoffQueue = DispatchQueue(label: "com.udnahc.opentasks.share.handoff")

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
                self.showPublishConfirmation(.accepted(payload))
            case .failure(let failure):
                self.showPublishConfirmation(.rejected(failure))
            }
        }
    }

    private func collectPayload(completion: @escaping (PayloadCollectionResult) -> Void) {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            completion(.payload(ShareHandoffPayload()))
            return
        }

        var providers: [NSItemProvider] = []
        for item in items {
            for provider in item.attachments ?? [] {
                guard providers.count < ShareExtensionLimits.maxProviderCount else {
                    completion(.failure(.tooManyItems))
                    return
                }
                providers.append(provider)
            }
        }

        let group = DispatchGroup()
        let resultQueue = DispatchQueue(label: "com.udnahc.opentasks.share.provider-results")
        var results = Array(repeating: ProviderResult.ignored, count: providers.count)

        for (index, provider) in providers.enumerated() {
            guard let typeIdentifier = Self.typeIdentifier(for: provider) else { continue }
            group.enter()
            Self.loadProvider(provider, typeIdentifier: typeIdentifier) { result in
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

    private static func loadProvider(
        _ provider: NSItemProvider,
        typeIdentifier: String,
        completion: @escaping (ProviderResult) -> Void,
    ) {
        provider.loadInPlaceFileRepresentation(forTypeIdentifier: typeIdentifier) { url, _, _ in
            if let url {
                completion(streamResult(url, typeIdentifier: typeIdentifier))
                return
            }

            provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, _ in
                if let url {
                    completion(streamResult(url, typeIdentifier: typeIdentifier))
                    return
                }

                guard typeIdentifier == UTType.url.identifier,
                      provider.canLoadObject(ofClass: URL.self) else {
                    completion(.failure(.unreadable))
                    return
                }
                _ = provider.loadObject(ofClass: URL.self) { url, error in
                    guard error == nil, let url else {
                        completion(.failure(.unreadable))
                        return
                    }
                    completion(boundedResult(.url(url.absoluteString)))
                }
            }
        }
    }

    private static func streamResult(_ url: URL, typeIdentifier: String) -> ProviderResult {
        let hasSecurityScope = url.startAccessingSecurityScopedResource()
        defer {
            if hasSecurityScope {
                url.stopAccessingSecurityScopedResource()
            }
        }

        do {
            let data = try readBoundedRegularFile(url)
            return dataResult(data, as: resultFactory(for: typeIdentifier))
        } catch ProviderReadFailure.tooLarge {
            return .failure(.tooLarge)
        } catch {
            return .failure(.unreadable)
        }
    }

    private static func readBoundedRegularFile(_ url: URL) throws -> Data {
        let descriptor = Darwin.open(url.path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW)
        guard descriptor >= 0 else { throw ProviderReadFailure.unreadable }
        defer { Darwin.close(descriptor) }

        var information = stat()
        guard fstat(descriptor, &information) == 0,
              information.st_mode & mode_t(S_IFMT) == mode_t(S_IFREG),
              information.st_size >= 0 else {
            throw ProviderReadFailure.unreadable
        }
        if information.st_size > Int64(ShareHandoffPolicy.maxDecodedPayloadBytes) {
            throw ProviderReadFailure.tooLarge
        }

        var data = Data()
        var buffer = [UInt8](repeating: 0, count: ShareExtensionLimits.readBufferBytes)
        while data.count <= ShareHandoffPolicy.maxDecodedPayloadBytes {
            let requested = min(
                buffer.count,
                ShareHandoffPolicy.maxDecodedPayloadBytes + 1 - data.count
            )
            let count = buffer.withUnsafeMutableBytes { bytes -> Int in
                guard let baseAddress = bytes.baseAddress else { return -1 }
                return Darwin.read(descriptor, baseAddress, requested)
            }
            if count < 0 {
                guard errno == EINTR else { throw ProviderReadFailure.unreadable }
                continue
            }
            if count == 0 { break }
            data.append(contentsOf: buffer[0..<count])
            if data.count > ShareHandoffPolicy.maxDecodedPayloadBytes {
                throw ProviderReadFailure.tooLarge
            }
        }
        return data
    }

    private static func resultFactory(for typeIdentifier: String) -> (String) -> ProviderResult {
        if typeIdentifier == UTType.url.identifier {
            return ProviderResult.url
        }
        if typeIdentifier == UTType.text.identifier {
            return ProviderResult.text
        }
        return ProviderResult.calendar
    }

    private static func dataResult(
        _ data: Data,
        as makeResult: (String) -> ProviderResult,
    ) -> ProviderResult {
        guard data.count <= ShareHandoffPolicy.maxDecodedPayloadBytes else {
            return .failure(.tooLarge)
        }
        guard let value = String(data: data, encoding: .utf8) else {
            return .failure(.invalidUTF8)
        }
        return boundedResult(makeResult(value))
    }

    private static func boundedResult(_ result: ProviderResult) -> ProviderResult {
        switch result {
        case .text(let value), .url(let value), .calendar(let value):
            guard ShareHandoffPolicy.utf8CountUpTo(
                value,
                limit: ShareHandoffPolicy.maxDecodedPayloadBytes
            ) <= ShareHandoffPolicy.maxDecodedPayloadBytes else {
                return .failure(.tooLarge)
            }
            return result
        case .ignored, .failure:
            return result
        }
    }

    private static func aggregate(_ results: [ProviderResult]) -> PayloadCollectionResult {
        var payload = ShareHandoffPayload()
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
            if payload.hasContent, !ShareHandoffPolicy.accepts(payload) {
                return .failure(.tooLarge)
            }
        }
        return .payload(payload)
    }

    private func showPublishConfirmation(_ envelope: ShareHandoffEnvelope) {
        let alert = UIAlertController(
            title: localized("share.title", "OpenTasks"),
            message: localized(
                "share.confirm.message",
                "Save this shared item for review in OpenTasks?"
            ),
            preferredStyle: .alert,
        )
        alert.addAction(
            UIAlertAction(
                title: localized("share.cancel", "Cancel"),
                style: .cancel,
            ) { [weak self] _ in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        )
        alert.addAction(
            UIAlertAction(
                title: localized("share.save", "Save"),
                style: .default,
            ) { [weak self] _ in
                self?.publishForReview(envelope)
            }
        )
        present(alert, animated: true)
    }

    private func publishForReview(_ envelope: ShareHandoffEnvelope) {
        handoffQueue.async {
            do {
                _ = try ShareHandoffStore.publish(envelope)
            } catch {
                DispatchQueue.main.async {
                    self.showPublishFailure(envelope)
                }
                return
            }

            DispatchQueue.main.async {
                self.showSavedForReview()
            }
        }
    }

    private func showPublishFailure(_ envelope: ShareHandoffEnvelope) {
        let alert = UIAlertController(
            title: localized("share.title", "OpenTasks"),
            message: localized(
                "share.save.failure",
                "Unable to save this shared item. Please try again."
            ),
            preferredStyle: .alert,
        )
        alert.addAction(
            UIAlertAction(
                title: localized("share.cancel", "Cancel"),
                style: .cancel,
            ) { [weak self] _ in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        )
        alert.addAction(
            UIAlertAction(
                title: localized("share.retry", "Retry"),
                style: .default,
            ) { [weak self] _ in
                self?.publishForReview(envelope)
            }
        )
        present(alert, animated: true)
    }

    private func showSavedForReview() {
        let alert = UIAlertController(
            title: localized("share.title", "OpenTasks"),
            message: localized(
                "share.saved.message",
                "Saved. Open OpenTasks to review this shared item."
            ),
            preferredStyle: .alert,
        )
        alert.addAction(
            UIAlertAction(
                title: localized("share.done", "Done"),
                style: .default,
            ) { [weak self] _ in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        )
        present(alert, animated: true)
    }

    private func localized(_ key: String, _ fallback: String) -> String {
        NSLocalizedString(key, tableName: nil, bundle: .main, value: fallback, comment: "")
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
}

private enum PayloadCollectionResult {
    case payload(ShareHandoffPayload)
    case failure(ShareHandoffRejectionCode)
}

private enum ProviderResult {
    case ignored
    case text(String)
    case url(String)
    case calendar(String)
    case failure(ShareHandoffRejectionCode)
}

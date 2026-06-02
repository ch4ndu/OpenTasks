import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        collectPayload { [weak self] payload in
            guard let self else { return }
            guard payload.hasContent else {
                self.extensionContext?.completeRequest(returningItems: nil)
                return
            }
            self.openContainingApp(with: payload)
        }
    }

    private func collectPayload(completion: @escaping (SharedPayload) -> Void) {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            completion(SharedPayload())
            return
        }

        let providers = items.flatMap { $0.attachments ?? [] }
        let group = DispatchGroup()
        var payload = SharedPayload()

        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.calendarEvent.identifier) ||
                provider.registeredTypeIdentifiers.contains("com.apple.ical.ics") {
                loadCalendarEvent(from: provider, group: group) { content in
                    if let content, !content.isEmpty {
                        payload.icsContent = content
                    }
                }
                continue
            }

            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                group.enter()
                provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { item, _ in
                    defer { group.leave() }
                    if let url = item as? URL {
                        payload.url = url.absoluteString
                        payload.description = payload.description.ifBlank(url.absoluteString)
                    } else if let url = item as? NSURL {
                        let value = url.absoluteString ?? ""
                        payload.url = value
                        payload.description = payload.description.ifBlank(value)
                    } else if let value = item as? String {
                        payload.url = value
                        payload.description = payload.description.ifBlank(value)
                    }
                }
                continue
            }

            if provider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
                group.enter()
                provider.loadItem(forTypeIdentifier: UTType.text.identifier, options: nil) { item, _ in
                    defer { group.leave() }
                    if let value = item as? String, !value.isEmpty {
                        payload.description = payload.description.ifBlank(value)
                        payload.url = payload.url.ifBlank(Self.firstURL(in: value) ?? "")
                    }
                }
            }
        }

        group.notify(queue: .main) {
            completion(payload)
        }
    }

    private func loadCalendarEvent(
        from provider: NSItemProvider,
        group: DispatchGroup,
        completion: @escaping (String?) -> Void
    ) {
        let typeIdentifier = provider.registeredTypeIdentifiers.first {
            $0 == "com.apple.ical.ics" || UTType($0)?.conforms(to: .calendarEvent) == true
        } ?? UTType.calendarEvent.identifier

        group.enter()
        provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { item, _ in
            defer { group.leave() }

            if let content = item as? String {
                completion(content)
            } else if let data = item as? Data {
                completion(String(data: data, encoding: .utf8))
            } else if let url = item as? URL {
                completion(try? String(contentsOf: url, encoding: .utf8))
            } else if let url = item as? NSURL, let fileURL = url as URL? {
                completion(try? String(contentsOf: fileURL, encoding: .utf8))
            } else {
                completion(nil)
            }
        }
    }

    private func openContainingApp(with payload: SharedPayload) {
        var components = URLComponents()
        components.scheme = "opentasks"
        components.host = "share"
        components.queryItems = [
            URLQueryItem(name: "description", value: payload.description),
            URLQueryItem(name: "url", value: payload.url),
            URLQueryItem(name: "ics", value: payload.icsContent),
            URLQueryItem(name: "icsFileName", value: "shared.ics"),
        ]

        guard let url = components.url else {
            extensionContext?.completeRequest(returningItems: nil)
            return
        }

        extensionContext?.open(url) { [weak self] _ in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
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

private struct SharedPayload {
    var description: String = ""
    var url: String = ""
    var icsContent: String = ""

    var hasContent: Bool {
        !description.isEmpty || !url.isEmpty || !icsContent.isEmpty
    }
}

private extension String {
    func ifBlank(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}

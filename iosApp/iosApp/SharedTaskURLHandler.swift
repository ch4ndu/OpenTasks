import Foundation
import ComposeApp

private enum SharedURLLimits {
    static let maxPayloadBytes = 32 * 1024
    static let maxURLBytes = 64 * 1024
    static let maxFilenameBytes = 255
}

private enum SharedURLFailure: String {
    case tooLarge = "too_large"
    case tooManyItems = "too_many_items"
    case invalidUTF8 = "invalid_utf8"
    case unreadable = "unreadable"
}

func handleOpenTasksURL(_ url: URL) {
    guard url.scheme == "opentasks", url.host == "share" else { return }
    guard utf8CountUpTo(url.absoluteString, limit: SharedURLLimits.maxURLBytes) <= SharedURLLimits.maxURLBytes else {
        publishRejection(.tooLarge)
        return
    }
    guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }

    var description = ""
    var taskURL = ""
    var icsContent = ""
    var icsFileName = "shared.ics"
    var failure: SharedURLFailure?

    for item in components.queryItems ?? [] {
        switch item.name {
        case "description":
            guard let value = item.value else { return }
            description = value
        case "url":
            guard let value = item.value else { return }
            taskURL = value
        case "ics":
            guard let value = item.value else { return }
            icsContent = value
        case "icsFileName":
            guard let value = item.value else { return }
            icsFileName = value
        case "error":
            guard let value = item.value, let knownFailure = SharedURLFailure(rawValue: value) else {
                return
            }
            failure = knownFailure
        default:
            break
        }
    }

    if let failure {
        publishRejection(failure)
        return
    }
    guard !description.isEmpty || !taskURL.isEmpty || !icsContent.isEmpty else { return }
    guard utf8CountUpTo(icsFileName, limit: SharedURLLimits.maxFilenameBytes) <= SharedURLLimits.maxFilenameBytes,
          payloadFitsWithinLimit(
              description: description,
              url: taskURL,
              ics: icsContent,
              filename: icsFileName,
          ) else {
        publishRejection(.tooLarge)
        return
    }

    SharedTaskPayloadKt.publishSharedTaskPayload(
        id: Int64(Date().timeIntervalSince1970 * 1000),
        description: description,
        url: taskURL,
        icsContent: icsContent,
        icsFileName: icsFileName
    )
}

private func publishRejection(_ failure: SharedURLFailure) {
    SharedTaskPayloadKt.publishSharedTaskPayloadRejectionCode(
        id: Int64(Date().timeIntervalSince1970 * 1000),
        reason: failure.rawValue,
    )
}

private func payloadFitsWithinLimit(
    description: String,
    url: String,
    ics: String,
    filename: String,
) -> Bool {
    var remaining = SharedURLLimits.maxPayloadBytes
    for value in [description, url, ics, filename] {
        let byteCount = utf8CountUpTo(value, limit: remaining)
        if byteCount > remaining { return false }
        remaining -= byteCount
    }
    return true
}

private func utf8CountUpTo(_ value: String, limit: Int) -> Int {
    value.utf8.prefix(limit + 1).count
}

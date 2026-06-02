import Foundation
import ComposeApp

func handleOpenTasksURL(_ url: URL) {
    guard url.scheme == "opentasks", url.host == "share" else { return }
    guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }

    var description = ""
    var taskURL = ""
    var icsContent = ""
    var icsFileName = "shared.ics"

    components.queryItems?.forEach { item in
        switch item.name {
        case "description":
            description = item.value ?? ""
        case "url":
            taskURL = item.value ?? ""
        case "ics":
            icsContent = item.value ?? ""
        case "icsFileName":
            icsFileName = item.value ?? "shared.ics"
        default:
            break
        }
    }

    SharedTaskPayloadKt.publishSharedTaskPayload(
        id: Int64(Date().timeIntervalSince1970 * 1000),
        description: description,
        url: taskURL,
        icsContent: icsContent,
        icsFileName: icsFileName
    )
}

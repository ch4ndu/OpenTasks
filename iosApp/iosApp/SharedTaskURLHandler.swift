import ComposeApp
import Foundation

private enum SharedHandoffReceiver {
    static let queue = DispatchQueue(label: "com.udnahc.opentasks.share.claim")
    static var lastEventID: Int64 = 0

    static func enqueue(nonce: String) {
        queue.async {
            guard let eventID = nextEventID(),
                  SharedTaskPayloadKt.reserveSharedTaskPayload(id: eventID) else {
                return
            }
            defer {
                _ = SharedTaskPayloadKt.releaseSharedTaskPayloadReservation(id: eventID)
            }
            guard let envelope = try? ShareHandoffStore.claim(nonce: nonce) else { return }

            let published: Bool
            switch envelope {
            case .accepted(let payload):
                published = SharedTaskPayloadKt.publishReservedSharedTaskPayload(
                    id: eventID,
                    description: payload.description,
                    url: payload.url,
                    icsContent: payload.icsContent,
                    icsFileName: payload.icsFileName
                )
            case .rejected(let rejectionCode):
                published = SharedTaskPayloadKt.publishReservedSharedTaskPayloadRejectionCode(
                    id: eventID,
                    reason: rejectionCode.rawValue,
                )
            }
            guard published else { return }
        }
    }

    private static func nextEventID() -> Int64? {
        let (minimumNext, overflow) = lastEventID.addingReportingOverflow(1)
        guard !overflow else { return nil }

        let wallClockMilliseconds = Date().timeIntervalSince1970 * 1000
        let wallClockID: Int64?
        if wallClockMilliseconds.isFinite,
           wallClockMilliseconds >= 0,
           wallClockMilliseconds <= Double(Int64.max) {
            wallClockID = Int64(wallClockMilliseconds)
        } else {
            wallClockID = nil
        }

        let eventID = max(minimumNext, wallClockID ?? minimumNext)
        lastEventID = eventID
        return eventID
    }
}

func handleOpenTasksURL(_ url: URL) {
    guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
          components.scheme == "opentasks",
          components.host == "share",
          components.user == nil,
          components.password == nil,
          components.port == nil,
          components.path.isEmpty,
          components.fragment == nil,
          let queryItems = components.queryItems,
          queryItems.count == 1,
          queryItems[0].name == "nonce",
          let nonce = queryItems[0].value,
          ShareHandoffPolicy.acceptsNonce(nonce),
          url.absoluteString == "opentasks://share?nonce=\(nonce)" else {
        return
    }

    SharedHandoffReceiver.enqueue(nonce: nonce)
}

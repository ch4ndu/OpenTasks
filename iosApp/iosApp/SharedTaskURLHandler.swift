import ComposeApp
import Foundation

enum SharedHandoffReceiver {
    private static let intakeNotification = Notification.Name("OpenTasksSharedTaskIntakeReady")
    private static let queue = DispatchQueue(label: "com.udnahc.opentasks.share.claim")
    private static let stateLock = NSLock()
    private static var observer: NSObjectProtocol?
    private static var isRunning = false
    private static var isRequested = false
    private static var lastEventID: Int64 = 0

    static func start() {
        stateLock.lock()
        if observer == nil {
            observer = NotificationCenter.default.addObserver(
                forName: intakeNotification,
                object: nil,
                queue: nil,
            ) { _ in
                SharedHandoffReceiver.enqueueScan()
            }
        }
        stateLock.unlock()
        enqueueScan()
    }

    static func enqueueScan() {
        stateLock.lock()
        isRequested = true
        guard !isRunning else {
            stateLock.unlock()
            return
        }
        isRunning = true
        stateLock.unlock()

        queue.async {
            SharedHandoffReceiver.drainRequests()
        }
    }

    private static func drainRequests() {
        while true {
            stateLock.lock()
            guard isRequested else {
                isRunning = false
                stateLock.unlock()
                return
            }
            isRequested = false
            stateLock.unlock()
            scanOnce()
        }
    }

    private static func scanOnce() {
        guard SharedTaskPayloadKt.canScanSharedTaskIntake() else { return }

        let nonce: String
        do {
            guard let discovered = try ShareHandoffStore.discoverPendingNonce() else { return }
            nonce = discovered
        } catch {
            return
        }

        guard let eventID = nextEventID(),
              let ticket = SharedTaskPayloadKt.reserveSharedTaskIntake(id: eventID) else {
            return
        }
        defer {
            _ = SharedTaskPayloadKt.abandonSharedTaskIntakeReservation(id: eventID)
        }

        let envelope: ShareHandoffEnvelope
        do {
            guard let claimed = try ShareHandoffStore.claim(nonce: nonce) else { return }
            envelope = claimed
        } catch {
            return
        }

        switch envelope {
        case .accepted(let payload):
            _ = SharedTaskPayloadKt.publishSharedTaskIntake(
                id: eventID,
                readinessGeneration: ticket.readinessGeneration,
                accountId: ticket.accountId,
                boundaryEpoch: ticket.boundaryEpoch,
                description: payload.description,
                url: payload.url,
                icsContent: payload.icsContent,
                icsFileName: payload.icsFileName
            )
        case .rejected(let rejectionCode):
            _ = SharedTaskPayloadKt.publishSharedTaskIntakeRejectionCode(
                id: eventID,
                readinessGeneration: ticket.readinessGeneration,
                accountId: ticket.accountId,
                boundaryEpoch: ticket.boundaryEpoch,
                reason: rejectionCode.rawValue
            )
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

    SharedHandoffReceiver.enqueueScan()
}

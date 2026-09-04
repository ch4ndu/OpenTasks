import Foundation
import SwiftUI
import UserNotifications
import BackgroundTasks
import ComposeApp

private final class BackgroundSyncExecution {
    private let task: BGAppRefreshTask
    private let lock = NSLock()
    private var handle: BackgroundSyncHandle?
    private var isFinished = false
    private var isExpired = false

    init(task: BGAppRefreshTask) {
        self.task = task
    }

    func start() {
        let newHandle = BackgroundSyncHelper.shared.performSync { [self] success in
            finish(success: success.boolValue)
        }

        lock.lock()
        let shouldCancel = isExpired || isFinished
        if !isFinished {
            handle = newHandle
        }
        lock.unlock()

        if shouldCancel {
            newHandle.cancel()
        }
    }

    func expire() {
        lock.lock()
        guard !isFinished else {
            lock.unlock()
            return
        }
        isExpired = true
        let runningHandle = handle
        lock.unlock()

        runningHandle?.cancel()
        finish(success: false)
    }

    private func finish(success: Bool) {
        lock.lock()
        guard !isFinished else {
            lock.unlock()
            return
        }
        isFinished = true
        handle = nil
        lock.unlock()

        task.expirationHandler = nil
        task.setTaskCompleted(success: success)
    }
}

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    static let syncTaskIdentifier = "com.udnahc.opentasks.sync"
    private static let notificationEventIdKey = "notification_event_id"
    private static let notificationOccurrenceDeadlineUtcKey = "notification_occurrence_deadline_utc"
    private static let notificationAtUtcKey = "notification_at_utc"
    private static let notificationSemanticKey = "notification_semantic_key"
    private static let notificationAccountIdKey = "notification_account_id"
    private static let notificationBoundaryEpochKey = "notification_boundary_epoch"

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        MainViewControllerKt.initializeOpenTasksKoin()
        UNUserNotificationCenter.current().delegate = self

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.syncTaskIdentifier,
            using: nil
        ) { task in
            self.handleSyncTask(task as! BGAppRefreshTask)
        }

        scheduleNextSync()
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        NotificationBoundaryHelper.shared.currentBoundary { [weak self] accountId, boundaryEpochText in
            guard let accountId = accountId,
                  !accountId.isEmpty,
                  let boundaryEpochText = boundaryEpochText,
                  let boundaryEpoch = Int64(boundaryEpochText),
                  boundaryEpoch > 0 else { return }
            self?.removeOlderDeliveredRemindersPerEvent(
                accountId: accountId,
                boundaryEpoch: boundaryEpoch
            )
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        let eventId = userInfo[Self.notificationEventIdKey] as? String
        let accountId = userInfo[Self.notificationAccountIdKey] as? String
        let boundaryEpoch = Int64(userInfo[Self.notificationBoundaryEpochKey] as? String ?? "") ?? 0
        guard let eventId = eventId,
              !eventId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let accountId = accountId,
              !accountId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              boundaryEpoch > 0 else {
            completionHandler([])
            return
        }

        NotificationBoundaryHelper.shared.validate(
            accountId: accountId,
            boundaryEpoch: boundaryEpoch
        ) { [weak self] allowed in
            guard allowed.boolValue else {
                completionHandler([])
                return
            }
            self?.removeDeliveredReminders(
                for: eventId,
                accountId: accountId,
                boundaryEpoch: boundaryEpoch,
                keepingIdentifier: notification.request.identifier
            )
            completionHandler([.banner, .sound, .badge])
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        let eventId = userInfo[Self.notificationEventIdKey] as? String
        let accountId = userInfo[Self.notificationAccountIdKey] as? String
        let boundaryEpoch = Int64(userInfo[Self.notificationBoundaryEpochKey] as? String ?? "") ?? 0
        NotificationBoundaryHelper.shared.validate(
            accountId: accountId,
            boundaryEpoch: boundaryEpoch
        ) { [weak self] allowed in
            guard allowed.boolValue,
                  let self = self,
                  let eventId = eventId,
                  let accountId = accountId,
                  !eventId.isEmpty,
                  !accountId.isEmpty,
                  boundaryEpoch > 0 else {
                completionHandler()
                return
            }

            self.removeDeliveredReminders(
                for: eventId,
                accountId: accountId,
                boundaryEpoch: boundaryEpoch
            )
            NotificationDeepLinkKt.publishNotificationDeepLinkEvent(
                eventId: eventId,
                occurrenceDeadlineUtcMillis: Int64(userInfo[Self.notificationOccurrenceDeadlineUtcKey] as? String ?? "") ?? 0,
                notificationAtUtcMillis: Int64(userInfo[Self.notificationAtUtcKey] as? String ?? "") ?? 0,
                semanticKey: userInfo[Self.notificationSemanticKey] as? String,
                accountId: accountId,
                boundaryEpoch: boundaryEpoch
            )
            completionHandler()
        }
    }

    private func handleSyncTask(_ task: BGAppRefreshTask) {
        scheduleNextSync()

        let execution = BackgroundSyncExecution(task: task)
        task.expirationHandler = {
            execution.expire()
        }
        execution.start()
    }

    private func scheduleNextSync() {
        let request = BGAppRefreshTaskRequest(identifier: Self.syncTaskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 2 * 60 * 60) // 2 hours
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Background sync scheduling failed")
        }
    }

    private func eventId(for notification: UNNotification) -> String? {
        notification.request.content.userInfo[Self.notificationEventIdKey] as? String
    }

    private func accountId(for notification: UNNotification) -> String? {
        notification.request.content.userInfo[Self.notificationAccountIdKey] as? String
    }

    private func boundaryEpoch(for notification: UNNotification) -> Int64 {
        Int64(notification.request.content.userInfo[Self.notificationBoundaryEpochKey] as? String ?? "") ?? 0
    }

    private func notificationBelongsToBoundary(
        _ notification: UNNotification,
        expectedEventId: String,
        expectedAccountId: String,
        expectedBoundaryEpoch: Int64
    ) -> Bool {
        NotificationDeepLinkKt.notificationOwnershipMatches(
            eventId: eventId(for: notification),
            accountId: accountId(for: notification),
            boundaryEpoch: boundaryEpoch(for: notification),
            expectedEventId: expectedEventId,
            expectedAccountId: expectedAccountId,
            expectedBoundaryEpoch: expectedBoundaryEpoch
        )
    }

    private func removeDeliveredReminders(
        for eventId: String,
        accountId: String,
        boundaryEpoch: Int64,
        keepingIdentifier: String? = nil
    ) {
        UNUserNotificationCenter.current().getDeliveredNotifications { [weak self] notifications in
            guard let self = self else { return }
            let identifiers = notifications.compactMap { notification -> String? in
                guard self.notificationBelongsToBoundary(
                    notification,
                    expectedEventId: eventId,
                    expectedAccountId: accountId,
                    expectedBoundaryEpoch: boundaryEpoch
                ) else { return nil }
                guard notification.request.identifier != keepingIdentifier else { return nil }
                return notification.request.identifier
            }
            guard !identifiers.isEmpty else { return }
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: identifiers)
        }
    }

    private func removeOlderDeliveredRemindersPerEvent(accountId: String, boundaryEpoch: Int64) {
        UNUserNotificationCenter.current().getDeliveredNotifications { [weak self] notifications in
            guard let self = self else { return }

            var newestByOwnership: [DeliveredReminderOwnership: UNNotification] = [:]
            var identifiersToRemove: [String] = []

            for notification in notifications {
                guard let eventId = self.eventId(for: notification) else { continue }
                guard self.notificationBelongsToBoundary(
                    notification,
                    expectedEventId: eventId,
                    expectedAccountId: accountId,
                    expectedBoundaryEpoch: boundaryEpoch
                ) else { continue }
                let ownership = DeliveredReminderOwnership(
                    eventId: eventId,
                    accountId: accountId,
                    boundaryEpoch: boundaryEpoch
                )
                guard let newest = newestByOwnership[ownership] else {
                    newestByOwnership[ownership] = notification
                    continue
                }

                if notification.date > newest.date {
                    identifiersToRemove.append(newest.request.identifier)
                    newestByOwnership[ownership] = notification
                } else {
                    identifiersToRemove.append(notification.request.identifier)
                }
            }

            guard !identifiersToRemove.isEmpty else { return }
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: identifiersToRemove)
        }
    }
}

private struct DeliveredReminderOwnership: Hashable {
    let eventId: String
    let accountId: String
    let boundaryEpoch: Int64
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    handleOpenTasksURL(url)
                }
        }
    }
}

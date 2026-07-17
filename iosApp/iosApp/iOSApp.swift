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

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
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
        removeOlderDeliveredRemindersPerEvent()
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if let eventId = eventId(for: notification) {
            removeDeliveredReminders(for: eventId, keepingIdentifier: notification.request.identifier)
        }
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        if let eventId = userInfo[Self.notificationEventIdKey] as? String {
            removeDeliveredReminders(for: eventId)
            NotificationDeepLinkKt.publishNotificationDeepLinkEvent(
                eventId: eventId,
                occurrenceDeadlineUtcMillis: Int64(userInfo[Self.notificationOccurrenceDeadlineUtcKey] as? String ?? "") ?? 0,
                notificationAtUtcMillis: Int64(userInfo[Self.notificationAtUtcKey] as? String ?? "") ?? 0,
                semanticKey: userInfo[Self.notificationSemanticKey] as? String
            )
        }
        completionHandler()
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
            print("Could not schedule background sync: \(error)")
        }
    }

    private func eventId(for notification: UNNotification) -> String? {
        notification.request.content.userInfo[Self.notificationEventIdKey] as? String
    }

    private func removeDeliveredReminders(for eventId: String, keepingIdentifier: String? = nil) {
        UNUserNotificationCenter.current().getDeliveredNotifications { [weak self] notifications in
            guard let self = self else { return }
            let identifiers = notifications.compactMap { notification -> String? in
                guard self.eventId(for: notification) == eventId else { return nil }
                guard notification.request.identifier != keepingIdentifier else { return nil }
                return notification.request.identifier
            }
            guard !identifiers.isEmpty else { return }
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: identifiers)
        }
    }

    private func removeOlderDeliveredRemindersPerEvent() {
        UNUserNotificationCenter.current().getDeliveredNotifications { [weak self] notifications in
            guard let self = self else { return }

            var newestByEventId: [String: UNNotification] = [:]
            var identifiersToRemove: [String] = []

            for notification in notifications {
                guard let eventId = self.eventId(for: notification) else { continue }
                guard let newest = newestByEventId[eventId] else {
                    newestByEventId[eventId] = notification
                    continue
                }

                if notification.date > newest.date {
                    identifiersToRemove.append(newest.request.identifier)
                    newestByEventId[eventId] = notification
                } else {
                    identifiersToRemove.append(notification.request.identifier)
                }
            }

            guard !identifiersToRemove.isEmpty else { return }
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: identifiersToRemove)
        }
    }
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

import SwiftUI
import UserNotifications
import BackgroundTasks
import ComposeApp

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    static let syncTaskIdentifier = "com.udnahc.opentasks.sync"
    private static let notificationEventIdKey = "notification_event_id"

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
            NotificationDeepLinkKt.publishNotificationDeepLinkEventId(eventId: eventId)
        }
        completionHandler()
    }

    private func handleSyncTask(_ task: BGAppRefreshTask) {
        scheduleNextSync()

        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1

        let operation = BlockOperation {
            BackgroundSyncHelper.shared.performSync()
        }

        task.expirationHandler = {
            queue.cancelAllOperations()
        }

        operation.completionBlock = {
            task.setTaskCompleted(success: !operation.isCancelled)
        }

        queue.addOperation(operation)
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
        }
    }
}

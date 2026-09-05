import CoreFoundation
import Darwin
import Foundation
import Security

enum ShareHandoffPolicy {
    static let appGroupIdentifier = "group.com.udnahc.opentasks"
    static let maxDecodedPayloadBytes = 32 * 1024
    static let maxEnvelopeBytes = 64 * 1024
    static let maxEntries = 64
    static let maxAggregateBytes: Int64 = 4 * 1024 * 1024
    static let maxFilenameBytes = 255
    static let timeToLiveMillis: Int64 = 24 * 60 * 60 * 1000
    static let futureSkewMillis: Int64 = 60 * 1000
    static let defaultFilename = "shared.ics"

    static func accepts(_ payload: ShareHandoffPayload) -> Bool {
        guard payload.hasContent,
              utf8CountUpTo(payload.icsFileName, limit: maxFilenameBytes) <= maxFilenameBytes else {
            return false
        }

        var remaining = maxDecodedPayloadBytes
        for value in [payload.description, payload.url, payload.icsContent, payload.icsFileName] {
            let byteCount = utf8CountUpTo(value, limit: remaining)
            guard byteCount <= remaining else { return false }
            remaining -= byteCount
        }
        return true
    }

    static func utf8CountUpTo(_ value: String, limit: Int) -> Int {
        value.utf8.prefix(limit + 1).count
    }

    static func acceptsNonce(_ nonce: String) -> Bool {
        let bytes = Array(nonce.utf8)
        guard bytes.count == 64 else { return false }
        return bytes.allSatisfy { byte in
            (byte >= 48 && byte <= 57) || (byte >= 97 && byte <= 102)
        }
    }
}

enum ShareHandoffRejectionCode: String, CaseIterable {
    case tooLarge = "too_large"
    case tooManyItems = "too_many_items"
    case invalidUTF8 = "invalid_utf8"
    case unreadable = "unreadable"
}

struct ShareHandoffPayload {
    var description: String = ""
    var url: String = ""
    var icsContent: String = ""
    var icsFileName: String = ShareHandoffPolicy.defaultFilename

    var hasContent: Bool {
        !description.isEmpty || !url.isEmpty || !icsContent.isEmpty
    }
}

enum ShareHandoffEnvelope {
    case accepted(ShareHandoffPayload)
    case rejected(ShareHandoffRejectionCode)
}

enum ShareHandoffStore {
    static func publish(_ envelope: ShareHandoffEnvelope) throws -> String {
        let nonce = try generateNonce()

        return try withExclusiveLock { layout in
            let fileName = fileName(for: nonce)
            let temporaryURL = layout.temporary.appendingPathComponent(fileName, isDirectory: false)
            let pendingURL = layout.pending.appendingPathComponent(fileName, isDirectory: false)
            var ownsTemporary = false
            var ownsPending = false

            do {
                let createdAtMillis = try currentTimeMillis()
                let storedEnvelope = StoredEnvelope(
                    createdAtMillis: createdAtMillis,
                    envelope: envelope
                )
                let data = try encode(storedEnvelope)
                guard data.count <= ShareHandoffPolicy.maxEnvelopeBytes else {
                    throw StoreFailure.limitExceeded
                }

                let pendingEntries = try recoverAndPrune(
                    layout: layout,
                    nowMillis: createdAtMillis
                )
                guard pendingEntries.count < ShareHandoffPolicy.maxEntries else {
                    throw StoreFailure.quotaExceeded
                }

                var aggregateBytes: Int64 = 0
                for entry in pendingEntries.values {
                    let (nextTotal, overflow) = aggregateBytes.addingReportingOverflow(entry.size)
                    guard !overflow else { throw StoreFailure.quotaExceeded }
                    aggregateBytes = nextTotal
                }
                let newSize = Int64(data.count)
                guard aggregateBytes <= ShareHandoffPolicy.maxAggregateBytes - newSize else {
                    throw StoreFailure.quotaExceeded
                }
                guard pendingEntries[nonce] == nil else { throw StoreFailure.invalidState }

                try writeOwnerOnly(data, to: temporaryURL) {
                    ownsTemporary = true
                }
                try syncDirectory(layout.temporary)
                guard try linkNoReplace(from: temporaryURL, to: pendingURL) else {
                    throw StoreFailure.invalidState
                }
                ownsPending = true
                try syncDirectory(layout.pending)
                try removeEntries([temporaryURL], from: layout.temporary)
                ownsTemporary = false
                return nonce
            } catch let originalError {
                var compensationFailed = false
                if ownsPending {
                    do {
                        try removeOwnedEntry(pendingURL, from: layout.pending)
                        ownsPending = false
                    } catch {
                        compensationFailed = true
                    }
                }
                if ownsTemporary {
                    do {
                        try removeOwnedEntry(temporaryURL, from: layout.temporary)
                        ownsTemporary = false
                    } catch {
                        compensationFailed = true
                    }
                }
                if compensationFailed {
                    throw StoreFailure.inputOutput
                }
                throw originalError
            }
        }
    }

    static func claim(nonce: String) throws -> ShareHandoffEnvelope? {
        guard ShareHandoffPolicy.acceptsNonce(nonce) else { return nil }

        return try withExclusiveLock { layout in
            let nowMillis = try currentTimeMillis()
            let pendingEntries = try recoverAndPrune(layout: layout, nowMillis: nowMillis)
            guard let pendingEntry = pendingEntries[nonce] else { return nil }

            let claimedURL = layout.claimed.appendingPathComponent(fileName(for: nonce), isDirectory: false)
            guard try linkNoReplace(from: pendingEntry.url, to: claimedURL) else {
                try retire(nonce: nonce, layout: layout)
                return nil
            }
            try syncDirectory(layout.claimed)
            try removeEntries([pendingEntry.url], from: layout.pending)

            let claimedEnvelope: ShareHandoffEnvelope?
            do {
                let data = try readBoundedRegularFile(
                    at: claimedURL,
                    maxBytes: ShareHandoffPolicy.maxEnvelopeBytes
                )
                let storedEnvelope = try decode(data)
                claimedEnvelope = isFresh(storedEnvelope.createdAtMillis, nowMillis: nowMillis)
                    ? storedEnvelope.envelope
                    : nil
            } catch {
                claimedEnvelope = nil
            }

            try removeEntries([claimedURL], from: layout.claimed)
            return claimedEnvelope
        }
    }

    static func discoverPendingNonce() throws -> String? {
        try withExclusiveLock { layout in
            let entries = try recoverAndPrune(
                layout: layout,
                nowMillis: try currentTimeMillis()
            )
            var candidates: [(createdAtMillis: Int64, nonce: String)] = []
            candidates.reserveCapacity(entries.count)
            for entry in entries.values {
                let data = try readBoundedRegularFile(
                    at: entry.url,
                    maxBytes: ShareHandoffPolicy.maxEnvelopeBytes
                )
                let storedEnvelope = try decode(data)
                candidates.append((storedEnvelope.createdAtMillis, entry.nonce))
            }
            return candidates.min { first, second in
                if first.createdAtMillis != second.createdAtMillis {
                    return first.createdAtMillis < second.createdAtMillis
                }
                return first.nonce < second.nonce
            }?.nonce
        }
    }

}

private extension ShareHandoffStore {
    enum StoreFailure: Error {
        case unavailable
        case invalidState
        case unsafeEntry
        case invalidEnvelope
        case limitExceeded
        case quotaExceeded
        case inputOutput
    }

    struct StoredEnvelope {
        let createdAtMillis: Int64
        let envelope: ShareHandoffEnvelope
    }

    struct QueueEntry {
        let nonce: String
        let url: URL
        let size: Int64
    }

    struct QueueSnapshot {
        let temporary: [String: QueueEntry]
        let pending: [String: QueueEntry]
        let claimed: [String: QueueEntry]
    }

    struct Layout {
        let container: URL
        let lockFile: URL
        let quotaRoot: URL
        let temporary: URL
        let pending: URL
        let claimed: URL

        static func resolve() throws -> Layout {
            guard let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: ShareHandoffPolicy.appGroupIdentifier
            ) else {
                throw StoreFailure.unavailable
            }
            let quotaRoot = container.appendingPathComponent("share-handoffs", isDirectory: true)
            return Layout(
                container: container,
                lockFile: container.appendingPathComponent("share-handoffs.lock", isDirectory: false),
                quotaRoot: quotaRoot,
                temporary: quotaRoot.appendingPathComponent("temp", isDirectory: true),
                pending: quotaRoot.appendingPathComponent("pending", isDirectory: true),
                claimed: quotaRoot.appendingPathComponent("claimed", isDirectory: true)
            )
        }
    }

    static let acceptedKeys: Set<String> = [
        "version",
        "createdAtMillis",
        "kind",
        "description",
        "url",
        "icsContent",
        "icsFileName",
    ]
    static let rejectedKeys: Set<String> = [
        "version",
        "createdAtMillis",
        "kind",
        "rejectionCode",
    ]

    static func withExclusiveLock<T>(_ operation: (Layout) throws -> T) throws -> T {
        let layout = try Layout.resolve()
        try validateExistingDirectory(layout.container, requireOwnerOnly: false)

        let descriptor = Darwin.open(
            layout.lockFile.path,
            O_RDWR | O_CREAT | O_CLOEXEC | O_NOFOLLOW,
            mode_t(0o600)
        )
        guard descriptor >= 0 else { throw StoreFailure.unavailable }
        defer { Darwin.close(descriptor) }

        var information = stat()
        guard fstat(descriptor, &information) == 0,
              isRegular(information),
              hasOwnerOnlyPermissions(information),
              fchmod(descriptor, mode_t(0o600)) == 0 else {
            throw StoreFailure.unsafeEntry
        }

        while flock(descriptor, LOCK_EX) != 0 {
            guard errno == EINTR else { throw StoreFailure.inputOutput }
        }
        defer { flock(descriptor, LOCK_UN) }

        try ensureLayout(layout)
        return try operation(layout)
    }

    static func ensureLayout(_ layout: Layout) throws {
        let createdQuotaRoot = try ensureOwnerOnlyDirectory(layout.quotaRoot)
        if createdQuotaRoot {
            try syncDirectory(layout.container)
        }

        var createdStateDirectory = false
        for directory in [layout.temporary, layout.pending, layout.claimed] {
            createdStateDirectory = try ensureOwnerOnlyDirectory(directory) || createdStateDirectory
        }
        if createdStateDirectory {
            try syncDirectory(layout.quotaRoot)
        }

        let entries = try directoryNames(at: layout.quotaRoot, maximumCount: 3)
        guard Set(entries) == Set(["temp", "pending", "claimed"]) else {
            throw StoreFailure.unsafeEntry
        }
    }

    static func recoverAndPrune(layout: Layout, nowMillis: Int64) throws -> [String: QueueEntry] {
        let snapshot = try scan(layout: layout)

        if !snapshot.temporary.isEmpty {
            try removeEntries(snapshot.temporary.values.map(\.url), from: layout.temporary)
        }

        let pendingClaimedNonces = Set(snapshot.pending.keys).intersection(snapshot.claimed.keys)
        let interruptedClaimPending = pendingClaimedNonces.compactMap { snapshot.pending[$0]?.url }
        if !interruptedClaimPending.isEmpty {
            try removeEntries(interruptedClaimPending, from: layout.pending)
        }
        if !snapshot.claimed.isEmpty {
            try removeEntries(snapshot.claimed.values.map(\.url), from: layout.claimed)
        }

        var pendingEntries = try scanStateDirectory(layout.pending)
        var entriesToPrune: [URL] = []
        for entry in pendingEntries.values {
            do {
                let data = try readBoundedRegularFile(
                    at: entry.url,
                    maxBytes: ShareHandoffPolicy.maxEnvelopeBytes
                )
                let storedEnvelope = try decode(data)
                if !isFresh(storedEnvelope.createdAtMillis, nowMillis: nowMillis) {
                    entriesToPrune.append(entry.url)
                }
            } catch StoreFailure.invalidEnvelope {
                entriesToPrune.append(entry.url)
            } catch StoreFailure.limitExceeded {
                entriesToPrune.append(entry.url)
            }
        }
        if !entriesToPrune.isEmpty {
            try removeEntries(entriesToPrune, from: layout.pending)
            pendingEntries = try scanStateDirectory(layout.pending)
        }
        return pendingEntries
    }

    static func scan(layout: Layout) throws -> QueueSnapshot {
        QueueSnapshot(
            temporary: try scanStateDirectory(layout.temporary),
            pending: try scanStateDirectory(layout.pending),
            claimed: try scanStateDirectory(layout.claimed)
        )
    }

    static func scanStateDirectory(_ directory: URL) throws -> [String: QueueEntry] {
        try validateExistingDirectory(directory, requireOwnerOnly: true)
        var entries: [String: QueueEntry] = [:]
        for name in try directoryNames(
            at: directory,
            maximumCount: ShareHandoffPolicy.maxEntries
        ) {
            guard let nonce = nonce(fromFileName: name) else { throw StoreFailure.unsafeEntry }
            let url = directory.appendingPathComponent(name, isDirectory: false)
            let information = try fileInformation(at: url)
            guard isRegular(information),
                  hasOwnerOnlyPermissions(information),
                  information.st_size >= 0,
                  entries[nonce] == nil else {
                throw StoreFailure.unsafeEntry
            }
            entries[nonce] = QueueEntry(
                nonce: nonce,
                url: url,
                size: Int64(information.st_size)
            )
        }
        return entries
    }

    static func retire(nonce: String, layout: Layout) throws {
        let name = fileName(for: nonce)
        let pendingURL = layout.pending.appendingPathComponent(name, isDirectory: false)
        let claimedURL = layout.claimed.appendingPathComponent(name, isDirectory: false)

        if try pathExists(pendingURL) {
            try removeEntries([pendingURL], from: layout.pending)
        }
        if try pathExists(claimedURL) {
            try removeEntries([claimedURL], from: layout.claimed)
        }
    }

    static func writeOwnerOnly(
        _ data: Data,
        to url: URL,
        didCreate: () -> Void
    ) throws {
        guard !data.isEmpty else { throw StoreFailure.invalidEnvelope }
        let descriptor = Darwin.open(
            url.path,
            O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW,
            mode_t(0o600)
        )
        guard descriptor >= 0 else { throw StoreFailure.inputOutput }
        didCreate()
        defer { Darwin.close(descriptor) }

        var information = stat()
        guard fstat(descriptor, &information) == 0,
              isRegular(information),
              fchmod(descriptor, mode_t(0o600)) == 0 else {
            throw StoreFailure.unsafeEntry
        }

        try data.withUnsafeBytes { buffer in
            guard let baseAddress = buffer.baseAddress else { throw StoreFailure.inputOutput }
            var offset = 0
            while offset < buffer.count {
                let written = Darwin.write(
                    descriptor,
                    baseAddress.advanced(by: offset),
                    buffer.count - offset
                )
                if written < 0 {
                    guard errno == EINTR else { throw StoreFailure.inputOutput }
                    continue
                }
                guard written > 0 else { throw StoreFailure.inputOutput }
                offset += written
            }
        }
        try syncDescriptor(descriptor)
    }

    static func readBoundedRegularFile(at url: URL, maxBytes: Int) throws -> Data {
        let descriptor = Darwin.open(url.path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW)
        guard descriptor >= 0 else { throw StoreFailure.inputOutput }
        defer { Darwin.close(descriptor) }

        var information = stat()
        guard fstat(descriptor, &information) == 0,
              isRegular(information),
              hasOwnerOnlyPermissions(information),
              information.st_size >= 0 else {
            throw StoreFailure.unsafeEntry
        }
        if information.st_size > Int64(maxBytes) {
            throw StoreFailure.limitExceeded
        }

        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 8 * 1024)
        while data.count <= maxBytes {
            let requested = min(buffer.count, maxBytes + 1 - data.count)
            let count = buffer.withUnsafeMutableBytes { bytes in
                Darwin.read(descriptor, bytes.baseAddress, requested)
            }
            if count < 0 {
                guard errno == EINTR else { throw StoreFailure.inputOutput }
                continue
            }
            if count == 0 { break }
            data.append(contentsOf: buffer[0..<count])
            if data.count > maxBytes { throw StoreFailure.limitExceeded }
        }
        return data
    }

    static func linkNoReplace(from source: URL, to destination: URL) throws -> Bool {
        if Darwin.link(source.path, destination.path) == 0 {
            return true
        }
        if errno == EEXIST { return false }
        throw StoreFailure.inputOutput
    }

    static func removeEntries(_ urls: [URL], from directory: URL) throws {
        guard !urls.isEmpty else { return }
        for url in urls {
            let information = try fileInformation(at: url)
            guard isRegular(information), hasOwnerOnlyPermissions(information) else {
                throw StoreFailure.unsafeEntry
            }
        }
        for url in urls {
            guard Darwin.unlink(url.path) == 0 else { throw StoreFailure.inputOutput }
        }
        try syncDirectory(directory)
        for url in urls {
            try proveAbsent(url)
        }
    }

    static func removeOwnedEntry(_ url: URL, from directory: URL) throws {
        if try pathExists(url) {
            try removeEntries([url], from: directory)
        } else {
            try syncDirectory(directory)
            try proveAbsent(url)
        }
    }

    static func ensureOwnerOnlyDirectory(_ url: URL) throws -> Bool {
        var information = stat()
        if Darwin.lstat(url.path, &information) == 0 {
            guard isDirectory(information), hasOwnerOnlyPermissions(information) else {
                throw StoreFailure.unsafeEntry
            }
            return false
        }
        guard errno == ENOENT else { throw StoreFailure.inputOutput }
        if Darwin.mkdir(url.path, mode_t(0o700)) != 0, errno != EEXIST {
            throw StoreFailure.inputOutput
        }
        try validateExistingDirectory(url, requireOwnerOnly: true)
        return true
    }

    static func validateExistingDirectory(_ url: URL, requireOwnerOnly: Bool) throws {
        let information = try fileInformation(at: url)
        guard isDirectory(information),
              !requireOwnerOnly || hasOwnerOnlyPermissions(information) else {
            throw StoreFailure.unsafeEntry
        }
    }

    static func directoryNames(at url: URL, maximumCount: Int) throws -> [String] {
        guard maximumCount >= 0 else { throw StoreFailure.invalidState }

        var enumerationFailed = false
        guard let enumerator = FileManager.default.enumerator(
            at: url,
            includingPropertiesForKeys: nil,
            options: [.skipsSubdirectoryDescendants],
            errorHandler: { _, _ in
                enumerationFailed = true
                return false
            }
        ) else {
            throw StoreFailure.inputOutput
        }

        var names: [String] = []
        while let object = enumerator.nextObject() {
            guard !enumerationFailed,
                  names.count < maximumCount,
                  let entryURL = object as? URL else {
                throw StoreFailure.unsafeEntry
            }
            names.append(entryURL.lastPathComponent)
        }
        guard !enumerationFailed else { throw StoreFailure.inputOutput }
        return names.sorted()
    }

    static func fileInformation(at url: URL) throws -> stat {
        var information = stat()
        guard Darwin.lstat(url.path, &information) == 0 else {
            throw StoreFailure.inputOutput
        }
        return information
    }

    static func pathExists(_ url: URL) throws -> Bool {
        var information = stat()
        if Darwin.lstat(url.path, &information) == 0 {
            guard isRegular(information), hasOwnerOnlyPermissions(information) else {
                throw StoreFailure.unsafeEntry
            }
            return true
        }
        if errno == ENOENT { return false }
        throw StoreFailure.inputOutput
    }

    static func proveAbsent(_ url: URL) throws {
        var information = stat()
        if Darwin.lstat(url.path, &information) == 0 {
            throw StoreFailure.invalidState
        }
        guard errno == ENOENT else { throw StoreFailure.inputOutput }
    }

    static func syncDirectory(_ url: URL) throws {
        let descriptor = Darwin.open(
            url.path,
            O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW
        )
        guard descriptor >= 0 else { throw StoreFailure.inputOutput }
        defer { Darwin.close(descriptor) }

        var information = stat()
        guard fstat(descriptor, &information) == 0, isDirectory(information) else {
            throw StoreFailure.unsafeEntry
        }
        try syncDescriptor(descriptor)
    }

    static func syncDescriptor(_ descriptor: Int32) throws {
        while Darwin.fsync(descriptor) != 0 {
            guard errno == EINTR else { throw StoreFailure.inputOutput }
        }
    }

    static func isRegular(_ information: stat) -> Bool {
        information.st_mode & mode_t(S_IFMT) == mode_t(S_IFREG)
    }

    static func isDirectory(_ information: stat) -> Bool {
        information.st_mode & mode_t(S_IFMT) == mode_t(S_IFDIR)
    }

    static func hasOwnerOnlyPermissions(_ information: stat) -> Bool {
        information.st_mode & mode_t(0o077) == 0
    }

    static func encode(_ storedEnvelope: StoredEnvelope) throws -> Data {
        let object: [String: Any]
        switch storedEnvelope.envelope {
        case .accepted(let payload):
            guard ShareHandoffPolicy.accepts(payload) else { throw StoreFailure.invalidEnvelope }
            object = [
                "version": 1,
                "createdAtMillis": storedEnvelope.createdAtMillis,
                "kind": "accepted",
                "description": payload.description,
                "url": payload.url,
                "icsContent": payload.icsContent,
                "icsFileName": payload.icsFileName,
            ]
        case .rejected(let rejectionCode):
            object = [
                "version": 1,
                "createdAtMillis": storedEnvelope.createdAtMillis,
                "kind": "rejected",
                "rejectionCode": rejectionCode.rawValue,
            ]
        }

        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]),
              data.count <= ShareHandoffPolicy.maxEnvelopeBytes else {
            throw StoreFailure.invalidEnvelope
        }
        return data
    }

    static func decode(_ data: Data) throws -> StoredEnvelope {
        guard !data.isEmpty,
              data.count <= ShareHandoffPolicy.maxEnvelopeBytes,
              String(data: data, encoding: .utf8) != nil,
              let object = try? JSONSerialization.jsonObject(with: data),
              let dictionary = object as? [String: Any],
              exactInt64(dictionary["version"]) == 1,
              let createdAtMillis = exactInt64(dictionary["createdAtMillis"]),
              createdAtMillis >= 0,
              let kind = dictionary["kind"] as? String else {
            throw StoreFailure.invalidEnvelope
        }

        let storedEnvelope: StoredEnvelope
        switch kind {
        case "accepted":
            guard Set(dictionary.keys) == acceptedKeys,
                  let description = dictionary["description"] as? String,
                  let url = dictionary["url"] as? String,
                  let icsContent = dictionary["icsContent"] as? String,
                  let icsFileName = dictionary["icsFileName"] as? String else {
                throw StoreFailure.invalidEnvelope
            }
            let payload = ShareHandoffPayload(
                description: description,
                url: url,
                icsContent: icsContent,
                icsFileName: icsFileName
            )
            guard ShareHandoffPolicy.accepts(payload) else { throw StoreFailure.invalidEnvelope }
            storedEnvelope = StoredEnvelope(
                createdAtMillis: createdAtMillis,
                envelope: .accepted(payload)
            )
        case "rejected":
            guard Set(dictionary.keys) == rejectedKeys,
                  let rawCode = dictionary["rejectionCode"] as? String,
                  let rejectionCode = ShareHandoffRejectionCode(rawValue: rawCode) else {
                throw StoreFailure.invalidEnvelope
            }
            storedEnvelope = StoredEnvelope(
                createdAtMillis: createdAtMillis,
                envelope: .rejected(rejectionCode)
            )
        default:
            throw StoreFailure.invalidEnvelope
        }

        guard try encode(storedEnvelope) == data else { throw StoreFailure.invalidEnvelope }
        return storedEnvelope
    }

    static func exactInt64(_ value: Any?) -> Int64? {
        guard let number = value as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID(),
              !CFNumberIsFloatType(number) else {
            return nil
        }
        let text = number.stringValue
        guard let result = Int64(text), String(result) == text else { return nil }
        return result
    }

    static func isFresh(_ createdAtMillis: Int64, nowMillis: Int64) -> Bool {
        if createdAtMillis > nowMillis {
            let (difference, overflow) = createdAtMillis.subtractingReportingOverflow(nowMillis)
            return !overflow && difference <= ShareHandoffPolicy.futureSkewMillis
        }
        let (difference, overflow) = nowMillis.subtractingReportingOverflow(createdAtMillis)
        return !overflow && difference <= ShareHandoffPolicy.timeToLiveMillis
    }

    static func currentTimeMillis() throws -> Int64 {
        let milliseconds = Date().timeIntervalSince1970 * 1000
        guard milliseconds.isFinite,
              milliseconds >= 0,
              milliseconds <= Double(Int64.max) else {
            throw StoreFailure.invalidState
        }
        return Int64(milliseconds)
    }

    static func generateNonce() throws -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = bytes.withUnsafeMutableBytes { buffer -> Int32 in
            guard let baseAddress = buffer.baseAddress else { return errSecParam }
            return SecRandomCopyBytes(kSecRandomDefault, buffer.count, baseAddress)
        }
        guard status == errSecSuccess else { throw StoreFailure.unavailable }

        let digits = Array("0123456789abcdef".utf8)
        var encoded = [UInt8]()
        encoded.reserveCapacity(64)
        for byte in bytes {
            encoded.append(digits[Int(byte >> 4)])
            encoded.append(digits[Int(byte & 0x0f)])
        }
        guard let nonce = String(bytes: encoded, encoding: .utf8),
              ShareHandoffPolicy.acceptsNonce(nonce) else {
            throw StoreFailure.invalidState
        }
        return nonce
    }

    static func fileName(for nonce: String) -> String {
        "\(nonce).json"
    }

    static func nonce(fromFileName fileName: String) -> String? {
        guard fileName.hasSuffix(".json") else { return nil }
        let nonce = String(fileName.dropLast(5))
        return ShareHandoffPolicy.acceptsNonce(nonce) ? nonce : nil
    }
}

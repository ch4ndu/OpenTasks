package com.udnahc.opentasks.data.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.nio.channels.Channels
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JvmAuthTokenStore(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthTokenStore {
    private val delegate: AuthTokenStore = if (isMacOs()) {
        MacKeychainAuthTokenStore(ioDispatcher)
    } else {
        OwnerOnlyFileAuthTokenStore(ioDispatcher)
    }

    override val storageWarning: String?
        get() = delegate.storageWarning

    override suspend fun readActiveToken(): String? = delegate.readActiveToken()

    override suspend fun writeActiveToken(token: String) = delegate.writeActiveToken(token)

    override suspend fun clearActiveToken() = delegate.clearActiveToken()

    override suspend fun readPendingToken(): String? = delegate.readPendingToken()

    override suspend fun writePendingToken(token: String) = delegate.writePendingToken(token)

    override suspend fun clearPendingToken() = delegate.clearPendingToken()

    override suspend fun promotePendingToken() = delegate.promotePendingToken()

    override suspend fun clearAllTokens() = delegate.clearAllTokens()

    private fun isMacOs(): Boolean =
        (System.getProperty("os.name") ?: "").startsWith("Mac", ignoreCase = true)
}

private class MacKeychainAuthTokenStore(
    private val ioDispatcher: CoroutineDispatcher,
) : AuthTokenStore {
    override suspend fun readActiveToken(): String? = read(ACTIVE_ACCOUNT)

    override suspend fun writeActiveToken(token: String) = write(ACTIVE_ACCOUNT, token)

    override suspend fun clearActiveToken() = clear(ACTIVE_ACCOUNT)

    override suspend fun readPendingToken(): String? = read(PENDING_ACCOUNT)

    override suspend fun writePendingToken(token: String) = write(PENDING_ACCOUNT, token)

    override suspend fun clearPendingToken() = clear(PENDING_ACCOUNT)

    override suspend fun promotePendingToken() {
        withContext(ioDispatcher) {
            val pending = readOnDispatcher(PENDING_ACCOUNT) ?: return@withContext
            writeOnDispatcher(ACTIVE_ACCOUNT, pending)
            clearOnDispatcher(PENDING_ACCOUNT)
        }
    }

    override suspend fun clearAllTokens() {
        withContext(ioDispatcher) {
            clearOnDispatcher(ACTIVE_ACCOUNT)
            clearOnDispatcher(PENDING_ACCOUNT)
        }
    }

    private suspend fun read(account: String): String? = withContext(ioDispatcher) {
        readOnDispatcher(account)
    }

    private suspend fun write(account: String, token: String) {
        require(token.isNotBlank()) { "Auth token must not be blank" }
        withContext(ioDispatcher) { writeOnDispatcher(account, token) }
    }

    private suspend fun clear(account: String) {
        withContext(ioDispatcher) { clearOnDispatcher(account) }
    }

    private fun readOnDispatcher(account: String): String? {
        return withKeyring { keyring ->
            try {
                val token = keyring.getPassword(SERVICE, account) ?: return@withKeyring null
                token.takeIf { it.isNotBlank() }
                    ?: throw SecureTokenStoreException("macOS Keychain returned an empty token")
            } catch (error: PasswordAccessException) {
                if (error.isMissingCredential()) null
                else throw SecureTokenStoreException("macOS Keychain read failed", error)
            }
        }
    }

    private fun writeOnDispatcher(account: String, token: String) {
        withKeyring { keyring ->
            try {
                keyring.setPassword(SERVICE, account, token)
            } catch (error: PasswordAccessException) {
                throw SecureTokenStoreException("macOS Keychain write failed", error)
            }
        }
    }

    private fun clearOnDispatcher(account: String) {
        withKeyring { keyring ->
            try {
                keyring.deletePassword(SERVICE, account)
            } catch (error: PasswordAccessException) {
                if (!error.isMissingCredential()) {
                    throw SecureTokenStoreException("macOS Keychain cleanup failed", error)
                }
            }
        }
    }

    private fun <T> withKeyring(block: (Keyring) -> T): T {
        try {
            return Keyring.create().use(block)
        } catch (error: SecureTokenStoreException) {
            throw error
        } catch (error: Throwable) {
            throw SecureTokenStoreException("macOS Keychain is unavailable", error)
        }
    }

    private fun PasswordAccessException.isMissingCredential(): Boolean =
        message?.startsWith("No stored credentials match") == true ||
            message?.startsWith("No password to delete") == true

    private companion object {
        const val SERVICE = "com.udnahc.opentasks.auth"
        const val ACTIVE_ACCOUNT = "active"
        const val PENDING_ACCOUNT = "pending"
    }
}

private class OwnerOnlyFileAuthTokenStore(
    private val ioDispatcher: CoroutineDispatcher,
) : AuthTokenStore {
    private val lock = Any()
    private val tokenDirectory = resolveTokenDirectory()
    private val tokenFile = tokenDirectory.resolve(TOKEN_FILE_NAME)
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        exceptionsWithDebugInfo = false
    }

    override val storageWarning: String =
        "Desktop sign-in uses an owner-only token file on this platform; native credential storage is unavailable."

    override suspend fun readActiveToken(): String? = readState().active

    override suspend fun writeActiveToken(token: String) {
        require(token.isNotBlank()) { "Auth token must not be blank" }
        update { it.copy(active = token) }
    }

    override suspend fun clearActiveToken() {
        update { it.copy(active = null) }
    }

    override suspend fun readPendingToken(): String? = readState().pending

    override suspend fun writePendingToken(token: String) {
        require(token.isNotBlank()) { "Auth token must not be blank" }
        update { it.copy(pending = token) }
    }

    override suspend fun clearPendingToken() {
        update { it.copy(pending = null) }
    }

    override suspend fun promotePendingToken() {
        update { state ->
            val pending = state.pending ?: return@update state
            state.copy(active = pending, pending = null)
        }
    }

    override suspend fun clearAllTokens() {
        withContext(ioDispatcher) {
            synchronized(lock) {
                deleteTokenFileIfPresent()
            }
        }
    }

    private suspend fun readState(): TokenFile = withContext(ioDispatcher) {
        synchronized(lock) {
            readStateWithoutContext()
        }
    }

    private suspend fun update(transform: (TokenFile) -> TokenFile) {
        withContext(ioDispatcher) {
            synchronized(lock) {
                val next = transform(readStateWithoutContext())
                if (next.active == null && next.pending == null) {
                    deleteTokenFileIfPresent()
                    return@synchronized
                }
                writeState(next)
            }
        }
    }

    private fun readStateWithoutContext(): TokenFile {
        if (!ensureTokenDirectory(createIfMissing = false)) return TokenFile()
        val identity = validateTokenFileIfPresent() ?: return TokenFile()
        validateOwnerOnlyFile(tokenFile, TOKEN_FILE_UNSAFE, identity)
        val encoded = secureFileOperation(READ_FAILED) {
            Files.newByteChannel(
                tokenFile,
                setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
            ).use { channel ->
                Channels.newInputStream(channel)
                    .bufferedReader(Charsets.UTF_8)
                    .use { reader -> reader.readText() }
            }
        }
        validateOwnerOnlyFile(tokenFile, TOKEN_FILE_UNSAFE, identity)
        return try {
            json.decodeFromString(encoded)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw SecureTokenStoreException(INVALID_FILE)
        }
    }

    private fun writeState(state: TokenFile) {
        ensureTokenDirectory(createIfMissing = true)
        var temporary: Path? = null
        var temporaryIdentity: Any? = null
        var published = false
        try {
            temporary = secureFileOperation(WRITE_FAILED) {
                Files.createTempFile(tokenDirectory, TOKEN_FILE_NAME, ".tmp")
            }
            val ownedIdentity = captureRegularFileIdentity(temporary, TEMPORARY_FILE_UNSAFE)
            temporaryIdentity = ownedIdentity
            secureNewOwnerOnlyFile(temporary, TEMPORARY_FILE_UNSAFE, ownedIdentity)

            val encoded = try {
                json.encodeToString(state)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                throw SecureTokenStoreException(WRITE_FAILED)
            }
            validateOwnerOnlyFile(temporary, TEMPORARY_FILE_UNSAFE, ownedIdentity)
            secureFileOperation(WRITE_FAILED) {
                Files.newByteChannel(
                    temporary,
                    setOf<OpenOption>(
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS,
                    ),
                ).use { channel ->
                    Channels.newOutputStream(channel)
                        .bufferedWriter(Charsets.UTF_8)
                        .use { writer -> writer.write(encoded) }
                }
            }
            validateOwnerOnlyFile(temporary, TEMPORARY_FILE_UNSAFE, ownedIdentity)
            validateTokenFileIfPresent()
            validateOwnerOnlyFile(temporary, TEMPORARY_FILE_UNSAFE, ownedIdentity)
            moveIntoPlace(temporary)
            published = true
            temporary = null
            validateOwnerOnlyFile(tokenFile, TOKEN_FILE_UNSAFE, ownedIdentity)
        } catch (error: CancellationException) {
            cleanupFailedWrite(temporary, temporaryIdentity, published, preserveCancellation = true)
            throw error
        } catch (error: SecureTokenStoreException) {
            cleanupFailedWrite(temporary, temporaryIdentity, published, preserveCancellation = false)
            throw error
        } catch (_: Exception) {
            cleanupFailedWrite(temporary, temporaryIdentity, published, preserveCancellation = false)
            throw SecureTokenStoreException(WRITE_FAILED)
        }
    }

    private fun moveIntoPlace(temporary: Path) {
        secureFileOperation(WRITE_FAILED) {
            try {
                Files.move(
                    temporary,
                    tokenFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, tokenFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun cleanupFailedWrite(
        temporary: Path?,
        temporaryIdentity: Any?,
        published: Boolean,
        preserveCancellation: Boolean,
    ) {
        try {
            if (published) {
                val expectedIdentity = temporaryIdentity
                    ?: throw SecureTokenStoreException(CLEANUP_FAILED)
                deleteOwnedPathIfPresent(tokenFile, expectedIdentity)
            }
            if (temporary != null) {
                val expectedIdentity = temporaryIdentity
                    ?: throw SecureTokenStoreException(CLEANUP_FAILED)
                deleteOwnedPathIfPresent(temporary, expectedIdentity)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecureTokenStoreException) {
            if (!preserveCancellation) throw error
        }
    }

    private fun deleteTokenFileIfPresent() {
        if (!ensureTokenDirectory(createIfMissing = false)) return
        val identity = validateTokenFileIfPresent() ?: return
        deleteOwnedPathIfPresent(tokenFile, identity)
    }

    private fun deleteOwnedPathIfPresent(path: Path, expectedIdentity: Any) {
        ensureTokenDirectory(createIfMissing = false)
        val attributes = readAttributesOrNull(path, CLEANUP_FAILED) ?: return
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw SecureTokenStoreException(CLEANUP_UNSAFE)
        }
        validateOwnerOnlyFile(path, CLEANUP_UNSAFE, expectedIdentity)
        secureFileOperation(CLEANUP_FAILED) { Files.delete(path) }
        if (readAttributesOrNull(path, CLEANUP_FAILED) != null) {
            throw SecureTokenStoreException(CLEANUP_FAILED)
        }
    }

    private fun validateTokenFileIfPresent(): Any? {
        val attributes = readAttributesOrNull(tokenFile, READ_FAILED) ?: return null
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw SecureTokenStoreException(TOKEN_FILE_UNSAFE)
        }
        return validateOwnerOnlyFile(tokenFile, TOKEN_FILE_UNSAFE)
    }

    private fun ensureTokenDirectory(createIfMissing: Boolean): Boolean {
        var attributes = readAttributesOrNull(tokenDirectory, DIRECTORY_FAILED)
        if (attributes == null) {
            if (!createIfMissing) return false
            secureFileOperation(DIRECTORY_FAILED) {
                val parent = tokenDirectory.parent
                    ?: throw SecureTokenStoreException(DIRECTORY_FAILED)
                val posixView = Files.getFileAttributeView(
                    parent,
                    PosixFileAttributeView::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                if (posixView != null) {
                    Files.createDirectory(
                        tokenDirectory,
                        PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS),
                    )
                } else {
                    Files.createDirectory(tokenDirectory)
                }
            }
            attributes = readAttributesOrNull(tokenDirectory, DIRECTORY_FAILED)
                ?: throw SecureTokenStoreException(DIRECTORY_FAILED)
        }
        if (!attributes.isDirectory || attributes.isSymbolicLink) {
            throw SecureTokenStoreException(DIRECTORY_UNSAFE)
        }
        applyAndVerifyOwnerOnly(tokenDirectory, isDirectory = true, DIRECTORY_SECURITY_FAILED)
        return true
    }

    private fun secureNewOwnerOnlyFile(
        path: Path,
        failureMessage: String,
        expectedIdentity: Any,
    ) {
        if (captureRegularFileIdentity(path, failureMessage) != expectedIdentity) {
            throw SecureTokenStoreException(failureMessage)
        }
        applyAndVerifyOwnerOnly(path, isDirectory = false, failureMessage)
        validateOwnerOnlyFile(path, failureMessage, expectedIdentity)
    }

    private fun validateOwnerOnlyFile(
        path: Path,
        failureMessage: String,
        expectedIdentity: Any? = null,
    ): Any {
        val identity = captureRegularFileIdentity(path, failureMessage)
        if (expectedIdentity != null && identity != expectedIdentity) {
            throw SecureTokenStoreException(failureMessage)
        }
        verifyOwnerOnly(path, isDirectory = false, failureMessage)
        val verifiedAttributes = readAttributesOrNull(path, failureMessage)
            ?: throw SecureTokenStoreException(failureMessage)
        if (
            !verifiedAttributes.isRegularFile ||
            verifiedAttributes.isSymbolicLink ||
            verifiedAttributes.fileKey() != identity
        ) {
            throw SecureTokenStoreException(failureMessage)
        }
        return identity
    }

    private fun captureRegularFileIdentity(path: Path, failureMessage: String): Any {
        val attributes = readAttributesOrNull(path, failureMessage)
            ?: throw SecureTokenStoreException(failureMessage)
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw SecureTokenStoreException(failureMessage)
        }
        return attributes.fileKey()
            ?: throw SecureTokenStoreException(failureMessage)
    }

    private fun applyAndVerifyOwnerOnly(
        path: Path,
        isDirectory: Boolean,
        failureMessage: String,
    ) {
        secureFileOperation(failureMessage) {
            val posixView = Files.getFileAttributeView(
                path,
                PosixFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            if (posixView != null) {
                posixView.setPermissions(if (isDirectory) DIRECTORY_PERMISSIONS else FILE_PERMISSIONS)
            } else {
                val aclView = Files.getFileAttributeView(
                    path,
                    AclFileAttributeView::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                ) ?: throw SecureTokenStoreException(failureMessage)
                val owner = aclView.owner
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(if (isDirectory) DIRECTORY_ACL_PERMISSIONS else FILE_ACL_PERMISSIONS)
                    .build()
                aclView.setAcl(listOf(entry))
            }
            verifyOwnerOnly(path, isDirectory, failureMessage)
        }
    }

    private fun verifyOwnerOnly(
        path: Path,
        isDirectory: Boolean,
        failureMessage: String,
    ) {
        secureFileOperation(failureMessage) {
            val posixView = Files.getFileAttributeView(
                path,
                PosixFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            if (posixView != null) {
                val expected = if (isDirectory) DIRECTORY_PERMISSIONS else FILE_PERMISSIONS
                if (posixView.readAttributes().permissions() != expected) {
                    throw SecureTokenStoreException(failureMessage)
                }
                return@secureFileOperation
            }

            val aclView = Files.getFileAttributeView(
                path,
                AclFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            ) ?: throw SecureTokenStoreException(failureMessage)
            val expectedPermissions = if (isDirectory) DIRECTORY_ACL_PERMISSIONS else FILE_ACL_PERMISSIONS
            val owner = aclView.owner
            val entries = aclView.acl
            val entry = entries.singleOrNull()
            if (
                entry == null ||
                entry.type() != AclEntryType.ALLOW ||
                entry.principal() != owner ||
                entry.flags().isNotEmpty() ||
                entry.permissions() != expectedPermissions
            ) {
                throw SecureTokenStoreException(failureMessage)
            }
        }
    }

    private fun readAttributesOrNull(path: Path, failureMessage: String): BasicFileAttributes? {
        return try {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw SecureTokenStoreException(failureMessage)
        }
    }

    private fun resolveTokenDirectory(): Path {
        return try {
            val userHome = System.getProperty("user.home")
                ?.takeIf { it.isNotBlank() }
                ?: throw SecureTokenStoreException(DIRECTORY_FAILED)
            Path.of(userHome, DIRECTORY_NAME)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecureTokenStoreException) {
            throw error
        } catch (_: Exception) {
            throw SecureTokenStoreException(DIRECTORY_FAILED)
        }
    }

    private fun <T> secureFileOperation(failureMessage: String, operation: () -> T): T {
        return try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecureTokenStoreException) {
            throw error
        } catch (_: Exception) {
            throw SecureTokenStoreException(failureMessage)
        }
    }

    @Serializable
    private data class TokenFile(
        val active: String? = null,
        val pending: String? = null,
    )

    private companion object {
        const val DIRECTORY_NAME = ".opentasks"
        const val TOKEN_FILE_NAME = "account-tokens.json"
        const val DIRECTORY_FAILED = "Desktop auth token directory is unavailable"
        const val DIRECTORY_UNSAFE = "Desktop auth token directory is unsafe"
        const val DIRECTORY_SECURITY_FAILED = "Desktop auth token directory security check failed"
        const val TOKEN_FILE_UNSAFE = "Desktop auth token file is unsafe"
        const val TEMPORARY_FILE_UNSAFE = "Desktop auth token temporary file is unsafe"
        const val READ_FAILED = "Desktop auth token file read failed"
        const val WRITE_FAILED = "Desktop auth token file write failed"
        const val INVALID_FILE = "Desktop auth token file is invalid"
        const val CLEANUP_FAILED = "Desktop auth token cleanup failed"
        const val CLEANUP_UNSAFE = "Desktop auth token cleanup target is unsafe"

        val DIRECTORY_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
        val FILE_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )
        val FILE_ACL_PERMISSIONS = setOf(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.WRITE_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.WRITE_ATTRIBUTES,
            AclEntryPermission.DELETE,
            AclEntryPermission.READ_ACL,
            AclEntryPermission.WRITE_ACL,
            AclEntryPermission.WRITE_OWNER,
            AclEntryPermission.SYNCHRONIZE,
        )
        val DIRECTORY_ACL_PERMISSIONS = FILE_ACL_PERMISSIONS + setOf(
            AclEntryPermission.EXECUTE,
            AclEntryPermission.DELETE_CHILD,
        )
    }
}

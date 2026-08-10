package com.udnahc.opentasks.data.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val tokenFile = File(
        System.getProperty("user.home") ?: throw SecureTokenStoreException("Desktop user directory is unavailable"),
        ".opentasks/account-tokens.json",
    )
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

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
                if (tokenFile.exists() && !tokenFile.delete()) {
                    throw SecureTokenStoreException("Desktop auth token cleanup failed")
                }
            }
        }
    }

    private suspend fun readState(): TokenFile = withContext(ioDispatcher) {
        synchronized(lock) {
            if (!tokenFile.isFile) return@synchronized TokenFile()
            try {
                json.decodeFromString<TokenFile>(tokenFile.readText())
            } catch (error: Throwable) {
                throw SecureTokenStoreException("Desktop auth token file is invalid", error)
            }
        }
    }

    private suspend fun update(transform: (TokenFile) -> TokenFile) {
        withContext(ioDispatcher) {
            synchronized(lock) {
                val next = transform(readStateWithoutContext())
                if (next.active == null && next.pending == null) {
                    if (tokenFile.exists() && !tokenFile.delete()) {
                        throw SecureTokenStoreException("Desktop auth token cleanup failed")
                    }
                    return@synchronized
                }
                val parent = tokenFile.parentFile
                    ?: throw SecureTokenStoreException("Desktop auth token directory is unavailable")
                if (!parent.exists() && !parent.mkdirs()) {
                    throw SecureTokenStoreException("Desktop auth token directory could not be created")
                }
                val temporary = Files.createTempFile(parent.toPath(), tokenFile.name, ".tmp").toFile()
                try {
                    temporary.writeText(json.encodeToString(next))
                    applyOwnerOnlyPermissions(temporary)
                    moveIntoPlace(temporary)
                    applyOwnerOnlyPermissions(tokenFile)
                } catch (error: SecureTokenStoreException) {
                    throw error
                } catch (error: Throwable) {
                    throw SecureTokenStoreException("Desktop auth token file write failed", error)
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }
        }
    }

    private fun readStateWithoutContext(): TokenFile {
        if (!tokenFile.isFile) return TokenFile()
        return try {
            json.decodeFromString(tokenFile.readText())
        } catch (error: Throwable) {
            throw SecureTokenStoreException("Desktop auth token file is invalid", error)
        }
    }

    private fun moveIntoPlace(temporary: File) {
        try {
            Files.move(
                temporary.toPath(),
                tokenFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), tokenFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        val path = file.toPath()
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }.onFailure {
            val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            if (acl != null) {
                val owner = acl.owner
                val permissions = setOf(
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.DELETE,
                )
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(permissions)
                    .build()
                acl.setAcl(listOf(entry))
            }
        }
    }

    @Serializable
    private data class TokenFile(
        val active: String? = null,
        val pending: String? = null,
    )
}

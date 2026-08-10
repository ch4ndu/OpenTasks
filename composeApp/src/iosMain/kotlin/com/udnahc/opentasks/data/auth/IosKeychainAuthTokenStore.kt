package com.udnahc.opentasks.data.auth

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainAuthTokenStore(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    private fun readOnDispatcher(account: String): String? = memScoped {
        val query = keychainDictionary(account, includeData = true)
            ?: throw SecureTokenStoreException("iOS Keychain query could not be created")
        try {
            val result = alloc<COpaquePointerVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            val resultValue = result.value
            when {
                status == errSecItemNotFound -> null
                status != errSecSuccess -> throw SecureTokenStoreException("iOS Keychain read failed")
                resultValue == null -> throw SecureTokenStoreException("iOS Keychain returned no token")
                else -> {
                    val data = CFBridgingRelease(resultValue) as? NSData
                        ?: throw SecureTokenStoreException("iOS Keychain returned invalid data")
                    val bytes = data.bytes
                        ?: throw SecureTokenStoreException("iOS Keychain returned empty data")
                    bytes.reinterpret<ByteVar>().readBytes(data.length.toInt()).decodeToString().takeIf { it.isNotBlank() }
                        ?: throw SecureTokenStoreException("iOS Keychain returned an empty token")
                }
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun writeOnDispatcher(account: String, token: String) = memScoped {
        val match = keychainDictionary(account, includeData = false)
            ?: throw SecureTokenStoreException("iOS Keychain query could not be created")
        val valueData = token.toNSData()
        val attributes = createDictionary(1)
            ?: throw SecureTokenStoreException("iOS Keychain update dictionary could not be created")
        try {
            setBridgedValue(attributes, kSecValueData, valueData)
            val updateStatus = SecItemUpdate(match, attributes)
            if (updateStatus == errSecSuccess) return@memScoped
            if (updateStatus != errSecItemNotFound && updateStatus != errSecDuplicateItem) {
                throw SecureTokenStoreException("iOS Keychain update failed")
            }
            val add = keychainDictionary(account, includeData = false, valueData = valueData)
                ?: throw SecureTokenStoreException("iOS Keychain add dictionary could not be created")
            try {
                val addStatus = SecItemAdd(add, null)
                if (addStatus != errSecSuccess && addStatus != errSecDuplicateItem) {
                    throw SecureTokenStoreException("iOS Keychain write failed")
                }
                if (addStatus == errSecDuplicateItem) {
                    val retryStatus = SecItemUpdate(match, attributes)
                    if (retryStatus != errSecSuccess) {
                        throw SecureTokenStoreException("iOS Keychain replacement failed")
                    }
                }
            } finally {
                CFRelease(add)
            }
        } finally {
            CFRelease(attributes)
            CFRelease(match)
        }
    }

    private fun clearOnDispatcher(account: String) = memScoped {
        val query = keychainDictionary(account, includeData = false)
            ?: throw SecureTokenStoreException("iOS Keychain query could not be created")
        try {
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw SecureTokenStoreException("iOS Keychain cleanup failed")
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun keychainDictionary(
        account: String,
        includeData: Boolean,
        valueData: NSData? = null,
    ): platform.CoreFoundation.CFMutableDictionaryRef? {
        val dictionary = createDictionary(6) ?: return null
        CFDictionarySetValue(dictionary, kSecClass, kSecClassGenericPassword)
        setBridgedValue(dictionary, kSecAttrService, SERVICE)
        setBridgedValue(dictionary, kSecAttrAccount, account)
        if (valueData != null) {
            CFDictionarySetValue(dictionary, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        if (includeData) {
            CFDictionarySetValue(dictionary, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
        }
        if (valueData != null) setBridgedValue(dictionary, kSecValueData, valueData)
        return dictionary
    }

    private fun createDictionary(capacity: Long): platform.CoreFoundation.CFMutableDictionaryRef? =
        CFDictionaryCreateMutable(
            allocator = null,
            capacity = capacity,
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        )

    private fun setBridgedValue(
        dictionary: platform.CoreFoundation.CFMutableDictionaryRef,
        key: CPointer<out CPointed>?,
        value: Any,
    ) {
        if (key == null) return
        val retained = CFBridgingRetain(value) ?: return
        CFDictionarySetValue(dictionary, key, retained)
        CFRelease(retained)
    }

    private fun String.toNSData(): NSData = encodeToByteArray().toNSData()

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }

    private companion object {
        const val SERVICE = "com.udnahc.opentasks.auth"
        const val ACTIVE_ACCOUNT = "active"
        const val PENDING_ACCOUNT = "pending"
    }
}

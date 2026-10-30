import Foundation
import Security
import AyudixShared

/// The enrollment secret is replaced by a device credential before this store is used.
final class AppleConnectionStore: NSObject, SecureConnectionStore {
    private let service = "pe.ayudix.link.ios.connections"
    private let account = "registry-v1"
    private var readFailed = false
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account,
         kSecAttrSynchronizable as String: false]
    }
    func load() -> String? {
        if readFailed { return "KEYCHAIN_UNAVAILABLE_DO_NOT_REPLACE" }
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data,
              let text = String(data: data, encoding: .utf8) else {
            // A nonempty invalid payload locks writes in the coordinator, preserving old data.
            readFailed = true; return "KEYCHAIN_UNAVAILABLE_DO_NOT_REPLACE"
        }
        return text
    }
    func save(value: String) -> Bool {
        guard !readFailed else { return false }
        guard let data = value.data(using: .utf8) else { return false }
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        return SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess
    }
    func deviceIdentity() -> String {
        // ThisDeviceOnly prevents a restored backup from cloning another iPhone's identity.
        var identityQuery = query
        identityQuery[kSecAttrAccount as String] = "device-id-v1"
        var request = identityQuery
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data,
           let text = String(data: data, encoding: .utf8), UUID(uuidString: text) != nil { return text }
        guard status == errSecItemNotFound else { readFailed = true; return "" }
        let value = UUID().uuidString.lowercased()
        identityQuery[kSecValueData as String] = value.data(using: .utf8)!
        identityQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(identityQuery as CFDictionary, nil) == errSecSuccess else { readFailed = true; return "" }
        return value
    }
}

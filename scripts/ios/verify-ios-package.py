"""Inspect an unsigned device IPA without executing or installing its contents."""
import argparse
import hashlib
import json
import plistlib
import struct
from pathlib import Path, PurePosixPath
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('--ipa', required=True, type=Path)
parser.add_argument('--expected-source', required=True)
parser.add_argument('--source-receipt', required=True, type=Path)
parser.add_argument('--report', required=True, type=Path)
args = parser.parse_args()
assert args.source_receipt.read_text(encoding='utf8').strip() == args.expected_source, 'Build source receipt differs'
digest = hashlib.sha256(args.ipa.read_bytes()).hexdigest()
checksums = args.ipa.with_name('SHA256SUMS.txt').read_text(encoding='utf8').splitlines()
assert any(line.split()[0].lower() == digest for line in checksums if line.strip()), 'IPA checksum differs'
with ZipFile(args.ipa) as package:
    names = package.namelist()
    assert len(names) == len(set(names)), 'Duplicate archive entries'
    assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts and '\\' not in n for n in names), 'Unsafe archive path'
    assert package.testzip() is None, 'ZIP integrity check failed'
    app = 'Payload/Ayudix.app/'
    assert all(n.startswith(app) or n in ('Payload/', 'Payload/Ayudix.app/') for n in names), 'Unexpected IPA root'
    info = plistlib.loads(package.read(app + 'Info.plist'))
    assert info['CFBundleIdentifier'] == 'pe.ayudix.link.ios'
    assert info['CFBundleShortVersionString'] == '0.1.0'
    assert info['CFBundlePackageType'] == 'APPL'
    assert info['CFBundleSupportedPlatforms'] == ['iPhoneOS'], 'Simulator app cannot be installed on a phone'
    assert info['CADisableMinimumFrameDurationOnPhone'] is True
    assert info['NSCameraUsageDescription'] and info['NSLocalNetworkUsageDescription']
    assert info['NSAppTransportSecurity'] == {'NSAllowsLocalNetworking': True}
    assert app + 'PrivacyInfo.xcprivacy' in names and app + 'Assets.car' in names
    executable = info['CFBundleExecutable']
    assert PurePosixPath(executable).name == executable
    binary = package.read(app + executable)
    magic, cpu, subtype, kind, count, command_bytes, flags, reserved = struct.unpack_from('<8I', binary)
    assert magic == 0xFEEDFACF and cpu == 0x100000C and kind == 2, 'An arm64 executable is required'
    offset = 32
    platform = minimum = sdk = None
    for _ in range(count):
        command, length = struct.unpack_from('<2I', binary, offset)
        assert length >= 8 and offset + length <= 32 + command_bytes <= len(binary)
        if command == 0x32:
            platform, minimum, sdk = struct.unpack_from('<3I', binary, offset + 8)
        offset += length
    assert platform == 2, 'Mach-O must target iOS devices, not the simulator'
    assert app + 'embedded.mobileprovision' not in names, 'This checker expects the unsigned preparation package'
    resources_signed = any(n.startswith(app + '_CodeSignature/') for n in names)
    assert not resources_signed, 'Unexpected signed resources in an unsigned preparation'

def version(value):
    return f'{value >> 16}.{(value >> 8) & 255}.{value & 255}'

result = {'ok': True, 'kind': 'unsigned-device-ipa', 'file': str(args.ipa.resolve()),
          'sha256': digest, 'sourceCommit': args.expected_source, 'sourceReceiptMatches': True,
          'bundleId': info['CFBundleIdentifier'], 'version': info['CFBundleShortVersionString'],
          'build': info['CFBundleVersion'], 'architecture': 'arm64', 'platform': 'iPhoneOS',
          'minimumOs': version(minimum), 'sdk': version(sdk), 'zipIntegrity': True,
          'appleProvisioningIncluded': False, 'installableWithoutSigning': False, 'physicalIPhoneTest': False}
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text(json.dumps(result, indent=2), encoding='utf8')
print(json.dumps(result))

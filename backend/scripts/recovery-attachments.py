#!/usr/bin/env python3
"""Snapshot and restore a stopped ERP attachment volume, preserving its owner.

Only regular files and directories are accepted. The entire archive is verified
before the empty restore volume receives its first file. Use Python 3.11+.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import tarfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe_path(value):
    require(isinstance(value, str) and value not in ('', '.', '..') and '\\' not in value, 'Invalid attachment path')
    path = PurePosixPath(value)
    require(not path.is_absolute() and value == path.as_posix() and
            not any(part in ('.', '..') for part in path.parts) and
            not any(ord(char) < 32 for char in value), 'Unsafe attachment path')
    return path


def digest(file):
    with open(file, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def metadata(path):
    info = path.lstat()
    require(not stat.S_ISLNK(info.st_mode), 'Attachment symlinks are not supported')
    require(stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode), 'Unsupported attachment file type')
    return {'kind': 'directory' if stat.S_ISDIR(info.st_mode) else 'file',
            'mode': stat.S_IMODE(info.st_mode) & 0o777, 'uid': info.st_uid, 'gid': info.st_gid}


def snapshot(root):
    root = Path(root)
    root_info = metadata(root)
    require(root_info['kind'] == 'directory', 'Attachment root must be a real directory')
    entries = []
    for file in sorted(root.rglob('*')):
        relative = file.relative_to(root).as_posix()
        safe_path(relative)
        entry = {'path': relative, **metadata(file)}
        if entry['kind'] == 'file':
            entry.update(size=file.stat().st_size, sha256=digest(file))
        entries.append(entry)
    return {'schemaVersion': 1, 'kind': 'erp-attachment-snapshot', 'root': root_info, 'entries': entries}


def validate_snapshot(value):
    require(value.get('schemaVersion') == 1 and value.get('kind') == 'erp-attachment-snapshot', 'Unknown attachment snapshot')
    require(value['root']['kind'] == 'directory', 'Invalid attachment root metadata')
    require(isinstance(value['entries'], list), 'Invalid attachment inventory')
    names = set()
    entries = {}
    for entry in [value['root'], *value['entries']]:
        require(entry['kind'] in ('directory', 'file'), 'Invalid attachment kind')
        for key, maximum in [('mode', 0o777), ('uid', 2**31 - 1), ('gid', 2**31 - 1)]:
            require(type(entry[key]) is int and 0 <= entry[key] <= maximum, 'Invalid attachment ownership or mode')
        if entry is value['root']:
            continue
        safe_path(entry['path'])
        require(entry['path'] not in names, 'Duplicate attachment path')
        names.add(entry['path'])
        entries[entry['path']] = entry
        if entry['kind'] == 'file':
            require(type(entry['size']) is int and entry['size'] >= 0, 'Invalid attachment size')
            require(len(entry['sha256']) == 64 and all(c in '0123456789abcdef' for c in entry['sha256']), 'Invalid attachment digest')
    for name in names:
        for parent in PurePosixPath(name).parents:
            if parent.as_posix() != '.':
                require(entries.get(parent.as_posix(), {}).get('kind') == 'directory', 'Missing attachment parent directory')
    return entries


def verify(archive, manifest):
    value = json.loads(Path(manifest).read_text())
    expected = validate_snapshot(value)
    seen = set()
    require(Path(archive).is_file() and not Path(archive).is_symlink(), 'Archive must be a regular file')
    with tarfile.open(archive, 'r:gz') as source:
        for member in source:
            safe_path(member.name)
            require(member.name in expected and member.name not in seen, 'Unexpected or duplicate archive entry')
            entry = expected[member.name]
            require(member.isfile() if entry['kind'] == 'file' else member.isdir(), 'Archive links and special files are forbidden')
            require((member.mode & 0o7777) == entry['mode'] and member.uid == entry['uid'] and member.gid == entry['gid'], 'Archive metadata mismatch')
            if member.isfile():
                require(member.size == entry['size'], 'Attachment size mismatch')
                with source.extractfile(member) as stream:
                    require(hashlib.file_digest(stream, 'sha256').hexdigest() == entry['sha256'], 'Attachment checksum mismatch')
            seen.add(member.name)
    require(seen == set(expected), 'Missing attachment archive entries')
    return value


def backup(root, archive, manifest):
    root, archive, manifest = Path(root), Path(archive), Path(manifest)
    require(not archive.exists() and not manifest.exists(), 'Refusing to overwrite an attachment backup')
    value = snapshot(root)
    validate_snapshot(value)
    # Preserve numeric owners, while discarding any special permission bits.
    with tarfile.open(archive, 'x:gz', format=tarfile.PAX_FORMAT) as target:
        for entry in value['entries']:
            member = target.gettarinfo(str(root / entry['path']), arcname=entry['path'])
            member.mode = entry['mode']
            member.uname = member.gname = ''
            if member.isfile():
                with (root / entry['path']).open('rb') as stream:
                    target.addfile(member, stream)
            else:
                target.addfile(member)
    require(snapshot(root) == value, 'Attachment source changed while backing up; stop writers first')
    with manifest.open('x') as target:
        json.dump(value, target, ensure_ascii=False, indent=2)
        target.write('\n')
    for file in [archive, manifest]:
        file.chmod(0o600)
        # A root container writing to a bind mount returns files to its directory owner.
        if os.geteuid() == 0:
            owner = file.parent.stat()
            os.chown(file, owner.st_uid, owner.st_gid)
    verify(archive, manifest)
    return value


def set_metadata(path, entry):
    if os.geteuid() == 0:
        os.chown(path, entry['uid'], entry['gid'])
    else:
        info = path.stat()
        require((info.st_uid, info.st_gid) == (entry['uid'], entry['gid']), 'Restore requires the original owner or root')
    path.chmod(entry['mode'])


def restore(root, archive, manifest):
    root = Path(root)
    require(metadata(root)['kind'] == 'directory' and not any(root.iterdir()), 'Restore volume must be empty')
    value = verify(archive, manifest)  # No target writes before every entry is verified.
    expected = validate_snapshot(value)
    try:
        # Parent directories are created before children regardless of tar order.
        for entry in sorted(value['entries'], key=lambda row: (len(PurePosixPath(row['path']).parts), row['path'])):
            if entry['kind'] == 'directory':
                (root / entry['path']).mkdir(mode=0o700)
        with tarfile.open(archive, 'r:gz') as source:
            for member in source:
                if member.isfile():
                    destination = root / member.name
                    with source.extractfile(member) as stream, destination.open('xb') as target:
                        shutil.copyfileobj(stream, target, length=1024 * 1024)
                    set_metadata(destination, expected[member.name])
        for entry in sorted(value['entries'], key=lambda row: len(PurePosixPath(row['path']).parts), reverse=True):
            if entry['kind'] == 'directory':
                set_metadata(root / entry['path'], entry)
        set_metadata(root, value['root'])
        require(snapshot(root) == value, 'Restored attachment inventory differs from the checkpoint')
    except Exception:
        # The target was empty at entry; only this attempt's output can be removed.
        for child in root.iterdir():
            if child.is_dir():
                shutil.rmtree(child)
            else:
                child.unlink()
        raise
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['snapshot', 'backup', 'verify', 'restore'])
    parser.add_argument('--root')
    parser.add_argument('--archive')
    parser.add_argument('--manifest')
    args = parser.parse_args()
    if args.command == 'snapshot':
        result = snapshot(args.root)
    elif args.command == 'backup':
        result = backup(args.root, args.archive, args.manifest)
    elif args.command == 'restore':
        result = restore(args.root, args.archive, args.manifest)
    else:
        result = verify(args.archive, args.manifest)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, tarfile.TarError, KeyError, TypeError) as error:
        raise SystemExit(str(error))

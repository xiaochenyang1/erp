import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import sys

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location('attachments', Path(__file__).with_name('recovery-attachments.py'))
attachments = importlib.util.module_from_spec(spec)
spec.loader.exec_module(attachments)


class AttachmentRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.base = Path(self.directory.name)
        self.source = self.base / 'source'
        self.source.mkdir()
        (self.source / '账套/历史').mkdir(parents=True)
        (self.source / '账套/历史/凭证.txt').write_bytes('历史附件 🧪'.encode() * 200000)
        (self.source / 'empty').write_bytes(b'')
        self.archive = self.base / 'attachments.tar.gz'
        self.manifest = self.base / 'attachments.json'
        self.target = self.base / 'target'
        self.target.mkdir()
        self.expected = attachments.backup(self.source, self.archive, self.manifest)

    def test_restores_all_bytes_paths_permissions_and_owners(self):
        (self.source / '账套/历史/凭证.txt').unlink()
        (self.source / 'later').write_bytes(b'not in checkpoint')
        self.assertEqual(attachments.restore(self.target, self.archive, self.manifest), self.expected)
        self.assertEqual(attachments.snapshot(self.target), self.expected)
        self.assertFalse((self.target / 'later').exists())
        self.assertEqual(self.archive.stat().st_mode & 0o777, 0o600)

    def test_corrupt_archive_is_rejected_before_any_restore_write(self):
        self.archive.write_bytes(b'invalid gzip')
        with self.assertRaises((ValueError, tarfile.TarError)):
            attachments.restore(self.target, self.archive, self.manifest)
        self.assertEqual(list(self.target.iterdir()), [])

    def test_missing_entry_and_same_size_content_tampering_are_rejected(self):
        for replace in (False, True):
            with self.subTest(replace=replace):
                with tarfile.open(self.archive, 'w:gz') as target:
                    for entry in self.expected['entries']:
                        if entry['path'] == 'empty':
                            if not replace:
                                continue
                        member = tarfile.TarInfo(entry['path'])
                        member.mode, member.uid, member.gid = entry['mode'], entry['uid'], entry['gid']
                        if entry['kind'] == 'directory':
                            member.type = tarfile.DIRTYPE
                            target.addfile(member)
                        else:
                            member.size = entry['size']
                            content = b'z' * member.size if replace else (self.source / entry['path']).read_bytes()
                            target.addfile(member, io.BytesIO(content))
                with self.assertRaisesRegex(ValueError, 'checksum|Missing'):
                    attachments.restore(self.target, self.archive, self.manifest)
                self.assertEqual(list(self.target.iterdir()), [])

    def test_rejects_symlink_and_traversal_archive_members(self):
        for name, kind in [('../escape', tarfile.REGTYPE), ('/absolute', tarfile.REGTYPE),
                           ('empty', tarfile.SYMTYPE), ('empty', tarfile.LNKTYPE)]:
            with self.subTest(name=name, kind=kind):
                with tarfile.open(self.archive, 'w:gz') as target:
                    member = tarfile.TarInfo(name)
                    member.type = kind
                    member.linkname = '/outside'
                    target.addfile(member)
                with self.assertRaises(ValueError):
                    attachments.restore(self.target, self.archive, self.manifest)
                self.assertEqual(list(self.target.iterdir()), [])
                self.assertFalse((self.base / 'escape').exists())

    def test_nonempty_destination_is_preserved(self):
        sentinel = self.target / 'keep'
        sentinel.write_text('existing data')
        with self.assertRaisesRegex(ValueError, 'empty'):
            attachments.restore(self.target, self.archive, self.manifest)
        self.assertEqual(sentinel.read_text(), 'existing data')

    def test_source_and_target_symlinks_are_rejected(self):
        link = self.source / 'external'
        link.symlink_to(self.base / 'target', target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            attachments.snapshot(self.source)
        self.target.rmdir()
        self.target.symlink_to(self.source, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            attachments.restore(self.target, self.archive, self.manifest)

    def test_duplicate_and_unsafe_manifest_paths_are_rejected(self):
        for path in ['../escape', '/absolute', '.', 'a\\b', 'a/../b', 'a//b']:
            value = json.loads(self.manifest.read_text())
            value['entries'][0]['path'] = path
            with self.subTest(path=path), self.assertRaises(ValueError):
                attachments.validate_snapshot(value)
        value = json.loads(self.manifest.read_text())
        value['entries'].append(value['entries'][0])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            attachments.validate_snapshot(value)


if __name__ == '__main__':
    unittest.main()

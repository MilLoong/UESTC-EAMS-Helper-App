from pathlib import Path
import tempfile
import unittest

from release_notes import render


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def render(self, tag='v1.0.0', version='1.0.0', manual=''):
        return render(tag, version, '1', manual, self.directory)

    def test_publishes_written_description(self):
        (self.directory / 'v1.0.0.md').write_text('## 更新内容\n\n- 扫码页面改为竖屏', encoding='utf-8')
        notes = self.render()
        self.assertIn('## 更新内容\n\n- 扫码页面改为竖屏', notes)
        self.assertIn('versionCode 1', notes)
        self.assertIn('app-debug.apk', notes)

    def test_manual_description_overrides_file(self):
        (self.directory / 'v1.0.0.md').write_text('旧说明', encoding='utf-8')
        notes = self.render(manual='  ## 更新内容\n\n- 自定义说明  ')
        self.assertIn('- 自定义说明', notes)
        self.assertNotIn('旧说明', notes)

    def test_missing_description_fails(self):
        with self.assertRaisesRegex(ValueError, 'Missing release description'):
            self.render()

    def test_empty_description_fails(self):
        (self.directory / 'v1.0.0.md').write_text(' \n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Empty release description'):
            self.render()

    def test_invalid_tag_is_rejected(self):
        with self.assertRaises(ValueError):
            self.render(tag="v1.0'; echo invalid")

    def test_version_mismatch_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'does not match'):
            self.render(version='2.0.0')


if __name__ == '__main__':
    unittest.main()

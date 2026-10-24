import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from release_notes import render


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.original = Path.cwd()
        os.chdir(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'test@example.com')

    def tearDown(self):
        os.chdir(self.original)
        self.temp.cleanup()

    def git(self, *args):
        subprocess.run(['git', *args], check=True, capture_output=True)

    def commit(self, message):
        self.git('-c', 'commit.gpgsign=false', 'commit', '--allow-empty', '-qm', message)

    def test_first_release_contains_initial_history(self):
        self.commit('Add course reminders')
        self.git('tag', 'v1.0.0')
        notes = render('v1.0.0', '1.0.0', '1')
        self.assertIn('Add course reminders', notes)
        self.assertIn('versionCode 1', notes)
        self.assertIn('app-debug.apk', notes)

    def test_only_changes_since_previous_tag(self):
        self.commit('Old feature')
        self.git('tag', 'v1.0.0')
        self.commit('Fix portrait scanning')
        self.git('tag', 'v1.1.0')
        self.commit('Unreleased work')
        notes = render('v1.1.0', '1.1.0', '2', repository='owner/app')
        self.assertIn('Fix portrait scanning', notes)
        self.assertNotIn('Old feature', notes)
        self.assertNotIn('Unreleased work', notes)
        self.assertIn('/compare/v1.0.0...v1.1.0', notes)

    def test_manual_notes_override_commit_list(self):
        self.commit('Implementation detail')
        self.git('tag', '-a', 'v1.0.0', '-m', 'Annotated version')
        notes = render('v1.0.0', '1.0.0', '1', '  - 自定义说明\n- 第二项  ')
        self.assertIn('- 自定义说明\n- 第二项', notes)
        self.assertNotIn('Implementation detail', notes)

    def test_invalid_tag_is_rejected(self):
        with self.assertRaises(ValueError):
            render("v1.0'; echo invalid", '1.0', '1')

    def test_missing_tag_fails_instead_of_using_head(self):
        with self.assertRaises(subprocess.CalledProcessError):
            render('v9.9.9', '9.9.9', '99')


if __name__ == '__main__':
    unittest.main()

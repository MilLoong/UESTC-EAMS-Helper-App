"""Publish the release description written for the selected version."""
import argparse
import os
from pathlib import Path
import re


def render(tag, version, version_code, manual='', notes_directory='release-notes'):
    if not re.fullmatch(r'v[0-9][A-Za-z0-9._+-]*', tag):
        raise ValueError('Invalid release tag')
    if tag != f'v{version}':
        raise ValueError('Release tag does not match the version')
    changes = manual.strip()
    if not changes:
        path = Path(notes_directory) / f'{tag}.md'
        if not path.is_file():
            raise ValueError(f'Missing release description: {path}. Write it before publishing.')
        changes = path.read_text(encoding='utf-8-sig').strip()
        if not changes:
            raise ValueError(f'Empty release description: {path}')
    lines = [changes, '',
             f'versionName {version}', f'versionCode {version_code}', '',
             '下载 app-debug.apk 覆盖安装，无需卸载旧版。']
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--version-code', required=True)
    parser.add_argument('--notes-directory', default='release-notes')
    parser.add_argument('--output', default='release-notes.md')
    args = parser.parse_args()
    Path(args.output).write_text(render(args.tag, args.version, args.version_code,
                                       os.environ.get('INPUT_NOTES', ''), args.notes_directory), encoding='utf-8')


if __name__ == '__main__':
    main()

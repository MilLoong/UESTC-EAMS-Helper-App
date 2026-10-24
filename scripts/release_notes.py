"""Generate release notes from commits since the previous reachable version tag."""
import argparse
import os
from pathlib import Path
import re
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], encoding='utf-8', stderr=subprocess.PIPE).strip()


def render(tag, version, version_code, manual='', repository=''):
    if not re.fullmatch(r'v[0-9][A-Za-z0-9._+-]*', tag):
        raise ValueError('Invalid release tag')
    commit = git('rev-parse', '--verify', f'refs/tags/{tag}^{{commit}}')
    try:
        previous = git('describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*', f'{commit}^')
    except subprocess.CalledProcessError:
        previous = None
    history = f'{previous}..{commit}' if previous else commit
    changes = manual.strip() or git('log', '--no-merges', '--format=- %s (%h)', history)
    lines = ['## 更新内容', '', changes or '- 常规维护更新', '',
             f'versionName {version}', f'versionCode {version_code}', '',
             '下载 app-debug.apk 覆盖安装，无需卸载旧版。']
    if repository and previous:
        lines.extend(['', f'**完整变更**：https://github.com/{repository}/compare/{previous}...{tag}'])
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--version-code', required=True)
    parser.add_argument('--repository', default='')
    parser.add_argument('--output', default='release-notes.md')
    args = parser.parse_args()
    Path(args.output).write_text(render(args.tag, args.version, args.version_code,
                                       os.environ.get('INPUT_NOTES', ''), args.repository), encoding='utf-8')


if __name__ == '__main__':
    main()

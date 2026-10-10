#!/usr/bin/env python3
"""
FurFlix 静态自检 —— 零依赖基线扫描(阶段 3)。

用途:
  在确认 detekt / ktlint 的 Gradle 插件版本矩阵(AGP 9 内置 Kotlin + KGP 2.4.20)
  之前,用纯 Python 完成等价的存量盘点,输出可归档的报告。

不阻断构建:退出码恒为 0(除非 --strict)。
结果用于填充 ISSUES.md 的存量条目与阶段 4 的清理任务。

用法:
  python scripts/lint-baseline.py                 # 全量扫描,打印摘要
  python scripts/lint-baseline.py --format json   # 机器可读
  python scripts/lint-baseline.py --strict        # 有超阈项则退出码 1
  python scripts/lint-baseline.py --root app/src/main

检查项(对应 ISSUES.md):
  S-001  调试日志进入发布构建      Log.d / Log.v / println
  S-002  日志含敏感上下文          URL / HTML 片段落日志
  S-003  空 catch 吞异常           catch (...) {} 空体
  S-004  强制解包                  !!
  A-001  超大源文件                > 800 行
  I-001  疑似中文硬编码            Kotlin 字符串字面量含中文
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from typing import Dict, List

# ---------------------------------------------------------------- 规则定义

# S-001:调试日志。Log.w / Log.e 是告警/错误级,发布期保留属可接受。
RE_DEBUG_LOG = re.compile(r'\b(Log\.[dv]|println)\s*\(')

# S-002:敏感上下文。URL 或响应片段落日志。
RE_SENSITIVE_LOG = re.compile(
    r'Log\.[dviwe]\s*\([^)]*?(?:https?://|\$\{?html|html\.take|\burl\b)',
    re.IGNORECASE | re.DOTALL,
)

# S-003:空 catch 体(允许仅含注释)。
RE_EMPTY_CATCH = re.compile(
    r'catch\s*\([^)]*\)\s*\{\s*(?://[^\n]*\n\s*|/\*.*?\*/\s*)*\}',
    re.DOTALL,
)

# S-004:强制解包。排除注释行与字符串内的感叹号。
RE_FORCE_UNWRAP = re.compile(r'!!')

# I-001:Kotlin 字符串字面量中的中文。
RE_CJK_STRING = re.compile(r'"[^"\n]*[一-鿿][^"\n]*"')

# I-001 豁免:语言选择器里的语言名恒显母语名,不参与翻译。
# 依据 AGENTS.md「语言名是视觉锚点,恒显母语名,不参与翻译」。
I001_EXEMPT_FILES = (
    'TranslationModels.kt',   # 翻译目标语种名:zh-CN/简体中文、zh-TW/繁體中文、ja/日本語
    'SettingsScreen.kt',      # languageNameMap: en/ru/uk/zh 母语名
)

# A-001:超大源文件阈值。
LARGE_FILE_LINES = 800

# 豁免:数据表/常量文件天然很长,不计入 A-001。
LARGE_FILE_EXEMPT = (
    'FurSpeciesConstants.kt',
    'FurFilterConstants.kt',
)

# 目录级豁免:测试代码允许 println 与硬编码。
EXEMPT_DIRS = {'test', 'androidTest'}

RULES: Dict[str, str] = {
    'S-001': '调试日志进入发布构建',
    'S-002': '日志含敏感上下文(URL/HTML)',
    'S-003': '空 catch 吞异常',
    'S-004': '强制解包 !!',
    'A-001': '超大源文件(> %d 行)' % LARGE_FILE_LINES,
    'I-001': '疑似中文硬编码字符串',
}


# ---------------------------------------------------------------- 扫描


def iter_kotlin_files(root: str) -> List[str]:
    out: List[str] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in EXEMPT_DIRS]
        for f in filenames:
            if f.endswith('.kt'):
                out.append(os.path.join(dirpath, f))
    return sorted(out)


def strip_comments(line: str) -> str:
    """去掉整行注释,但保留字符串内的内容。"""
    idx = line.find('//')
    return line[:idx] if idx >= 0 else line


def scan(path: str) -> List[dict]:
    findings: List[dict] = []
    try:
        with open(path, encoding='utf-8', errors='ignore') as fh:
            lines = fh.readlines()
    except OSError as exc:  # pragma: no cover - 文件系统异常
        print(f'warning: cannot read {path}: {exc}', file=sys.stderr)
        return findings

    basename = os.path.basename(path)

    # A-001:文件行数
    if basename not in LARGE_FILE_EXEMPT and len(lines) > LARGE_FILE_LINES:
        findings.append({
            'rule': 'A-001',
            'file': path,
            'line': len(lines),
            'detail': f'{len(lines)} 行',
        })

    for lineno, raw in enumerate(lines, start=1):
        line = raw.rstrip('\n')
        code = strip_comments(line)
        if not code.strip():
            continue

        if RE_DEBUG_LOG.search(code):
            findings.append({
                'rule': 'S-001', 'file': path, 'line': lineno,
                'detail': code.strip()[:120],
            })
        if RE_SENSITIVE_LOG.search(code):
            findings.append({
                'rule': 'S-002', 'file': path, 'line': lineno,
                'detail': code.strip()[:120],
            })
        if RE_FORCE_UNWRAP.search(code) and '!' in code.replace('!!', 'X'):
            findings.append({
                'rule': 'S-004', 'file': path, 'line': lineno,
                'detail': code.strip()[:120],
            })
        if RE_CJK_STRING.search(code) and basename not in I001_EXEMPT_FILES:
            # 排除 KDoc:以 * 或 /** 开头的行多为注释,已由 strip_comments 处理 //;
            # 块注释内的中文不视为 UI 硬编码。
            stripped = code.strip()
            if not stripped.startswith('*') and not stripped.startswith('/*'):
                findings.append({
                    'rule': 'I-001', 'file': path, 'line': lineno,
                    'detail': code.strip()[:120],
                })

    # S-003 需跨行匹配,单独扫全文
    with open(path, encoding='utf-8', errors='ignore') as fh:
        text = fh.read()
    for m in RE_EMPTY_CATCH.finditer(text):
        lineno = text.count('\n', 0, m.start()) + 1
        findings.append({
            'rule': 'S-003', 'file': path, 'line': lineno,
            'detail': ' '.join(m.group(0).split())[:120],
        })

    return findings


# ---------------------------------------------------------------- 输出


def main() -> int:
    ap = argparse.ArgumentParser(description='FurFlix 静态自检(零依赖)')
    ap.add_argument('--root', default='app/src/main', help='扫描根目录')
    ap.add_argument('--format', choices=['text', 'json'], default='text')
    ap.add_argument('--strict', action='store_true',
                    help='存在超阈项时退出码 1(默认不阻断)')
    ap.add_argument('--rules', default='',
                    help='只跑指定规则,逗号分隔,如 S-001,S-003')
    args = ap.parse_args()

    wanted = {r.strip() for r in args.rules.split(',') if r.strip()}
    files = iter_kotlin_files(args.root)
    findings: List[dict] = []
    for f in files:
        findings.extend(scan(f))

    if wanted:
        findings = [x for x in findings if x['rule'] in wanted]

    if args.format == 'json':
        print(json.dumps({
            'root': args.root,
            'files_scanned': len(files),
            'findings': findings,
        }, ensure_ascii=False, indent=2))
    else:
        print(f'扫描根目录: {args.root}')
        print(f'Kotlin 文件: {len(files)}')
        print(f'命中项: {len(findings)}')
        print()
        by_rule: Dict[str, List[dict]] = {}
        for f in findings:
            by_rule.setdefault(f['rule'], []).append(f)
        for rule in sorted(by_rule):
            items = by_rule[rule]
            print(f'[{rule}] {RULES.get(rule, "")} — {len(items)} 处')
            for it in items[:10]:
                print(f'    {it["file"]}:{it["line"]}  {it["detail"]}')
            if len(items) > 10:
                print(f'    ... 另 {len(items) - 10} 处')
            print()

    if args.strict and findings:
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

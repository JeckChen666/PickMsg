#!/usr/bin/env python3
"""UI smoke checks on a dedicated pickmsg_ui_* emulator; never clear existing app data."""
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--server-port', default='5037')
parser.add_argument('--phase', choices=['all', 'details'], default='all')
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'Use a dedicated emulator, not a physical phone.'
adb = [args.adb, '-P', args.server_port, '-s', args.serial]
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
results = []

def run(*parts):
    for attempt in range(4):
        try:
            return subprocess.check_output(adb + list(parts), stderr=subprocess.PIPE, timeout=40).decode().strip()
        except subprocess.CalledProcessError:
            if attempt == 3:
                raise
            time.sleep(2)
            subprocess.run(adb + ['wait-for-device'], timeout=40, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def shell(*parts):
    return run('shell', shlex.join(parts))

def tree():
    shell('uiautomator', 'dump', '/sdcard/pickmsg-acceptance.xml')
    return ET.fromstring(shell('cat', '/sdcard/pickmsg-acceptance.xml'))

def matches(root, label):
    return [n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc'))]

def tap(label):
    nodes = matches(tree(), label)
    assert nodes, 'Missing control: ' + label
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[-1].get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.5)

def expect(label, present=True):
    assert bool(matches(tree(), label)) == present, f'{label}: expected present={present}'
    results.append(f'{label}: present={present}')
    print('PASS', results[-1], flush=True)

def shot(name):
    (out / (name + '.png')).write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p']))
    (out / (name + '.xml')).write_text(ET.tostring(tree(), encoding='unicode'))
    print('CAPTURE', name, flush=True)

def back():
    shell('input', 'keyevent', '4')
    time.sleep(.5)

def input_text(label, value):
    tap(label)
    shell('input', 'text', value)
    time.sleep(.5)

def find_scrolling(label, attempts=14):
    for _ in range(attempts):
        root = tree()
        if matches(root, label):
            return
        candidates = [list(map(int, re.findall(r'\d+', n.get('bounds'))))
                      for n in root.iter('node') if n.get('scrollable') == 'true']
        assert candidates, 'No scrollable region for ' + label
        x1, y1, x2, y2 = max(candidates, key=lambda bounds: bounds[3] - bounds[1])
        x = (x1+x2)//2
        shell('input', 'swipe', str(x), str(int(y1+(y2-y1)*.8)), str(x), str(int(y1+(y2-y1)*.2)), '250')
    raise AssertionError('Cannot reach ' + label)

run('wait-for-device')
time.sleep(2)
assert 'pickmsg_ui_' in run('emu', 'avd', 'name'), 'Refusing non-acceptance AVD.'
package = 'com.pickmsg.app'
original_font = shell('settings', 'get', 'system', 'font_scale')
try:
    shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('cmd', 'uimode', 'night', 'no')
    shell('am', 'force-stop', package)
    shell('am', 'start', '-n', package + '/.MainActivity')
    time.sleep(2)
    if args.phase == 'all':
        expect('需要通知授权')
        shot('01-unauthorized')
        for tab, empty, create in [('规则', '还没有规则\n点击“新建规则”，设置通知处理方式', '新建规则'),
                                   ('场景', '还没有场景\n按时间、网络等条件控制规则何时生效', '新建场景'),
                                   ('计划', '还没有计划任务\n在指定时间发一条本地通知，可用作定时提醒', '新建计划')]:
            tap(tab)
            expect(empty)
            shot('02-empty-' + tab)
            tap(create)
            tap('保存')
            expect('名称不能为空')
            back()
            expect(empty)
        tap('规则')
        tap('新建规则')
        input_text('规则名称', 'QA_A_long_notification_rule_name_for_layout_acceptance_20261029')
        expect('我的', present=False)
        shot('03-rule-keyboard')
        back()
        find_scrolling('添加格式化')
        tap('添加格式化')
        expect('添加格式化操作')
        shot('04-format-dialog')
        tap('取消')
        # This fixture has no actions, so it cannot send, remove or modify any notification.
        tap('保存')
        expect('QA_A_long_notification_rule_name_for_layout_acceptance_20261029')
        shot('05-long-rule')
        tap('启用QA_A_long_notification_rule_name_for_layout_acceptance_20261029')
        expect('编辑规则', present=False)
        tap('编辑QA_A_long_notification_rule_name_for_layout_acceptance_20261029')
        tap('删除规则')
        expect('删除规则？')
        tap('取消')
        expect('编辑规则')
        back()
        tap('场景')
        tap('新建场景')
        input_text('场景名称', 'QA_scene_layout')
        back()
        tap('添加条件')
        expect('添加条件')
        shot('06-condition-dialog')
        tap('取消')
        tap('保存')
        tap('编辑QA_scene_layout')
        tap('删除场景')
        expect('删除场景？')
        tap('删除')
        expect('还没有场景\n按时间、网络等条件控制规则何时生效')
        tap('计划'); tap('新建计划')
        input_text('计划名称', 'QA_plan_long_name_for_small_screen_acceptance')
        back(); find_scrolling('启用'); tap('启用'); tap('保存')
        tap('编辑09:00 · QA_plan_long_name_for_small_screen_acceptance')
        tap('删除计划'); expect('删除计划？'); tap('取消'); back()
        shot('20-plan-populated')
        tap('通知')
        shell('cmd', 'notification', 'allow_listener', package + '/.service.NotificationCollectorService')
        shell('pm', 'grant', package, 'android.permission.POST_NOTIFICATIONS')
        shell('am', 'force-stop', package)
        shell('am', 'start', '-n', package + '/.MainActivity')
        time.sleep(3)
        shell('cmd', 'notification', 'post', '-t', 'QA long notification title for text wrapping and detail display', 'qa-long',
              'Long notification body. ' * 18)
        time.sleep(1)
        tap('更多操作'); tap('发送测试通知')
        time.sleep(1)
        tap('按应用'); shot('07-app-groups')
        tap('按规则'); shot('08-rule-groups')
        tap('按时间')
        input_text('搜索通知', 'no_matching_fixture_789')
        back(); expect('没有匹配的记录'); shot('09-search-empty')
        tap('清除搜索')
    else:
        shell('cmd', 'notification', 'post', '-t', 'QA long notification title for text wrapping and detail display', 'qa-long',
              'Long notification body. ' * 18)
        time.sleep(1)
    input_text('搜索通知', 'QA long notification')
    back()
    tap('QA long notification title for text wrapping and detail display')
    expect('通知详情'); expect('通知 Key', present=False)
    tap('置顶'); expect('取消置顶'); tap('取消置顶'); expect('置顶')
    shot('10-detail')
    find_scrolling('查看诊断信息'); tap('查看诊断信息')
    find_scrolling('通知 Key'); expect('通知 Key'); shot('11-diagnostics')
    tap('删除通知'); expect('删除通知？'); tap('取消'); back()
    tap('清除搜索')
    tap('我的'); shot('12-settings')
    tap('展开备份与恢复'); find_scrolling('备份间隔（小时）'); shot('13-backup')
    # Tight portrait viewport and large text; no stored application data is changed.
    shell('wm', 'size', '945x1890')
    shell('settings', 'put', 'system', 'font_scale', '2.0')
    shell('am', 'force-stop', package); shell('am', 'start', '-n', package + '/.MainActivity'); time.sleep(2)
    shot('14-small-large-notifications')
    tap('场景'); tap('新建场景'); find_scrolling('添加条件'); tap('添加条件'); shot('15-small-large-dialog')
    tap('取消'); back()
    tap('我的'); shot('16-small-large-settings')
    shell('wm', 'size', '1920x1080')
    time.sleep(2)
    tap('场景'); tap('新建场景'); find_scrolling('添加条件'); tap('添加条件'); shot('17-landscape-dialog')
    tap('取消'); back()
    shell('wm', 'size', 'reset'); shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('cmd', 'uimode', 'night', 'yes');time.sleep(2)
    tap('规则');shot('18-dark-rules')
    tap('通知');shot('19-dark-notifications')
    results.append('All scripted assertions completed; screenshots require visual review.')
except Exception as exc:
    results.append('FAILED: ' + repr(exc))
    try: shot('failure')
    except Exception: pass
    raise
finally:
    (out / ('results-' + args.phase + '.json')).write_text(json.dumps(results, ensure_ascii=False, indent=2))
    shell('wm', 'size', 'reset')
    shell('settings', 'put', 'system', 'font_scale', original_font if original_font != 'null' else '1.0')
    shell('cmd', 'uimode', 'night', 'no')

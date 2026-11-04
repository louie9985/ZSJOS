# -*- coding: utf-8 -*-
"""Compile feature sources against a snapshot of an existing prepared local classpath."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--classpath-args', type=Path, required=True,
                    help='Existing javac args with a -cp entry; directories are copied, never mutated')
args = parser.parse_args()
lines = args.classpath_args.read_text(encoding='utf-8').splitlines()
original = lines[lines.index('"-cp"') + 1].strip('"')
out = ROOT / 'artifacts/exam-collection/backend'
out.mkdir(parents=True, exist_ok=True)
parts = []
for index, entry in enumerate(original.split(os.pathsep)):
    if not entry.strip():
        continue
    path = Path(entry)
    if path.is_dir():
        target = out / 'dependencies' / str(index)
        shutil.copytree(path, target, dirs_exist_ok=True)
        parts.append(str(target))
    elif path.is_file():
        parts.append(str(path))
    else:
        raise RuntimeError('Missing prepared dependency: ' + str(path))
launcher = Path.home() / '.m2/repository/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar'
cp = os.pathsep.join([str(out), *parts, str(launcher)])
base = ROOT / 'backend/yudao-module-zsjos/src'
package = 'java/cn/iocoder/yudao/module/zsjos'
main = base / 'main' / package
sources = []
for directory in ('service/examcollection', 'dal/dataobject/examcollection', 'dal/mysql/examcollection',
                  'dal/dataobject/studentinfo', 'dal/mysql/studentinfo', 'controller/admin/examcollection', 'controller/pub/examcollection'):
    sources.extend((main / directory).rglob('*.java'))
sources.extend([main / 'service/studentinfo/StudentInfoFields.java',
                main / 'service/task/BusinessTaskCancellationService.java',
                main / 'service/studentinfo/StudentSummaryService.java',
                main / 'service/studentinfo/StudentArchiveService.java',
                main / 'controller/admin/studentinfo/StudentArchiveController.java',
                main / 'controller/admin/studentinfo/vo/StudentArchiveVO.java',
                main / 'service/studentinfo/StudentInfoImageService.java',
                main / 'service/studentinfo/StudentInfoUploadRateLimit.java',
                main / 'controller/admin/studentinfo/vo/StudentInfoVO.java'])
tests = list((base / 'test' / package / 'service/examcollection').glob('*Test.java'))
tests.append(base / 'test' / package / 'service/studentinfo/StudentInfoImagesAndRulesTest.java')
tests.append(base / 'test' / package / 'service/studentinfo/StudentArchiveServiceTest.java')
tests.append(base / 'test' / package / 'service/studentinfo/StudentSummaryServiceTest.java')
sources.extend(tests)
runner = out / 'ExamBackendTests.java'
runner.write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class ExamBackendTests { public static void main(String[] args) {
  var request = LauncherDiscoveryRequestBuilder.request();
  for (var name : args) request.selectors(selectClass(name));
  var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener); launcher.execute(request.build());
  var writer = new java.io.PrintWriter(System.out);
  listener.getSummary().printTo(writer); listener.getSummary().printFailuresTo(writer);
  if (listener.getSummary().getTestsFoundCount() == 0 || listener.getSummary().getTestsFailedCount() > 0) System.exit(1);
}}''', encoding='utf-8')

def run(command, values):
    path = out / (command + '.args')
    path.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in values), encoding='utf-8')
    subprocess.run([command, '@' + str(path)], cwd=ROOT, check=True)

run('javac', ['-proc:full', '-parameters', '-encoding', 'UTF-8', '-cp', cp, '-d', out, *sources, runner])
run('java', ['-cp', cp, 'ExamBackendTests', *['cn.iocoder.yudao.module.zsjos.service.' + t.parent.name + '.' + t.stem for t in tests]])

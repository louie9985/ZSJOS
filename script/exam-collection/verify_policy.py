# -*- coding: utf-8 -*-
"""Compile and run pure exam-collection decisions with existing local JUnit artifacts."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts' / 'exam-collection' / 'policy'
OUT.mkdir(parents=True, exist_ok=True)
REPO = Path.home() / '.m2' / 'repository'
artifacts = [
    ('org/junit/jupiter', 'junit-jupiter-api', '6.0.3'),
    ('org/junit/jupiter', 'junit-jupiter-engine', '6.0.3'),
    ('org/junit/platform', 'junit-platform-commons', '6.0.3'),
    ('org/junit/platform', 'junit-platform-engine', '6.0.3'),
    ('org/junit/platform', 'junit-platform-launcher', '6.0.3'),
    ('org/opentest4j', 'opentest4j', '1.3.0'),
    ('org/apiguardian', 'apiguardian-api', '1.1.2'),
]
jars = [REPO / group / name / version / f'{name}-{version}.jar' for group, name, version in artifacts]
for jar in jars:
    if not jar.is_file():
        raise RuntimeError(f'Existing test dependency unavailable: {jar.name}')
cp = os.pathsep.join(map(str, [OUT, *jars]))
runner = OUT / 'ExamPolicyTests.java'
runner.write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class ExamPolicyTests {
  public static void main(String[] args) {
    var request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(
      "cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPolicyTest")).build();
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener); launcher.execute(request);
    var writer = new java.io.PrintWriter(System.out);
    listener.getSummary().printTo(writer); listener.getSummary().printFailuresTo(writer);
    if (listener.getSummary().getTestsFoundCount() == 0 || listener.getSummary().getTestsFailedCount() > 0)
      System.exit(1);
  }
}
''', encoding='utf-8')
module = ROOT / 'backend/yudao-module-zsjos/src'
package = 'java/cn/iocoder/yudao/module/zsjos/service/examcollection'
sources = [module / 'main' / package / 'ExamCollectionPolicy.java',
           module / 'test' / package / 'ExamCollectionPolicyTest.java', runner]
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(OUT), *map(str, sources)], check=True)
subprocess.run(['java', '-cp', cp, 'ExamPolicyTests'], check=True)

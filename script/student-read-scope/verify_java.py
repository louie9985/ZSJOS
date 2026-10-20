# -*- coding: utf-8 -*-
"""Compile current scope consumers/tests into isolated output using existing Maven dependencies."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
out = ROOT / 'frontend/workbench/tmp/student-read-scope/java'
out.mkdir(parents=True, exist_ok=True)
reports = list((ROOT / 'backend/yudao-module-zsjos/target/surefire-reports').glob('TEST-*.xml'))
if not reports:
    raise RuntimeError('Existing Maven test classpath required; prepare module dependencies first')
report = max(reports, key=lambda p: p.stat().st_mtime)
cp = next(p.attrib['value'] for p in ET.parse(report).findall('./properties/property')
          if p.attrib['name'] == 'java.class.path')
parts = []
for index, entry in enumerate(cp.split(';')):
    if not entry.strip():
        continue
    path = Path(entry)
    if path.is_dir():
        if path.name not in ('classes', 'test-classes') or ROOT / 'backend' not in path.parents:
            raise RuntimeError('Unexpected classpath directory: ' + str(path))
        target = out / 'dependencies' / str(index)
        if not target.exists():
            shutil.copytree(path, target)
        parts.append(str(target))
    else:
        parts.append(entry)
launcher = Path.home() / '.m2/repository/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar'
cp = str(out) + ';' + ';'.join(parts) + ';' + str(launcher)
main = ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos'
test = ROOT / 'backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos'
sources = [
    'service/deliveryclass/DeliveryClassScopeService.java',
    'service/deliveryclass/DeliveryClassObjectPermissionProvider.java',
    'service/deliveryclass/DeliveryClassServiceImpl.java',
    'service/deliveryclass/DeliveryClassService.java',
    'service/deliveryclass/DeliveryClassDates.java',
    'service/deliveryclass/DeliveryClassProjects.java',
    'service/registration/StudentServiceReadService.java',
    'service/registration/MyStudentServiceImpl.java',
    'service/registration/StudentObjectPermissionProvider.java',
    'service/registration/StudentServiceObjectPermissionProvider.java',
    'service/studentcontact/StudentContactServiceImpl.java',
    'dal/mysql/lead/PersonMapper.java',
    'dal/mysql/registration/ServiceRelationMapper.java',
    'dal/mysql/deliveryclass/DeliveryClassMapper.java',
    'dal/mysql/deliveryclass/DeliveryClassFilterSql.java',
]
tests = [
    'service/deliveryclass/DeliveryClassScopeServiceTest',
    'service/deliveryclass/DeliveryClassServiceImplTest',
    'service/deliveryclass/DeliveryClassFiltersTest',
    'service/deliveryclass/DeliveryClassFilterMysqlTest',
    'service/registration/StudentServiceReadServiceTest',
    'service/registration/MyStudentServiceImplTest',
    'service/registration/StudentObjectPermissionProviderTest',
    'service/registration/StudentServiceObjectPermissionProviderTest',
    'service/registration/StudentReadScopeRegressionTest',
    'service/studentcontact/StudentContactServiceImplTest',
    'dal/mysql/lead/PersonMapperStudentScopeSqlTest',
]
files = [main / p for p in sources]
files += list((main / 'controller/admin/deliveryclass/vo').glob('*.java'))
files += [test / (p + '.java') for p in tests]
runner = out / 'RunTests.java'
runner.write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class RunTests { public static void main(String[] args) {
var request=LauncherDiscoveryRequestBuilder.request();
for(String name:args) request.selectors(selectClass(name));
var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
launcher.registerTestExecutionListeners(listener);launcher.execute(request.build());
listener.getSummary().printTo(new java.io.PrintWriter(System.out));
listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out));
if(listener.getSummary().getTestsFoundCount()==0 || listener.getSummary().getTestsFailedCount()>0) System.exit(1);
}}''', encoding='utf-8')

def run(tool, args):
    argfile = out / (tool + '.args')
    argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    subprocess.run([tool, '@' + str(argfile)], check=True, cwd=ROOT)

run('javac', ['-proc:full', '-parameters', '-encoding', 'UTF-8', '-cp', cp, '-d', out, *files, runner])
run('java', ['-cp', cp, 'RunTests', *['cn.iocoder.yudao.module.zsjos.' + p.replace('/', '.') for p in tests]])

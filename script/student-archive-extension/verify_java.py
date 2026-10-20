# -*- coding: utf-8 -*-
"""Compile the current studentinfo sources into an isolated output; run focused JUnit tests."""
from pathlib import Path
import subprocess
import shutil
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
out = ROOT/'frontend/workbench/tmp/student-archive-extension/java'
out.mkdir(parents=True, exist_ok=True)
prior_path = ROOT/'frontend/workbench/tmp/student-archive/java/compile.args'
if prior_path.exists():
    prior=prior_path.read_text(encoding='utf-8').splitlines()
    cp=prior[prior.index('"-cp"')+1].strip('"')
else:
    reports=list((ROOT/'backend/yudao-module-zsjos/target/surefire-reports').glob('TEST-*.xml'))
    if not reports: raise RuntimeError('Prepare module Maven test dependencies first; no local classpath is available')
    report=max(reports,key=lambda p:p.stat().st_mtime)
    cp=next(p.attrib['value'] for p in ET.parse(report).findall('./properties/property') if p.attrib['name']=='java.class.path')
launcher = ROOT.home()/'.m2/repository/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar'
# Snapshot directory dependencies so other local builds cannot clean verification inputs.
parts=[]
for index, entry in enumerate(cp.split(';')):
    if not entry.strip(): continue
    path=Path(entry)
    if path.is_dir():
        if not (path.name in ('classes','test-classes','java') and (ROOT/'backend' in path.parents or ROOT/'frontend/workbench/tmp' in path.parents)):
            raise RuntimeError('Unexpected classpath directory: '+str(path))
        destination=out/'dependencies'/str(index)
        shutil.copytree(path,destination,dirs_exist_ok=True)
        parts.append(str(destination))
    else: parts.append(entry)
cp = str(out)+';'+';'.join(parts)+';'+str(launcher)
main=ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos'
test=ROOT/'backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/studentinfo'
files=[]
for directory in ['service/studentinfo','controller/admin/studentinfo','dal/mysql/studentinfo','dal/dataobject/studentinfo']:
    files.extend((main/directory).rglob('*.java'))
tests=list(test.glob('*Test.java'));files+=tests
runner=out/'RunTests.java'
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
}}''',encoding='utf-8')
def run(executable,args):
    argfile=out/(executable+'.args')
    argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
    subprocess.run([executable,'@'+str(argfile)],check=True,cwd=ROOT)
run('javac',['-proc:full','-parameters','-encoding','UTF-8','-cp',cp,'-d',out,*files,runner])
run('java',['-cp',cp,'RunTests',*['cn.iocoder.yudao.module.zsjos.service.studentinfo.'+p.stem for p in tests]])

# -*- coding: utf-8 -*-
"""Focused current-source compile/tests using existing Maven dependencies; no shared output writes."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
out=ROOT/'frontend/workbench/tmp/student-service-annotation/java'
out.mkdir(parents=True,exist_ok=True)
reports=list((ROOT/'backend/yudao-module-zsjos/target/surefire-reports').glob('TEST-*.xml'))
if reports:
    report=max(reports,key=lambda p:p.stat().st_mtime)
    cp=next(p.attrib['value'] for p in ET.parse(report).findall('./properties/property') if p.attrib['name']=='java.class.path')
else:
    args=(ROOT/'frontend/workbench/tmp/student-read-scope/java/javac.args').read_text(encoding='utf-8').splitlines()
    saved=args[args.index('"-cp"')+1].strip('"')
    jars=[s for s in saved.split(';') if s.endswith('.jar')]
    classes=[str(p) for p in (ROOT/'backend').glob('**/target/classes')]
    cp=';'.join(classes+jars)
launcher=Path.home()/'.m2/repository/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar'
cp=str(out)+';'+cp+';'+str(launcher)
main=ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos'
test=ROOT/'backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos'
files=list(main.rglob('StudentServiceAnnotation*.java'))+list(test.rglob('StudentServiceAnnotation*Test.java'))
files += [main/p for p in ['service/registration/StudentServiceObjectPermissionProvider.java','service/registration/StudentDeliveryParticipation.java','service/registration/TeachingServiceLifecycle.java','service/deliveryclass/DeliveryClassScopeService.java','dal/dataobject/registration/ServiceRelationDO.java']]
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
def run(tool,args):
    argfile=out/(tool+'.args')
    argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
    mockito=next(s for s in cp.split(';') if '/mockito-core/' in s.replace('\\','/'))
    prefix=['-J-Xmx192m','-J-XX:+UseSerialGC','-J-XX:ActiveProcessorCount=2'] if tool=='javac' else ['-Xmx192m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=2','-javaagent:'+mockito]
    subprocess.run([tool,*prefix,'@'+str(argfile)],check=True,cwd=ROOT)
run('javac',['-proc:full','-parameters','-encoding','UTF-8','-cp',cp,'-d',out,*files,runner])
run('java',['-cp',cp,'RunTests',*['cn.iocoder.yudao.module.zsjos.service.registration.'+p.stem for p in test.rglob('StudentServiceAnnotation*Test.java')]])

// UTF-8. Real registration/overview consumers; all identities, files and HTTP responses are synthetic.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { App, Button, Card, Space } from 'antd';
import ThemeProvider from '../src/components/Theme/ThemeProvider';
import RegistrationStudentInfo from '../src/components/RegistrationStudentInfo';
import StudentOverviewBackground from '../src/components/StudentOverviewBackground';
import { http, type RegistrationCase } from '../src/services/api';
import type { StudentInfoDetail, StudentInfoField } from '../src/services/studentInfo';
import '../src/styles/index.css';

const options = new URLSearchParams(location.search);
type RequestEvent = { caseId: number; kind: string; imageId?: number };
const requests: RequestEvent[] = [];
const blobs = { created: [] as string[], revoked: [] as string[], downloads: [] as { name: string; url: string }[] };
const sync = () => {
  document.documentElement.dataset.studentRequests = JSON.stringify(requests);
  document.documentElement.dataset.studentBlobs = JSON.stringify(blobs);
};
const createObjectURL = URL.createObjectURL.bind(URL);
const revokeObjectURL = URL.revokeObjectURL.bind(URL);
URL.createObjectURL = object => { const url = createObjectURL(object); blobs.created.push(url); sync(); return url; };
URL.revokeObjectURL = url => { blobs.revoked.push(url); sync(); revokeObjectURL(url); };
const click = HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click = function () {
  if (this.download) { blobs.downloads.push({ name: this.download, url: this.href }); sync(); }
  click.call(this);
};
new MutationObserver(() => {
  if ([...document.querySelectorAll('.student-info-panel')].some(panel => panel.textContent?.includes('服务端电话遮罩-'))) {
    document.documentElement.dataset.studentMaskWasRendered = 'true';
  }
}).observe(document.body, { childList: true, characterData: true, subtree: true });

const fields: StudentInfoField[] = [
  { key: 'name', label: '学员姓名', type: 'text', group: 'basic', sensitive: false },
  { key: 'phone', label: '联系电话', type: 'text', group: 'basic', sensitive: true, note: '本字段按服务端返回内容显示。' },
  { key: 'identity', label: '证件信息', type: 'text', group: 'basic', sensitive: true },
  { key: 'area', label: '所在地区', type: 'area', group: 'basic', sensitive: false },
  { key: 'education_level', label: '学历层次', type: 'dict', group: 'education', sensitive: false, dictType: 'fixture_education' },
  { key: 'school', label: '毕业院校', type: 'text', group: 'education', sensitive: false },
  { key: 'employer', label: '工作单位', type: 'text', group: 'career', sensitive: false },
  { key: 'job', label: '职业背景', type: 'dict', group: 'career', sensitive: false, dictType: 'fixture_career' },
  { key: 'study_purpose', label: '学习目标与补充说明', type: 'textarea', group: 'career', sensitive: false },
  { key: 'evidence', label: '报名材料', type: 'images', group: 'materials', sensitive: true },
].map((field, sort) => ({ ...field, type: field.type as StudentInfoField['type'], enabled: true, required: false, sort }));
const purpose = '结合当前工作实践完善专业知识，每周安排固定学习时间，按照阶段目标完成训练与复盘。'.repeat(9) + '\n末尾完整正文标记。';
const imageBytes = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII='), c => c.charCodeAt(0));
function makePdf(): Blob {
  // The source is ASCII only, so string lengths are the exact byte offsets in the PDF xref.
  const stream = 'BT /F1 20 Tf 30 160 Td (Student info PDF preview verified) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 240] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
  ];
  let source = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(source.length);
    source += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = source.length;
  source += 'xref\n0 6\n0000000000 65535 f \n';
  source += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  source += `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return new Blob([source], { type: 'application/pdf' });
}
const pdfFile = makePdf();
function detail(caseId: number, sensitive = false): StudentInfoDetail {
  return {
    id: 880001 + caseId, status: options.has('empty') ? 'NONE' : 'SUBMITTED',
    submittedAt: Date.parse('2026-10-22T10:30:00+08:00'), configVersion: 3, fields,
    canReadSensitive: !options.has('denied'), canExport: !options.has('no-export'),
    values: {
      name: `合成学员-${caseId}`,
      phone: sensitive ? `SYNTHETIC-FULL-PHONE-${caseId}` : `服务端电话遮罩-${caseId}-甲`,
      identity: sensitive ? `SYNTHETIC-FULL-ID-${caseId}` : `服务端证件遮罩-${caseId}-乙`,
      area: '合成省 / 示例市 / 测试区',
      education_level: '学历历史标签（完整保留选择时名称）-' + 'LongSavedEducationLabel'.repeat(5),
      school: '合成测试院校', employer: '合成测试单位', job: '历史职业类别（保存快照）', study_purpose: purpose,
    },
    images: [
      { id: 91, fieldKey: 'evidence', side: 'FRONT', contentType: 'image/png', size: 128, name: '合成报名材料正面.png' },
      { id: 92, fieldKey: 'evidence', side: 'BACK', contentType: 'image/png', size: 256, name: '合成报名材料背面.png' },
      { id: 93, fieldKey: 'evidence', side: 'OTHER', contentType: 'application/pdf', size: pdfFile.size, name: '合成补充证明材料.pdf' },
    ],
  };
}
let detailFailures = options.has('detail-error') ? 1 : 0;
let sensitiveFailures = options.has('sensitive-error') ? 1 : 0;
let imageFailures = options.has('image-error') ? 1 : 0;
let rejectNextDetail = false;
let rejectNextSensitive = false;
let delayNextKind: 'detail' | 'sensitive' | undefined;
http.defaults.adapter = async config => {
  const url = config.url || '';
  const caseId = Number(url.match(/registration\/(\d+)/)?.[1]);
  const imageId = Number(url.match(/images\/(\d+)/)?.[1]) || undefined;
  const kind = imageId ? 'image' : url.split('/').pop()!;
  requests.push({ caseId, kind, ...(imageId ? { imageId } : {}) }); sync();
  let data: unknown;
  if (kind === 'detail' || kind === 'sensitive') {
    const delayed = delayNextKind === kind || options.has(`slow-${kind}`);
    if (delayNextKind === kind) delayNextKind = undefined;
    await new Promise(resolve => setTimeout(resolve, delayed ? 1400 : 70));
    if (kind === 'detail' && rejectNextDetail) { rejectNextDetail = false; throw new Error('合成资料读取被拒绝'); }
    if (kind === 'sensitive' && rejectNextSensitive) { rejectNextSensitive = false; throw new Error('合成完整敏感字段读取被拒绝'); }
    if (kind === 'detail' && detailFailures-- > 0) throw new Error('合成资料读取失败，请重试');
    if (kind === 'sensitive' && sensitiveFailures-- > 0) throw new Error('合成完整敏感字段读取被拒绝');
    data = { code: 0, data: detail(caseId, kind === 'sensitive') };
  } else if (kind === 'image') {
    await new Promise(resolve => setTimeout(resolve, options.has('slow-image') ? 1400 : 70));
    if (imageFailures-- > 0) throw new Error('合成附件暂时不可读取');
    data = imageId === 93 ? pdfFile : new Blob([imageBytes], { type: 'image/png' });
  } else if (kind === 'export') data = new Blob(['synthetic workbook'], { type: 'application/octet-stream' });
  else throw new Error(`Unexpected isolated student info request: ${url}`);
  return { config, status: 200, statusText: 'OK', headers: {}, data };
};

function Fixture() {
  const [caseId, setCaseId] = useState(7001);
  const [visible, setVisible] = useState(true);
  const [allowed, setAllowed] = useState(!options.has('overview-denied'));
  const item = { id: caseId, version: 1, status: 'processing', informationSubmissionStatus: 'SUBMITTED' } as RegistrationCase;
  return <main style={{ padding: 16 }}>
    <Space wrap style={{ marginBottom: 16 }}>
      <Button onClick={() => setCaseId(7002)}>切换报名</Button>
      <Button onClick={() => setVisible(value => !value)}>切换入口挂载</Button>
      <Button onClick={() => { rejectNextDetail = true; }}>下一次资料读取失败</Button>
      <Button onClick={() => { rejectNextSensitive = true; }}>下一次完整资料读取失败</Button>
      <Button onClick={() => { delayNextKind = 'detail'; }}>下一次资料读取延迟</Button>
      <Button onClick={() => { delayNextKind = 'sensitive'; }}>下一次完整资料读取延迟</Button>
      <Button onClick={() => setAllowed(false)}>撤销资料读取权限</Button>
    </Space>
    <Card title="合成学员资料验证" style={{ maxWidth: 1100 }}>
      {visible && (options.has('overview')
        ? <StudentOverviewBackground caseId={caseId} allowed={allowed} />
        : <RegistrationStudentInfo item={item} permissions={allowed ? ['zsjos:student-info-form:read'] : []} disabled={false} onChanged={() => {}} />)}
    </Card>
  </main>;
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>);

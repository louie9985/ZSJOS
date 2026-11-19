import { useState } from 'react';
import { Button, Modal, Radio } from 'antd';
import { sanitizeRichText } from '../SafeRichText';
import { examCollectionBodyHtml } from '../../../../shared/examCollectionBody';
import bodyCss from '../../../../shared/exam-collection-body.css?raw';
import formCss from '../../../../shared/exam-public-form.css?raw';
import imagesCss from '../../../../shared/exam-collection-images.css?raw';
import baseCss from '../../../../h5/src/styles/base.css?raw';
import themeCss from '../../../../h5/src/styles/themes/coral.css?raw';
import vantCss from '../../../../h5/node_modules/vant/lib/index.css?raw';
import vantOverrides from '../../../../h5/src/styles/vant-overrides.css?raw';
import type { StudentInfoField } from '../../services/studentInfo';
import { EXAM_ABSENCE_REASON_KEY, EXAM_ATTENDANCE_KEY, examFieldGroups } from './examFieldLayout';

function escapeText(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function fieldHtml(field: Pick<StudentInfoField, 'key' | 'label' | 'type' | 'note' | 'allowedExtensions' | 'maxImages' | 'maxSizeMb' | 'options' | 'required'>) {
  const label = escapeText(field.label);
  let control: string;
  if (field.type === 'images') {
    const slots = field.key === 'identity_images'
      ? [{ label: '身份证正面（人像面）', max: 1 }, { label: '身份证反面（国徽面）', max: 1 }]
      : [{ label: '添加附件', max: field.maxImages || 9 }];
    control = slots.map(slot => `<div class="collection-images__slot norem"><span class="collection-images__caption norem">${slot.label}（0/${slot.max}）</span><button class="collection-images__add norem" type="button" disabled aria-label="${slot.label}"><i class="van-icon van-icon-plus" style="font-size:24px" aria-hidden="true"></i><span>0 / ${slot.max}</span></button></div>`).join('')
      + `<p class="exam-public__hint norem">支持 ${escapeText((field.allowedExtensions || ['jpg', 'jpeg', 'png']).join('/').toUpperCase())}，单个不超过 ${field.maxSizeMb || 100}MB</p>`;
  } else if (field.type === 'radio' || field.type === 'checkbox') {
    control = `<div class="exam-public__select norem"><button id="field-${escapeText(field.key)}" type="button" class="exam-public__select-trigger norem"><span class="is-placeholder">请选择</span></button></div>`;
  } else {
    control = field.type === 'textarea'
      ? `<textarea id="field-${escapeText(field.key)}" rows="3" readonly></textarea>`
      : `<input id="field-${escapeText(field.key)}" type="${field.key === 'mobile' ? 'tel' : 'text'}" readonly>`;
  }
  if (field.key === EXAM_ATTENDANCE_KEY) control += '<p class="exam-public__hint norem">预览按选择「是」展示后续内容；选择「否」时仅填写不参加原因。</p>';
  return `<div class="exam-public__field norem ${field.type === 'text' ? '' : 'is-wide'}"><label for="field-${escapeText(field.key)}">${field.required !== false ? '<span class="exam-public__required norem">*</span>' : ''}${label}${field.type === 'images' ? `（0/${field.key === 'identity_images' ? 2 : field.maxImages || 9}）` : ''}</label>${control}${field.note ? `<p class="exam-public__hint norem">${escapeText(field.note)}</p>` : ''}</div>`;
}

/** fields arrive in collection-form order, including the fixed identity fields. */
export default function ExamCollectionBodyPreview({ title, description, content, fields, onClose }: {
  title: string; description: string; content: string; fields: StudentInfoField[]; onClose: () => void;
}) {
  const [mobile, setMobile] = useState(false);
  const body = examCollectionBodyHtml(sanitizeRichText(content));
  const { basic, materials } = examFieldGroups(fields.filter(field => field.key !== EXAM_ABSENCE_REASON_KEY));
  const source = `<!doctype html>
<html data-theme="coral"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
${vantCss}
${baseCss}
${themeCss}
${vantOverrides}
${formCss}
${imagesCss}
${bodyCss}
</style></head><body><main class="exam-public norem">
<header class="exam-public__header norem"><p class="exam-public__hint norem">中世健 · 考试资料收集</p>
<h1>${escapeText(title || '考试资料收集')}</h1>
${description ? `<p class="exam-public__description norem">${escapeText(description)}</p>` : ''}</header>
${body ? `<div class="exam-collection-body norem">${body}</div>` : ''}
<p class="exam-public__hint norem">请填写本人资料。标有 * 的项目为必填项，未提交内容不会自动保存。</p>
<section class="exam-public__section norem"><h2>基本信息</h2><div class="exam-public__grid norem">${basic.map(fieldHtml).join('')}</div></section>
${materials.length ? `<section class="exam-public__section norem"><h2>本次收集材料</h2><div class="exam-public__grid norem">${materials.map(fieldHtml).join('')}</div></section>` : ''}
<div class="exam-public__submit norem"><button class="van-button van-button--primary van-button--normal van-button--block" type="button" disabled><span class="van-button__text">提交资料</span></button></div>
</main></body></html>`;
  return <Modal open title="收集表预览" width="min(1400px, calc(100vw - 32px))"
    onCancel={onClose} footer={<Button onClick={onClose}>继续编辑</Button>}>
    <div style={{display:'flex',alignItems:'center',gap:16,flexWrap:'wrap',marginBottom:16}}><Radio.Group value={mobile} onChange={event => setMobile(event.target.value)}
      options={[{ label: '电脑', value: false }, { label: '手机', value: true }]}/><span>预览仅展示填写效果</span></div>
    <div style={{ overflowX: 'auto' }}>
      <iframe title="学员收集表预览" sandbox="" srcDoc={source}
        style={{display:'block',margin:'0 auto',width:mobile?375:'100%',maxWidth:'100%',height:'min(680px, 70dvh)',border:0}}/>
    </div>
  </Modal>;
}

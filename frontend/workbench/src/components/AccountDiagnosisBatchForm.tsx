import { Alert, App, Form } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, type DictData } from '../services/api';
import { createIdempotencyKey } from '../services/idempotency';
import { diagnosisBatchApi, weeklyDiagnosisApi, type AccountProfile, type DiagnosisBatchCandidate,
  type DiagnosisBatchItem, type DiagnosisBatchSubmit, type DiagnosisRequest, type WeeklyDiagnosisPreview } from '../services/mediaAccountProfile';
import AccountDiagnosisForm from './AccountDiagnosisForm';
import AccountDiagnosisAccounts, { type DiagnosisAccountRow } from './AccountDiagnosisAccounts';

type AccountValues = {currentStage?: string; accountStatus?: string; highestLikes?: number};
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试';

export default function AccountDiagnosisBatchForm({profile, type, seed, preview, dicts, disabled, onSavingChange,
  onReadyChange, onCountChange, onDirtyChange, onSuccess}: {
  profile: AccountProfile; type: DiagnosisRequest['templateType']; seed: Record<string, unknown>;
  preview?: WeeklyDiagnosisPreview; dicts: Record<string, DictData[]>; disabled: boolean;
  onSavingChange: (saving: boolean) => void; onReadyChange: (ready: boolean) => void;
  onCountChange: (count: number) => void; onDirtyChange: () => void; onSuccess: () => Promise<void>;
}) {
  const {message} = App.useApp();
  const [form] = Form.useForm();
  const initial = type === 'diagnosis_initial';
  const mode = initial ? 'diagnosis_initial' : 'weekly';
  const accountId = profile.account.id;
  const anchor: DiagnosisBatchCandidate = {accountId, accountNo: profile.account.accountNo, accountName: profile.account.nickname,
    platformLabel: profile.account.platformLabelSnapshot, version: profile.account.version, configVersionId: profile.config.id,
    currentStage: profile.account.sStage, accountStatus: profile.account.currentStatusValue, nodeKey: preview?.node.key, eligible: true};
  const [rows, setRows] = useState<DiagnosisAccountRow[]>([{candidate: anchor, preview}]);
  const rowsRef = useRef(rows); rowsRef.current = rows;
  const cache = useRef(new Map<number, DiagnosisAccountRow>());
  const sequences = useRef(new Map<number, number>());
  const lifetime = useRef(0);
  const candidatesSequence = useRef(0);
  const saving = useRef(false);
  const request = useRef<{fingerprint: string; key: string} | undefined>(undefined);
  const [candidates, setCandidates] = useState<DiagnosisBatchCandidate[]>([]);
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const [candidatesError, setCandidatesError] = useState('');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => () => {lifetime.current++; candidatesSequence.current++; sequences.current.clear();}, []);
  const update = (next: DiagnosisAccountRow[]) => {rowsRef.current = next; setRows(next);};
  useEffect(() => {
    onCountChange(rows.length);
    onReadyChange(rows.length > 0 && !rows.some(row => row.loading || row.error));
  }, [rows, onCountChange, onReadyChange]);

  const loadCandidates = useCallback(async () => {
    const sequence = ++candidatesSequence.current;
    const generation = lifetime.current;
    setCandidatesLoading(true); setCandidatesError('');
    try {
      const loaded = await diagnosisBatchApi.candidates(accountId, mode, preview?.node.key);
      if (sequence === candidatesSequence.current && generation === lifetime.current) setCandidates(loaded);
      return loaded;
    } catch (cause) {
      if (sequence === candidatesSequence.current && generation === lifetime.current) setCandidatesError(errorText(cause));
      return undefined;
    } finally {
      if (sequence === candidatesSequence.current && generation === lifetime.current) setCandidatesLoading(false);
    }
  }, [accountId, mode, preview?.node.key]);

  const refresh = async (id: number, highestLikes?: number) => {
    const row = rowsRef.current.find(row => row.candidate.accountId === id);
    if (!row || initial || !row.candidate.nodeKey || saving.current) return;
    const generation = lifetime.current;
    const sequence = (sequences.current.get(id) || 0) + 1; sequences.current.set(id, sequence);
    update(rowsRef.current.map(item => item.candidate.accountId === id ? {...item, loading: true, error: undefined} : item));
    try {
      const value = await weeklyDiagnosisApi.preview(id, row.candidate.nodeKey, highestLikes ?? form.getFieldValue(['accounts', String(id), 'highestLikes']));
      if (generation !== lifetime.current || sequences.current.get(id) !== sequence) return;
      update(rowsRef.current.map(item => item.candidate.accountId === id ? {...item, preview: value, loading: false, error: undefined} : item));
    } catch (cause) {
      if (generation !== lifetime.current || sequences.current.get(id) !== sequence) return;
      update(rowsRef.current.map(item => item.candidate.accountId === id ? {...item, loading: false, error: errorText(cause)} : item));
    }
  };
  const add = (candidate: DiagnosisBatchCandidate) => {
    if (saving.current || !candidate.eligible || rowsRef.current.some(row => row.candidate.accountId === candidate.accountId)) return;
    const old = cache.current.get(candidate.accountId);
    const reusable = old?.candidate.nodeKey === candidate.nodeKey;
    const row: DiagnosisAccountRow = {...(reusable ? old : {}), candidate};
    update([...rowsRef.current, row]); setPickerOpen(false); setError(''); setNotice(''); onDirtyChange();
    if (!initial) void refresh(candidate.accountId);
  };
  const remove = (id: number) => {
    if (saving.current) return;
    const row = rowsRef.current.find(row => row.candidate.accountId === id);
    if (row) cache.current.set(id, row);
    sequences.current.set(id, (sequences.current.get(id) || 0) + 1);
    update(rowsRef.current.filter(row => row.candidate.accountId !== id));
    setError(''); setNotice(''); onDirtyChange();
  };
  const submit = async (values: Record<string, unknown>) => {
    if (saving.current || !rowsRef.current.length || rowsRef.current.some(row => row.loading || row.error)) return;
    const generation = lifetime.current;
    const selected = [...rowsRef.current];
    const perAccount = values.accounts as Record<string, AccountValues>;
    const {accounts: _accounts, currentStage: _stage, accountStatus: _status, highestLikes: _likes, ...shared} = values;
    const items: DiagnosisBatchItem[] = selected.map(row => {
      const individual = perAccount[String(row.candidate.accountId)];
      const common = {...shared, currentStage: individual.currentStage, accountStatus: individual.accountStatus,
        version: row.candidate.version, configVersionId: row.candidate.configVersionId, idempotencyKey: 'batch',
        templateType: initial ? 'diagnosis_initial' : row.preview!.node.parts[0].templateType,
        cycle: initial ? 0 : row.preview!.node.parts[0].cycle} as DiagnosisRequest;
      return {accountId: row.candidate.accountId, ...(initial ? {initial: common} : {weekly: {
        nodeKey: row.preview!.node.key, common, highestLikes: individual.highestLikes,
        changeDirection: String(values.changeDirection), trafficNormal: String(values.trafficNormal),
        directionSummary: String(values.directionSummary), metricToken: row.preview!.metricToken,
      }})};
    });
    const payload = {accountId, templateType: mode, nodeKey: preview?.node.key, accounts: items};
    const fingerprint = JSON.stringify(payload);
    if (request.current?.fingerprint !== fingerprint) request.current = {fingerprint, key: createIdempotencyKey()};
    saving.current = true; onSavingChange(true); setError(''); setNotice('');
    try {
      await diagnosisBatchApi.submit({...payload, idempotencyKey: request.current.key} as DiagnosisBatchSubmit);
    } catch (cause) {
      if (generation !== lifetime.current) return;
      setError(errorText(cause));
      // A transport failure may have committed. Keep the exact request/key until the user changes the payload.
      if (cause instanceof ApiError && [1900011026, 1900011003].includes(cause.code)) {
        saving.current = false;
        const loaded = await loadCandidates();
        if (generation !== lifetime.current) return;
        if (loaded) update(rowsRef.current.map(row => {
          const candidate = loaded.find(item => item.accountId === row.candidate.accountId);
          return candidate?.eligible ? {...row, candidate} : {...row, error: candidate?.reason || '账号已不可填写，请移除后重试'};
        }));
        await Promise.all(rowsRef.current.filter(row => !row.error).map(row => refresh(row.candidate.accountId)));
        if (generation === lifetime.current) setNotice('已刷新账号与指标，填写内容已保留，请核对后再次提交。');
      }
      return;
    } finally {
      saving.current = false;
      if (generation === lifetime.current) onSavingChange(false);
    }
    if (generation !== lifetime.current) return;
    message.success(`已提交 ${items.length} 个账号的诊断`);
    await onSuccess();
  };
  return <>
    {error && <Alert type="error" showIcon title={error} />}
    {notice && <Alert type="info" showIcon title={notice} />}
    <AccountDiagnosisForm form={form} type={type} seed={seed} revising={false} disabled={disabled} dicts={dicts}
      weekly={preview?.node} onFinish={values => {void submit(values);}} onValuesChange={onDirtyChange}
      accountSection={<AccountDiagnosisAccounts rows={rows} candidates={candidates} candidatesLoading={candidatesLoading}
        candidatesError={candidatesError} pickerOpen={pickerOpen} onPickerOpen={open => {
          if (saving.current) return;
          setPickerOpen(open); if (open) void loadCandidates();
        }} onReloadCandidates={() => {void loadCandidates();}} onAdd={add} onRemove={remove}
        onRetry={id => {void refresh(id);}} onLikesChange={(id, value) => {void refresh(id, value);}}
        initial={initial} dicts={dicts} disabled={disabled} />} />
  </>;
}

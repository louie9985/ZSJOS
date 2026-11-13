import { afterEach, expect, it, vi } from 'vitest';
import { examCollectionApi, type ExamExportRequest } from './examCollection';
import { http } from './api';

vi.mock('./api', () => ({ http: { post: vi.fn() }, unwrap: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.useRealTimers(); });

it('downloads the Chinese ZIP filename returned by the server', async () => {
  vi.useFakeTimers();
  const filename = '中国营养学会2026年12月公共营养师考试报名核实表(202611111342).zip';
  vi.mocked(http.post).mockResolvedValue({
    data: new Blob(['zip'], { type: 'application/zip' }),
    headers: { 'content-disposition': `attachment;filename*=UTF-8''${encodeURIComponent(filename)}` },
  });
  const anchor = { href: '', download: '', click: vi.fn() };
  vi.stubGlobal('document', { createElement: vi.fn(() => anchor) });
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
  const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  await examCollectionApi.exportMaterials(1, {} as ExamExportRequest);
  expect(anchor.download).toBe(filename);
  expect(anchor.click).toHaveBeenCalledOnce();
  vi.runAllTimers();
  expect(revoke).toHaveBeenCalledWith('blob:test');
});

it('queues the export without waiting for a ZIP response', async () => {
  const request = { fingerprint: 'preview-version' } as ExamExportRequest;
  vi.mocked(http.post).mockResolvedValue({ data: { code: 0, data: 12 } });
  await examCollectionApi.createMaterialExport(2, request);
  expect(http.post).toHaveBeenLastCalledWith('/zsjos/exam-collection/2/material-export/tasks', request);
});

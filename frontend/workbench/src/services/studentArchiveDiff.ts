import { archiveImageReference, type StudentArchive } from './studentArchive';
import type { StudentInfoImage } from './studentInfo';

export interface ArchiveFieldChange {
  key: string;
  label: string;
  before: string;
  after: string;
  beforeImages: StudentInfoImage[];
  afterImages: StudentInfoImage[];
  images: boolean;
}

/** Compare persisted display snapshots, never current dictionary labels or masked secrets. */
export function archiveChanges(before: StudentArchive, after: StudentArchive, sensitive: boolean) {
  const prior = before.information, next = after.information;
  const definitions = new Map([...prior.fields, ...next.fields].map(field => [field.key, field]));
  const changes: ArchiveFieldChange[] = [];
  let hidden = false;
  for (const [key, field] of definitions) {
    const oldField = prior.fields.find(f => f.key === key);
    const newField = next.fields.find(f => f.key === key);
    if (!oldField?.enabled && !newField?.enabled) continue;
    if (!sensitive && (oldField?.sensitive || newField?.sensitive)) { hidden = true; continue; }
    const beforeImages = prior.images.filter(image => image.fieldKey === key);
    const afterImages = next.images.filter(image => image.fieldKey === key);
    const images = field.type === 'images';
    const oldValue = prior.values[key] ?? '', newValue = next.values[key] ?? '';
    const references = (items: StudentInfoImage[]) => items.map(image => String(archiveImageReference({...image, source:image.source || 'registration'}))).sort().join(',');
    if (images ? references(beforeImages) === references(afterImages) : oldValue === newValue) continue;
    changes.push({key,label:field.label,before:oldValue,after:newValue,beforeImages,afterImages,images});
  }
  return {changes,hidden};
}

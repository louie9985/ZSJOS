// Test-only request adapter, with no network access or business writes.
const gift = { code: 'old', name: '原礼品', path: ['原分类', '原礼品'], snapshotAt: '2026-01-01' }
export default { get: async () => ({ list: [
  { id: 1, orderNo: 'ORDER-OLD', studentName: '旧格式', giftItemsJson: JSON.stringify([JSON.stringify(gift)]) },
  { id: 2, orderNo: 'ORDER-NEW', studentName: '新格式', giftItemSnapshots: [gift] },
  { id: 3, orderNo: 'ORDER-BAD', studentName: '损坏快照', giftItemsInvalid: true }
], total: 3 }) }

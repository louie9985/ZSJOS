// UTF-8. Synthetic, isolated preview fixtures; never imported by production routes.
export type ReviewStatus = 'DRAFT' | 'DIRECTOR_REVIEW' | 'FINAL_REVIEW' | 'NEED_MODIFY' | 'COMPLETED' | 'PUBLISHED' | 'CANCELLED'
export type Account = { id: number; name: string; platform: string; operator: string; director: string }
export type Work = { id: number; title: string; topic: string; script: string; purpose: string; format: string }
export type Batch = { id: number; no: string; studentId: number; student: string; accounts: Account[]; status: ReviewStatus; submitted: string; works: Work[] }
export const statusNames: Record<ReviewStatus, string> = { DRAFT: '草稿', DIRECTOR_REVIEW: '待编导审核', FINAL_REVIEW: '待终审', NEED_MODIFY: '待修改', COMPLETED: '待发布', PUBLISHED: '已发布', CANCELLED: '已取消' }
export const categories = [
  { key: 'ALL', label: '全部', statuses: [] },
  { key: 'DRAFT', label: '草稿', statuses: ['DRAFT'] },
  { key: 'PENDING', label: '待审批', statuses: ['DIRECTOR_REVIEW', 'FINAL_REVIEW'] },
  { key: 'NEED_MODIFY', label: '待修改', statuses: ['NEED_MODIFY'] },
  { key: 'COMPLETED', label: '待发布', statuses: ['COMPLETED'] },
  { key: 'PUBLISHED', label: '已发布', statuses: ['PUBLISHED'] },
  { key: 'CANCELLED', label: '已取消', statuses: ['CANCELLED'] }
]
const paragraph = '很多人以为，做好一顿早餐需要很长时间。其实从前一晚的准备开始，留出十分钟，也能让清晨从容一些。今天分享三个容易坚持的小习惯：提前分装食材、搭配不同颜色的蔬菜、给自己留一杯温水的时间。\n\n镜头建议：用自然光拍摄厨房的晨间场景，先呈现食材，再展示制作步骤，最后回到餐桌。语气保持轻松，避免夸张承诺，让观众看到真实、可重复的生活方法。\n\n'
const ending = '\n【全文结束：最后一段完整可读】'
const unbrokenText = '\nhttps://example.com/' + 'long-unbroken-text-'.repeat(40)
export const longScript = paragraph.repeat(90).slice(0, 10000 - ending.length - unbrokenText.length) + unbrokenText + ending
const accounts: Account[] = [
  { id: 101, name: '小禾的轻食日记', platform: '抖音', operator: '运营甲', director: '编导甲' },
  { id: 102, name: '小禾的厨房与四季生活记录', platform: '小红书', operator: '运营乙', director: '编导乙' },
  { id: 103, name: '阿川的周末旅行', platform: '视频号', operator: '运营甲', director: '编导乙' },
  { id: 104, name: '认真记录每一天的城市漫步与生活美学分享账号', platform: '小红书', operator: '运营乙', director: '编导甲' }
]
function works(seed: number, count: number): Work[] {
  return Array.from({ length: count }, (_, index) => ({ id: seed * 10 + index,
    title: ['给忙碌早晨的一份十分钟早餐', '不用早起，也能好好吃早餐', '把周末的松弛感带进工作日'][index % 3],
    topic: ['晨间生活方式', '厨房里的效率小习惯', '周末日常记录'][index % 3],
    script: index === 0 && seed === 1 ? longScript : paragraph.repeat(2) + '结尾：分享你最喜欢的一种早餐搭配。',
    purpose: '建立信任', format: '口播 + 生活实拍' }))
}
export const initialBatches: Batch[] = [
  { id: 1, no: 'CR-DEMO-20260922-01', studentId: 11, student: '小禾（示例学员）', accounts: accounts.slice(0, 2), status: 'DIRECTOR_REVIEW', submitted: '2026-09-22 10:30', works: works(1, 3) },
  { id: 2, no: 'CR-DEMO-20260922-02', studentId: 12, student: '阿川（示例学员）', accounts: [accounts[2]], status: 'FINAL_REVIEW', submitted: '2026-09-22 09:15', works: works(2, 2) },
  { id: 3, no: 'CR-DEMO-20260921-03', studentId: 13, student: '一位使用较长展示名称的示例学员', accounts: [accounts[3]], status: 'DIRECTOR_REVIEW', submitted: '2026-09-21 16:40', works: works(3, 1) },
  ...(['DRAFT', 'NEED_MODIFY', 'COMPLETED', 'PUBLISHED', 'CANCELLED'] as ReviewStatus[]).map((status, index) => ({
    id: index + 4, no: `CR-DEMO-20260920-0${index + 4}`, studentId: 20 + index, student: `示例学员${['丁', '戊', '己', '庚', '辛'][index]}`,
    accounts: [{ ...accounts[index % accounts.length], id: 200 + index }], status, submitted: status === 'DRAFT' ? '' : '2026-09-20 15:20', works: works(index + 4, 1)
  }))
]
export type Filters = { operator?: string; director?: string; platform?: string; from?: string; to?: string; stage?: string; mine: boolean }
export function filterBatches(batches: Batch[], category: string, keyword: string, filters: Filters) {
  const statuses = categories.find(item => item.key === category)?.statuses || []
  const query = keyword.trim().toLocaleLowerCase()
  return batches.filter(batch => (!statuses.length || statuses.includes(batch.status))
    && (!filters.stage || category !== 'PENDING' || batch.status === filters.stage)
    && (!filters.mine || batch.accounts[0].operator === '运营甲')
    && (!filters.from || batch.submitted.slice(0, 10) >= filters.from)
    && (!filters.to || Boolean(batch.submitted) && batch.submitted.slice(0, 10) <= filters.to)
    && batch.accounts.some(account => (!filters.operator || account.operator === filters.operator)
      && (!filters.director || account.director === filters.director) && (!filters.platform || account.platform === filters.platform))
    && (!query || [batch.student, batch.no, ...batch.accounts.map(account => account.name),
      ...batch.works.flatMap(work => [work.title, work.topic, work.script])].some(value => value.toLocaleLowerCase().includes(query))))
}

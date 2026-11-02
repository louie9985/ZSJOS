// Date operand protocol: technical calendar anchors, not business dictionary values.
export const bases = [{value:'now',label:'当前时刻'},{value:'today_start',label:'今天开始（00:00）'},{value:'week_start',label:'本周开始'},{value:'month_start',label:'本月开始'},{value:'quarter_start',label:'本季度开始'},{value:'year_start',label:'本年开始'}]
export const units = [{value:'minute',label:'分钟'},{value:'hour',label:'小时'},{value:'day',label:'天'},{value:'week',label:'周'},{value:'month',label:'月'},{value:'year',label:'年'}]
export type RelativeTime = {kind:'relative';base:string;offset:number;unit:string}
export const isRelativeTime = (value: unknown): value is RelativeTime => !!value && typeof value === 'object' && 'kind' in value && value.kind === 'relative'
export const initialRelativeTime = (): RelativeTime => ({kind:'relative',base:'today_start',offset:0,unit:'day'})
export const relativeTimeLabel = (v: RelativeTime) => `${bases.find(x=>x.value===v.base)?.label || v.base}${v.offset ? ` ${v.offset > 0 ? '+' : '−'} ${Math.abs(v.offset)} ${units.find(x=>x.value===v.unit)?.label || v.unit}` : ''}`

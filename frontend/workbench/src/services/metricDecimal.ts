/** Keep backend decimal strings exact, including amounts above Number.MAX_SAFE_INTEGER. */
export function formatMetricDecimal(value:string,decimals:number,percentage=false):string {
 if(!/^-?\d+(\.\d+)?$/.test(value)||decimals<0||decimals>8)return '无法显示'
 const negative=value.startsWith('-');const [whole,fraction='']=value.replace(/^-/,'').split('.')
 const scale=fraction.length;let number=BigInt(whole+fraction);const target=decimals+(percentage?2:0)
 if(scale>target){const divisor=10n**BigInt(scale-target);number=(number+divisor/2n)/divisor}else number*=10n**BigInt(target-scale)
 const digits=number.toString().padStart(decimals+1,'0');const integer=decimals?digits.slice(0,-decimals):digits;const tail=decimals?'.'+digits.slice(-decimals):''
 return (negative&&number!==0n?'-':'')+integer.replace(/\B(?=(\d{3})+(?!\d))/g,',')+tail+(percentage?'%':'')
}

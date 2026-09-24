# UTF-8. Isolated component browser verification, no real business data.
from playwright.sync_api import sync_playwright
from pathlib import Path
output = Path(__file__).resolve().parents[3] / 'output'
output.mkdir(exist_ok=True)
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 page=b.new_page(viewport={'width':1440,'height':1000})
 errors=[]
 page.on('pageerror',lambda e:errors.append(str(e)))
 base='http://localhost:5174/test/lead-contact-rows.html'
 page.goto(base)
 grid=page.locator('.lead-profile-fields--contact-rows');grid.wait_for()
 for w in [1440,900,390]:
  page.set_viewport_size({'width':w,'height':1000})
  page.wait_for_timeout(150)
  assert grid.locator('.lead-field-value').evaluate_all('(els)=>els.every(e=>e.scrollWidth<=e.clientWidth+1)'), 'text overflow'
  assert grid.locator('button').evaluate_all('(els)=>els.every(e=>e.getBoundingClientRect().width>=28 && e.getBoundingClientRect().height>=28)')
  page.screenshot(path=str(output/f'lead-contact-{w}.png'),full_page=True)
 # Verify threshold using actual card container, independent of the viewport layout.
 page.set_viewport_size({'width':1440,'height':1000})
 for w,cols in [(480,1),(481,2)]:
  page.locator('.lead-profile-contact-container').evaluate('(e,w)=>e.style.width=w+"px"',w)
  assert grid.evaluate('(e)=>getComputedStyle(e).gridTemplateColumns.split(" ").length')==cols
 page.locator('.lead-profile-contact-container').evaluate('(e)=>e.style.removeProperty("width")')
 page.evaluate('''() => { window.copies=[]; Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async v=>window.copies.push(v)}}) }''')
 for label in ['姓名','手机号','微信号']:
  button=page.get_by_role('button',name='复制'+label,exact=True)
  button.focus();button.press('Enter')
  page.get_by_role('button',name='已复制',exact=True).wait_for()
  page.get_by_role('button',name='切换客资',exact=True).click()
  page.get_by_role('button',name='复制'+label,exact=True).wait_for()
 assert page.evaluate('window.copies')==['测试姓名','+8613800000000','wechat_abcdefghijklmnopqrstuvwxyz0123456789'*5]
 page.evaluate("() => { navigator.clipboard.writeText=async()=>{throw Error('denied')}; document.execCommand=()=>false }")
 page.get_by_role('button',name='复制姓名',exact=True).click()
 page.get_by_text('复制失败，请手动选择文本复制',exact=True).wait_for()
 page.goto(base+'?empty');grid.wait_for();assert grid.locator('button').count()==0
 page.goto(base+'?default');page.locator('.lead-profile-fields').wait_for()
 assert page.locator('.lead-profile-fields--contact-rows').count()==0
 assert page.get_by_role('button',name='复制姓名',exact=True).count()==0
 assert page.locator('.lead-field-copy-btn').count()==2
 assert not errors, errors
 b.close()
 print('PASS: three widths, 480/481 container threshold, full text, button sizing, keyboard copy, exact clipboard values, identity reset, failure, empty and default variant')

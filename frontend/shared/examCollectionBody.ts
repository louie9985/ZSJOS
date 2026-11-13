// UTF-8. Only call with sanitized HTML. Preserve stored dimensions in the display projection.
export function examCollectionBodyHtml(html: string): string {
  const document = new DOMParser().parseFromString(html, 'text/html');
  document.querySelectorAll('table').forEach(table => {
    const wrapper = document.createElement('div');
    wrapper.className = 'exam-body-table';
    wrapper.tabIndex = 0;
    wrapper.setAttribute('role', 'region');
    wrapper.setAttribute('aria-label', '正文表格，可横向滚动');
    table.replaceWith(wrapper);
    wrapper.append(table);
  });
  return document.body.innerHTML;
}

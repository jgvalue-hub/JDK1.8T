'use strict';
document.addEventListener('DOMContentLoaded', function () {
  const search = document.getElementById('menu-search');
  if (search) search.addEventListener('input', function () {
    const query = search.value.trim().toLowerCase(); let found = 0;
    document.querySelectorAll('.menu-group').forEach(function (group) {
      let matches = 0;
      group.querySelectorAll('.business-menu').forEach(function (link) {
        const show = link.textContent.toLowerCase().includes(query);
        link.hidden = !show; if (show) { matches++; found++; }
      });
      group.hidden = matches === 0; if (query) group.open = matches > 0;
    });
    document.getElementById('menu-empty').hidden = found !== 0;
  });
  const form = document.getElementById('report-form'); if (!form) return;
  const body = document.getElementById('report-lines');
  const status = document.getElementById('line-status');
  function announce(message) { status.textContent = message || (body.rows.length + ' / 12행 · 변경 후 초안 저장을 눌러주세요.'); }
  document.getElementById('add-line').addEventListener('click', function () {
    if (body.rows.length >= 12) { announce('명세는 최대 12행입니다.'); return; }
    const row = body.rows[0].cloneNode(true);
    row.querySelectorAll('input').forEach(function (input) { input.value = input.type === 'number' ? '0' : ''; });
    row.querySelector('select').selectedIndex = 0; body.appendChild(row); announce(); row.querySelector('input').focus();
  });
  body.addEventListener('click', function (event) {
    if (!event.target.classList.contains('remove-line')) return;
    if (body.rows.length === 1) { announce('최소 한 행이 필요합니다.'); return; }
    event.target.closest('tr').remove(); announce();
  });
  document.getElementById('sample-lines').addEventListener('click', function () {
    if (body.rows.length !== 1 || body.querySelector('[name="lineLabel"]').value.trim()) {
      announce('기존 명세를 덮어쓰지 않습니다. 비어 있는 첫 행에서만 예시를 채울 수 있습니다.'); return;
    }
    const samples = { LEDGER:[1000,1000,0], RECEIVABLE:[1000,300,95], PAYABLE:[1000,300,50], ASSET:[1200,0,12], BUDGET:[1000,800,1100], CASH:[500,300,200], MARKET:[10000,10,20], CREDIT:[10000,5,40], OPERATION:[4,4,25], CONTROL:[20,15,2] };
    const values = samples[form.dataset.family]; if (!values) return;
    body.querySelector('[name="lineLabel"]').value = '가상 데모 항목';
    ['lineA','lineB','lineC'].forEach(function (name,index) { body.querySelector('[name="'+name+'"]').value = values[index]; });
    body.querySelector('[name="lineMemo"]').value = '교육용 가상 가정 · 실제 금융자료 아님'; announce('가상 예시를 채웠습니다. 관리 단위와 검토자를 선택하고 저장하세요.');
  });
  announce();
});

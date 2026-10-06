'use strict';
window.Q = (() => {
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const date = value => new Date(`${String(value).slice(0,10)}T12:00:00+07:00`);
  const dateText = value => date(value).toLocaleDateString('th-TH',{day:'numeric',month:'long',year:'numeric',timeZone:'Asia/Bangkok'});
  const shortDate = value => date(value).toLocaleDateString('th-TH',{day:'numeric',month:'short',timeZone:'Asia/Bangkok'});
  const timeText = value => String(value).slice(0,5);
  const shiftDate = (value,days) => {const d=new Date(`${value}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10);};
  const token = () => document.querySelector('meta[name="_csrf"]').content;
  async function api(url,data) {
    const opts={headers:{Accept:'application/json'},credentials:'same-origin'};
    if(data!==undefined){opts.method='POST';opts.headers['Content-Type']='application/json';opts.headers[document.querySelector('meta[name="_csrf_header"]').content]=token();opts.body=JSON.stringify(data);}
    let response;
    try{response=await fetch(url,opts);}catch(e){throw Object.assign(new Error('เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบการเชื่อมต่อแล้วลองใหม่'),{code:'NETWORK'});}
    const body=await response.json().catch(()=>({message:'ไม่สามารถทำรายการได้ กรุณารีเฟรชแล้วลองใหม่'}));
    if(!response.ok){if(response.status===401&&document.body.dataset.page.startsWith('admin'))window.location.assign('/admin/login');throw Object.assign(new Error(body.message),{code:body.code,status:response.status});}
    return body;
  }
  function error(id,message){const el=$(id);if(!el)return;el.textContent=message||'';el.hidden=!message;}
  function toast(message){$('toastMessage').textContent=message;bootstrap.Toast.getOrCreateInstance($('appToast'),{delay:5000}).show();}
  async function busy(button,action){if(button.disabled)return;const text=button.innerHTML;button.disabled=true;button.innerHTML='<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>กำลังทำรายการ';try{return await action();}finally{button.disabled=false;button.innerHTML=text;}}
  const badge = b => `<span class="badge ${b.status==='CANCELLED'?'bg-label-danger':b.actionable?'bg-label-info':'bg-label-success'}">${esc(b.displayStatus)}</span>`;
  const empty = message => `<div class="empty-state"><i class="bx bx-calendar-x"></i>${esc(message)}</div>`;
  function details(b,copy=false){return `<dl class="booking-details"><dt>รหัสการจอง</dt><dd class="code">${esc(b.bookingCode)}${copy?'<button class="btn btn-icon btn-label-primary" id="copyCode" aria-label="คัดลอกรหัสการจอง"><i class="bx bx-copy"></i></button>':''}</dd><dt>ชื่อผู้จอง</dt><dd>${esc(b.customerName)}</dd><dt>วันที่</dt><dd>${dateText(b.bookingDate)}</dd><dt>เวลา</dt><dd>${timeText(b.bookingTime)} น.</dd><dt>เบอร์โทรศัพท์</dt><dd>${esc(b.customerPhone)}</dd><dt>สถานะ</dt><dd>${badge(b)}</dd>${b.cancellationReason?`<dt>เหตุผลยกเลิก</dt><dd>${esc(b.cancellationReason)}</dd>`:''}</dl>`;}
  function modal(title,body,footer=''){ $('actionModalTitle').textContent=title;$('actionModalBody').innerHTML=body;$('actionModalFooter').innerHTML=footer;bootstrap.Modal.getOrCreateInstance($('actionModal')).show(); }
  let actionOpening=false;
  if($('actionModal')){
    $('actionModal').addEventListener('show.bs.modal',()=>{actionOpening=true;});
    $('actionModal').addEventListener('shown.bs.modal',()=>{actionOpening=false;});
  }
  async function closeModal(){
    const el=$('actionModal');
    if(actionOpening)await new Promise(resolve=>el.addEventListener('shown.bs.modal',resolve,{once:true}));
    if(!el.classList.contains('show'))return;
    await new Promise(resolve=>{el.addEventListener('hidden.bs.modal',resolve,{once:true});bootstrap.Modal.getOrCreateInstance(el).hide();});
  }
  async function confirm(message,title='ยืนยันทำรายการ',danger=false){
    const existing=$('actionModal'),wasOpen=existing.classList.contains('show');
    if(wasOpen)await closeModal();
    $('confirmTitle').textContent=title;$('confirmMessage').textContent=message;$('confirmAccept').className=`btn ${danger?'btn-danger':'btn-primary'}`;
    return new Promise(resolve=>{let accepted=false;const el=$('confirmModal'),instance=bootstrap.Modal.getOrCreateInstance(el);$('confirmAccept').onclick=()=>{accepted=true;instance.hide();};el.addEventListener('hidden.bs.modal',()=>{if(wasOpen)bootstrap.Modal.getOrCreateInstance(existing).show();resolve(accepted);},{once:true});instance.show();});
  }
  const slotButtons = (slots,selected,current) => slots.map(s=>`<button type="button" class="slot-button ${selected===s.time?'selected':''}" data-time="${esc(s.time)}" ${!s.available||current===s.time?'disabled':''}><span>${timeText(s.time)}</span><small>${current===s.time?'เวลาปัจจุบัน':s.available?`ว่าง ${s.remaining} ที่`:esc(s.reason)}</small>${s.available&&s.remaining===1&&current!==s.time?'<span class="slot-last">เหลือ 1</span>':''}</button>`).join('');
  async function reschedule(b,phone,isAdmin,onSaved){
    let selected=null;
    modal('เลื่อนเวลาการจอง',`<div class="reschedule-date"><i class="bx bx-time-five"></i><div>วันที่<strong>${dateText(b.bookingDate)} (วันเดิม)</strong></div></div><h5>เลือกเวลาใหม่</h5><div id="rescheduleSlots" class="slot-grid modal-slot-grid">${empty('กำลังโหลดเวลา...')}</div><div id="rescheduleError" class="inline-error" role="alert" hidden></div>`,`<button class="btn btn-outline-secondary" data-bs-dismiss="modal">กลับ</button><button id="saveReschedule" class="btn btn-primary" disabled>ยืนยันการเลื่อนเวลา</button>`);
    try{const data=await api('/api/public/slots',{date:b.bookingDate});$('rescheduleSlots').innerHTML=data.closed?empty('ร้านปิดในวันที่เลือก'):slotButtons(data.slots,null,b.bookingTime);$('rescheduleSlots').onclick=e=>{const btn=e.target.closest('[data-time]');if(!btn||btn.disabled)return;selected=btn.dataset.time;$('rescheduleSlots').querySelectorAll('.slot-button').forEach(x=>x.classList.toggle('selected',x===btn));$('saveReschedule').disabled=false;};}catch(e){error('rescheduleError',e.message);}
    $('saveReschedule').onclick=()=>busy($('saveReschedule'),async()=>{error('rescheduleError','');try{await api(isAdmin?'/api/admin/reschedule':'/api/public/reschedule',{id:b.id,time:selected,phone});closeModal();toast('เลื่อนเวลาการจองเรียบร้อย');await onSaved();}catch(e){error('rescheduleError',e.message);}});
  }
  async function cancel(b,phone,isAdmin,onSaved){if(!await confirm(`ยกเลิกการจอง ${b.bookingCode} วันที่ ${dateText(b.bookingDate)} เวลา ${timeText(b.bookingTime)} น. หรือไม่?`,'ยกเลิกการจอง',true))return;try{await api(isAdmin?'/api/admin/cancel':'/api/public/cancel',{id:b.id,phone});closeModal();toast('ยกเลิกการจองเรียบร้อย');await onSaved();}catch(e){toast(e.message);}}
  const uuid = () => crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=crypto.getRandomValues(new Uint8Array(1))[0]%16;return(c==='x'?r:(r&3)|8).toString(16);});
  function updateHeader(){if(!$('headerHours'))return;const s=window.QUEUE_SETTINGS,h=s.hours[new Date(`${s.today}T12:00:00`).getDay()===0?6:new Date(`${s.today}T12:00:00`).getDay()-1],holiday=s.holidays.some(x=>String(x.date).slice(0,10)===s.today);$('headerHours').textContent=!h.open||holiday?'วันนี้ร้านปิด':`วันนี้เปิดบริการ ${timeText(h.openTime)}–${timeText(h.closeTime)} น.`;}
  updateHeader();
  return {$,esc,date,dateText,shortDate,timeText,shiftDate,api,error,toast,busy,badge,empty,details,modal,closeModal,confirm,slotButtons,reschedule,cancel,uuid,updateHeader};
})();

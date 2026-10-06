'use strict';
(async()=>{
  const {$,esc,dateText,shortDate,timeText,shiftDate,api,error,toast,busy,badge,empty}=Q;
  if(document.body.dataset.page==='booking'){
    let date=window.QUEUE_SETTINGS.today,time=null,slots=[],requestKey=Q.uuid(),duplicateConfirmed=false,loadVersion=0;
    const today=window.QUEUE_SETTINGS.today;
    $('bookingDate').min=today;$('bookingDate').max=shiftDate(today,2);$('bookingDate').value=date;
    $('dateChoices').innerHTML=[0,1,2].map(n=>`<button type="button" class="date-tile ${n===0?'selected':''}" data-date="${shiftDate(today,n)}"><strong>${n===0?'วันนี้':n===1?'พรุ่งนี้':shortDate(shiftDate(today,n))}</strong><small>${shortDate(shiftDate(today,n))}</small></button>`).join('');
    $('leadHint').textContent=`รอบละ 1 ชั่วโมง · จองล่วงหน้าขั้นต่ำ ${window.QUEUE_SETTINGS.leadMinutes} นาที`;
    function showStep(step){['chooseStep','formStep','successStep'].forEach((id,n)=>$(id).hidden=n+1!==step);document.querySelectorAll('.step').forEach(x=>x.classList.toggle('active',Number(x.dataset.step)===step));$('bookingStepper').hidden=step===3;window.scrollTo({top:0,behavior:'smooth'});}
    async function load(){const version=++loadVersion;$('nextStep').disabled=true;$('bookingSlots').innerHTML=empty('กำลังโหลดเวลา...');try{const data=await api('/api/public/slots',{date});if(version!==loadVersion)return;slots=data.slots;time=slots.some(s=>s.time===time&&s.available)?time:null;$('bookingSlots').innerHTML=data.closed?empty('ร้านปิดในวันที่เลือก กรุณาเลือกวันอื่น'):slots.length?Q.slotButtons(slots,time):empty('ไม่มีรอบจองในวันที่เลือก');$('nextStep').disabled=!time;}catch(e){if(version===loadVersion)$('bookingSlots').innerHTML=empty(e.message);}}
    async function selectDate(value){date=value;time=null;duplicateConfirmed=false;requestKey=Q.uuid();$('bookingDate').value=date;document.querySelectorAll('[data-date]').forEach(x=>x.classList.toggle('selected',x.dataset.date===date));await load();}
    $('dateChoices').onclick=e=>{const button=e.target.closest('[data-date]');if(button)selectDate(button.dataset.date);};
    $('bookingDate').onchange=()=>{if($('bookingDate').value)selectDate($('bookingDate').value);};
    $('bookingSlots').onclick=e=>{const button=e.target.closest('[data-time]');if(!button||button.disabled)return;time=button.dataset.time;requestKey=Q.uuid();duplicateConfirmed=false;$('bookingSlots').innerHTML=Q.slotButtons(slots,time);$('nextStep').disabled=false;};
    $('nextStep').onclick=()=>{$('selectedDate').textContent=dateText(date);$('selectedTime').textContent=`${timeText(time)} น.`;showStep(2);};
    $('backStep').onclick=$('changeSelection').onclick=()=>{showStep(1);load();};
    ['customerName','customerPhone'].forEach(id=>$(id).addEventListener('input',()=>{duplicateConfirmed=false;error('bookingError','');}));
    $('customerBookingForm').onsubmit=async e=>{e.preventDefault();const button=e.submitter;await busy(button,async()=>{
      error('bookingError','');const request={date,time,name:$('customerName').value,phone:$('customerPhone').value,requestKey,duplicateConfirmed};
      try{let result;try{result=await api('/api/public/bookings',request);}catch(e){if(e.code!=='DUPLICATE_WARNING')throw e;if(!await Q.confirm(e.message,'มีรายการจองในวันเดียวกัน'))return;duplicateConfirmed=true;result=await api('/api/public/bookings',{...request,duplicateConfirmed:true});}
        $('successDetails').innerHTML=Q.details(result,true);showStep(3);$('copyCode').onclick=async()=>{try{await navigator.clipboard.writeText(result.bookingCode);toast('คัดลอกรหัสการจองแล้ว');}catch(e){toast(`รหัสการจอง ${result.bookingCode}`);}};
      }catch(e){error('bookingError',e.message);}
    });};
    await load();
    setInterval(()=>{if(!$('chooseStep').hidden)load();},60_000);
  }
  if(document.body.dataset.page==='check'){
    let bookings=[],lookupPhone='';
    async function lookup(){bookings=await api('/api/public/lookup',{phone:lookupPhone});render();}
    function render(){const current=bookings.filter(b=>b.actionable).sort((a,b)=>(a.bookingDate+a.bookingTime).localeCompare(b.bookingDate+b.bookingTime));const history=bookings.filter(b=>!b.actionable);
      $('lookupResults').hidden=false;$('currentBookings').innerHTML=current.length?current.map(b=>`<article class="current-booking"><div class="current-booking-info"><i class="bx bx-calendar"></i><div><h5>${dateText(b.bookingDate)}</h5><h5>${timeText(b.bookingTime)} น.</h5><p>${esc(b.customerName)}</p><small>รหัส ${esc(b.bookingCode)}</small></div></div><div class="current-booking-actions">${badge(b)}<div><button class="btn btn-outline-primary" data-reschedule="${b.id}">เลื่อนเวลา</button><button class="btn btn-outline-danger" data-cancel="${b.id}">ยกเลิกการจอง</button></div></div></article>`).join(''):empty('ไม่มีรายการจองปัจจุบัน');
      $('bookingHistory').innerHTML=history.length?history.map(b=>`<button class="history-item" data-detail="${b.id}"><span class="history-date"><i class="bx bx-calendar me-2"></i>${dateText(b.bookingDate)}</span><span>${timeText(b.bookingTime)} น.</span><span class="history-code">รหัส ${esc(b.bookingCode)} · ${esc(b.customerName)}</span>${badge(b)}<i class="bx bx-chevron-right"></i></button>`).join(''):empty('ยังไม่มีประวัติการจอง');
    }
    $('lookupForm').onsubmit=e=>{e.preventDefault();lookupPhone=$('lookupPhone').value;busy(e.submitter,async()=>{error('lookupError','');$('lookupResults').hidden=true;try{await lookup();}catch(e){error('lookupError',e.message);}});};
    $('lookupResults').onclick=async e=>{const btn=e.target.closest('[data-reschedule],[data-cancel],[data-detail]');if(!btn)return;const id=Number(btn.dataset.reschedule||btn.dataset.cancel||btn.dataset.detail),b=bookings.find(x=>x.id===id);if(btn.dataset.reschedule)Q.reschedule(b,lookupPhone,false,lookup);else if(btn.dataset.cancel)await Q.cancel(b,lookupPhone,false,lookup);else Q.modal('รายละเอียดการจอง',Q.details(b),'<button class="btn btn-outline-secondary" data-bs-dismiss="modal">ปิด</button>');};
    setInterval(()=>{if(lookupPhone&&!$('lookupResults').hidden&&!document.hidden&&!$('actionModal').classList.contains('show'))lookup().catch(()=>{});},30_000);
  }
})();

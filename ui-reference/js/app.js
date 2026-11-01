import {state,changePage,goBack} from "./state.js";
import {loadLanguage,t} from "./i18n.js";
import {icon} from "./icons.js";
import {home} from "./screens/home.js";
import {summary} from "./screens/summary.js";
import {budget} from "./screens/budget.js";
import {settings} from "./screens/settings.js";
import {drawer} from "./components/drawer.js";
import {expense} from "./components/expense-sheet.js";

const app=document.getElementById("app");
const page=document.getElementById("page");
const scroll=document.getElementById("page-scroll");
const overlay=document.getElementById("overlay-root");
const menuButton=document.getElementById("menu-button");

function render(){
  document.documentElement.lang=state.language;
  document.documentElement.dir=state.language==="he"?"rtl":"ltr";
  document.documentElement.dataset.theme=state.theme;
  document.title=t(state.language,"app_name");
  menuButton.setAttribute("aria-label",t(state.language,"menu_button_label"));
  menuButton.innerHTML=icon("menu");
  page.innerHTML=[home,summary,settings,budget][state.page]();
  overlay.innerHTML=state.overlay==="menu"?drawer():state.overlay==="expense"?expense():"";
  app.inert=Boolean(state.overlay);
  scroll.style.overflow=state.overlay?"hidden":"auto";
}
function renderKeepScroll(){
  const top=scroll.scrollTop;
  render();
  scroll.scrollTop=top;
}
await loadLanguage(state.language);
render();

document.addEventListener("input",event=>{
  if(event.target.dataset.draft)state.settingsDrafts[event.target.dataset.draft]=event.target.value;
  if(event.target.dataset.expense)state.expenseDraft[event.target.dataset.expense]=event.target.value;
});
document.addEventListener("click",async event=>{
  const button=event.target.closest("[data-action]");
  if(!button)return;
  if(button.classList.contains("scrim")&&event.target.closest("[data-stop-close]"))return;
  switch(button.dataset.action){
    case "close-overlay":
      state.overlay=null;renderKeepScroll();break;
    case "navigate":
      changePage(Number(button.dataset.index),scroll);state.overlay=null;render();break;
    case "expense":
      state.overlay="expense";state.expenseDraft={amount:"",date:"",note:""};renderKeepScroll();break;
    case "budget-toggle":
      state.budgetExpanded=!state.budgetExpanded;renderKeepScroll();break;
    case "tab":
      state.tab=Number(button.dataset.index);renderKeepScroll();break;
    case "budget-prev":
    case "budget-next":
      state.budgetMonth.setMonth(state.budgetMonth.getMonth()+(button.dataset.action==="budget-prev"?-1:1));
      renderKeepScroll();break;
    case "manage-sources":
      changePage(2,scroll);state.expanded.add("sources");render();
      document.querySelector('[data-key="sources"]')?.scrollIntoView({block:"center"});break;
    case "toggle-setting":{
      const key=button.dataset.key;
      state.expanded.has(key)?state.expanded.delete(key):state.expanded.add(key);
      renderKeepScroll();break;
    }
    case "language":
      if(state.language!==button.dataset.value){
        await loadLanguage(button.dataset.value);
        state.language=button.dataset.value;state.page=2;state.expanded.add("language");
        renderKeepScroll();
      }
      break;
    case "theme":
      state.theme=button.dataset.value;renderKeepScroll();break;
  }
});
menuButton.addEventListener("click",()=>{state.overlay="menu";renderKeepScroll()});
document.addEventListener("keydown",event=>{
  if(event.key==="Escape"||(event.key==="Backspace"&&event.target.tagName!=="INPUT"&&event.target.tagName!=="TEXTAREA")){
    if(goBack(scroll)){event.preventDefault();renderKeepScroll()}
  }
});

let drag=null;
overlay.addEventListener("pointerdown",event=>{
  const handle=event.target.closest(".drag-area");
  if(!handle||state.overlay!=="expense"||drag)return;
  const sheet=handle.closest(".sheet");
  drag={pointerId:event.pointerId,startY:event.clientY,distance:0,handle,sheet};
  handle.setPointerCapture(event.pointerId);
});
overlay.addEventListener("pointermove",event=>{
  if(!drag||event.pointerId!==drag.pointerId)return;
  drag.distance=Math.max(0,event.clientY-drag.startY);
  drag.sheet.style.transform=`translateY(${drag.distance}px)`;
});
function finishDrag(event,cancelled){
  if(!drag||event.pointerId!==drag.pointerId)return;
  const {handle,sheet,distance,pointerId}=drag;
  drag=null;
  if(handle.hasPointerCapture(pointerId))handle.releasePointerCapture(pointerId);
  if(!cancelled&&distance>=64){
    state.overlay=null;
    renderKeepScroll();
  }else{
    sheet.style.transition="transform 180ms ease";
    sheet.style.transform="translateY(0)";
  }
}
overlay.addEventListener("pointerup",event=>finishDrag(event,false));
overlay.addEventListener("pointercancel",event=>finishDrag(event,true));
